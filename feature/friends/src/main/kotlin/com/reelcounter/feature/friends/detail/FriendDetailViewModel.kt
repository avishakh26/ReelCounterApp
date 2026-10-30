package com.reelcounter.feature.friends.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.reelcounter.core.domain.Outcome
import com.reelcounter.core.domain.social.ConnectionsState
import com.reelcounter.core.domain.social.FriendActivityRepository
import com.reelcounter.core.domain.social.FriendsRepository
import com.reelcounter.core.domain.social.PresenceRepository
import com.reelcounter.core.domain.tracking.CurrentDateProvider
import com.reelcounter.core.model.DailyPlatformActivity
import com.reelcounter.core.model.UserSummary
import com.reelcounter.core.ui.metric.DaySummary
import com.reelcounter.core.ui.metric.summarize
import com.reelcounter.feature.friends.navigation.FriendDetailRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

sealed interface FriendDetailUiState {
    data object Loading : FriendDetailUiState

    /** Not (or no longer) a friend, e.g. removed or blocked while the screen was open. */
    data object NotFriends : FriendDetailUiState

    data class Content(
        val user: UserSummary,
        val online: Boolean,
        /** Their today across platforms; figures are null when they shared nothing today. */
        val today: DaySummary,
        val yesterday: DaySummary?,
        /** The seven days ending today, oldest first. */
        val week: List<DaySummary>,
        /** Today's per-platform figures they share. */
        val todayActivities: List<DailyPlatformActivity>,
        /** True once figures were fetched at least once (so "nothing shared" isn't shown while loading). */
        val loaded: Boolean,
    ) : FriendDetailUiState {
        val sharesAnything: Boolean get() = week.any { it.hasData }
    }
}

/**
 * One friend's dashboard. Shows only what the friend's "Who can see my activity" setting lets
 * through (the server filters it), and refreshes live when their phone syncs new figures.
 */
@HiltViewModel
class FriendDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    friendsRepository: FriendsRepository,
    presenceRepository: PresenceRepository,
    private val activityRepository: FriendActivityRepository,
    currentDateProvider: CurrentDateProvider,
) : ViewModel() {

    private val userId = savedStateHandle.toRoute<FriendDetailRoute>().userId
    private val days = MutableStateFlow<Pair<LocalDate, List<DailyPlatformActivity>>?>(null)
    private val reload = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private var currentDay: LocalDate? = null

    private val _isRefreshing = MutableStateFlow(false)

    /** A pull-to-refresh is running. */
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    val uiState: StateFlow<FriendDetailUiState> = combine(
        friendsRepository.connections,
        presenceRepository.onlineFriendIds,
        days,
    ) { connections, online, loaded ->
        val friends = (connections as? ConnectionsState.Loaded)?.friends ?: return@combine FriendDetailUiState.Loading
        val user = friends.firstOrNull { it.id == userId } ?: return@combine FriendDetailUiState.NotFriends
        val (today, activities) = loaded ?: (null to emptyList())
        val date = today ?: LocalDate.now()
        val week = (6 downTo 0).map { back ->
            val day = date.minusDays(back.toLong())
            activities.filter { it.date == day }.summarize(day)
        }
        FriendDetailUiState.Content(
            user = user,
            online = userId in online,
            today = week.last(),
            yesterday = week[week.lastIndex - 1],
            week = week,
            todayActivities = activities.filter { it.date == date },
            loaded = loaded != null,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FriendDetailUiState.Loading)

    init {
        viewModelScope.launch {
            currentDateProvider.today.collectLatest { today ->
                currentDay = today
                load(today)
                merge(activityRepository.changes.filter { it == userId }.map { }, reload).collect { load(today) }
            }
        }
    }

    /** Re-reads the figures, e.g. when the screen resumes. */
    fun refresh() {
        reload.tryEmit(Unit)
    }

    /** Pull-to-refresh: re-reads the figures and shows the spinner until they're in. */
    fun pullToRefresh() {
        val today = currentDay ?: return
        if (_isRefreshing.value) return
        _isRefreshing.value = true
        viewModelScope.launch {
            try {
                load(today)
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    private suspend fun load(today: LocalDate) {
        when (val result = activityRepository.activity(listOf(userId), today.minusDays(6))) {
            is Outcome.Success -> days.value = today to result.value[userId].orEmpty()
            // Offline: keep the last figures, but stop showing a loading state.
            is Outcome.Failure -> if (days.value == null) days.value = today to emptyList()
        }
    }
}
