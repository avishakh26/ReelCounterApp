package com.reelcounter.feature.friends

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reelcounter.core.domain.Outcome
import com.reelcounter.core.domain.social.ConnectionsState
import com.reelcounter.core.domain.social.FriendActivityRepository
import com.reelcounter.core.domain.social.FriendRequestResult
import com.reelcounter.core.domain.social.FriendSuggestion
import com.reelcounter.core.domain.social.FriendsRepository
import com.reelcounter.core.domain.social.PresenceRepository
import com.reelcounter.core.domain.social.PrivacyRepository
import com.reelcounter.core.domain.social.PrivacyState
import com.reelcounter.core.domain.social.SocialError
import com.reelcounter.core.domain.tracking.CurrentDateProvider
import com.reelcounter.core.model.Connection
import com.reelcounter.core.model.Visibility
import com.reelcounter.core.ui.metric.DaySummary
import com.reelcounter.core.ui.metric.summarize
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class SearchState(
    val query: String = "",
    val isSearching: Boolean = false,
    /** null until a search for the current query has finished. */
    val results: List<Connection>? = null,
    val error: SocialError? = null,
) {
    val isActive: Boolean get() = query.trim().length >= MIN_QUERY_LENGTH
}

/** One-off feedback after an action, shown as a snackbar. */
sealed interface FriendsMessage {
    data class RequestResult(val result: FriendRequestResult) : FriendsMessage
    data object Blocked : FriendsMessage
    data class Failed(val error: SocialError) : FriendsMessage
}

data class FriendsUiState(
    val search: SearchState = SearchState(),
    val connections: ConnectionsState = ConnectionsState.Loading,
    val onlineIds: Set<String> = emptySet(),
    /** Users with an action in flight; their buttons are disabled. */
    val busyIds: Set<String> = emptySet(),
    /** The user's own activity visibility, shown so they always know what friends can see. */
    val activityVisibility: Visibility? = null,
    val message: FriendsMessage? = null,
    /** Each friend's today, for friends who share it and recorded something. Absent = nothing shared. */
    val friendToday: Map<String, DaySummary> = emptyMap(),
    val suggestions: SuggestionsState = SuggestionsState.Loading,
)

sealed interface SuggestionsState {
    data object Loading : SuggestionsState
    data class Loaded(val people: List<FriendSuggestion>) : SuggestionsState
    data class Failed(val error: SocialError) : SuggestionsState
}

internal const val MIN_QUERY_LENGTH = 2

@OptIn(FlowPreview::class)
@HiltViewModel
class FriendsViewModel @Inject constructor(
    private val friendsRepository: FriendsRepository,
    privacyRepository: PrivacyRepository,
    presenceRepository: PresenceRepository,
    private val activityRepository: FriendActivityRepository,
    currentDateProvider: CurrentDateProvider,
) : ViewModel() {

    private val search = MutableStateFlow(SearchState())
    private val busy = MutableStateFlow<Set<String>>(emptySet())
    private val message = MutableStateFlow<FriendsMessage?>(null)
    private val friendToday = MutableStateFlow<Map<String, DaySummary>>(emptyMap())
    private val suggestions = MutableStateFlow<SuggestionsState>(SuggestionsState.Loading)
    private val reloadActivity = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    val uiState: StateFlow<FriendsUiState> = combine(
        combine(search, friendsRepository.connections, presenceRepository.onlineFriendIds, ::Triple),
        busy,
        privacyRepository.settings.map { (it as? PrivacyState.Loaded)?.settings?.activityVisibility },
        message,
        combine(friendToday, suggestions, ::Pair),
    ) { (search, connections, online), busy, visibility, message, (today, suggestions) ->
        FriendsUiState(search, connections, online, busy, visibility, message, today, suggestions)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FriendsUiState())

    init {
        val friendIds = friendsRepository.connections
            .map { (it as? ConnectionsState.Loaded)?.friends?.map { friend -> friend.id }?.toSet() }
            .filterNotNull()
            .distinctUntilChanged()

        // Friends' today: loaded when the friend list or the date changes, and again whenever the
        // server says one of them synced new figures (or the screen asks, e.g. on resume).
        viewModelScope.launch {
            combine(friendIds, currentDateProvider.today, ::Pair).collectLatest { (ids, today) ->
                loadFriendToday(ids, today)
                merge(activityRepository.changes.filter { it in ids }.map { }, reloadActivity)
                    .collect { loadFriendToday(ids, today) }
            }
        }

        // Suggestions exclude friends and pending requests, so they follow any change to those.
        viewModelScope.launch {
            friendsRepository.connections
                .map { state -> (state as? ConnectionsState.Loaded)?.let { it.friends + it.incoming + it.outgoing }?.map { it.id }?.toSet() }
                .filterNotNull()
                .distinctUntilChanged()
                .collectLatest { loadSuggestions() }
        }

        viewModelScope.launch {
            search.map { it.query.trim() }
                .distinctUntilChanged()
                .debounce(SEARCH_DEBOUNCE_MS)
                .collect { query -> if (query.length >= MIN_QUERY_LENGTH) runSearch(query) }
        }
    }

    fun onQueryChange(query: String) = search.update {
        // A new query clears stale results; the debounced search fills them in.
        if (query.trim() == it.query.trim()) it.copy(query = query) else SearchState(query = query)
    }

    fun refresh() {
        viewModelScope.launch { friendsRepository.refresh() }
        reloadActivity.tryEmit(Unit)
    }

    /** Re-reads friends' figures, e.g. when the screen comes back to the foreground. */
    fun refreshActivity() {
        reloadActivity.tryEmit(Unit)
    }

    private suspend fun loadFriendToday(ids: Set<String>, today: LocalDate) {
        if (ids.isEmpty()) {
            friendToday.value = emptyMap()
            return
        }
        // On failure (e.g. offline) keep showing the last figures.
        val result = activityRepository.activity(ids, today) as? Outcome.Success ?: return
        friendToday.value = result.value
            .mapValues { (_, days) -> days.filter { it.date == today }.summarize(today) }
            .filterValues { it.hasData }
    }

    private suspend fun loadSuggestions() {
        suggestions.value = when (val result = activityRepository.suggestions()) {
            is Outcome.Success -> SuggestionsState.Loaded(result.value)
            // Keep a list that already loaded; fail only with nothing to show.
            is Outcome.Failure -> (suggestions.value as? SuggestionsState.Loaded) ?: SuggestionsState.Failed(result.error)
        }
    }

    fun sendRequest(userId: String) = act(userId) {
        when (val result = friendsRepository.sendRequest(userId)) {
            is Outcome.Success -> FriendsMessage.RequestResult(result.value)
            is Outcome.Failure -> FriendsMessage.Failed(result.error)
        }
    }

    fun accept(userId: String) = act(userId) { friendsRepository.respond(userId, accept = true).failureMessage() }

    fun decline(userId: String) = act(userId) { friendsRepository.respond(userId, accept = false).failureMessage() }

    fun cancel(userId: String) = act(userId) { friendsRepository.cancelRequest(userId).failureMessage() }

    fun remove(userId: String) = act(userId) { friendsRepository.removeFriend(userId).failureMessage() }

    fun block(userId: String) = act(userId) {
        friendsRepository.block(userId).failureMessage() ?: FriendsMessage.Blocked
    }

    fun onMessageShown() = message.update { null }

    private fun act(userId: String, action: suspend () -> FriendsMessage?) {
        if (userId in busy.value) return
        busy.update { it + userId }
        viewModelScope.launch {
            val outcome = action()
            busy.update { it - userId }
            message.value = outcome
            // Relationship labels in the results changed; refresh them.
            search.value.query.trim().takeIf { it.length >= MIN_QUERY_LENGTH }?.let { runSearch(it) }
        }
    }

    private suspend fun runSearch(query: String) {
        search.update { if (it.query.trim() == query) it.copy(isSearching = true, error = null) else it }
        val result = friendsRepository.search(query)
        search.update { current ->
            // Ignore answers for a query the user has already changed.
            if (current.query.trim() != query) return@update current
            when (result) {
                is Outcome.Success -> current.copy(isSearching = false, results = result.value)
                is Outcome.Failure -> current.copy(isSearching = false, error = result.error)
            }
        }
    }

    internal companion object {
        const val SEARCH_DEBOUNCE_MS = 300L
    }
}

private fun Outcome<Unit, SocialError>.failureMessage(): FriendsMessage? =
    (this as? Outcome.Failure)?.let { FriendsMessage.Failed(it.error) }
