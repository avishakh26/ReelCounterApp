package com.reelcounter.core.testing

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
import com.reelcounter.core.model.Connection
import com.reelcounter.core.model.DailyPlatformActivity
import com.reelcounter.core.model.PrivacySettings
import com.reelcounter.core.model.UserSummary
import com.reelcounter.core.model.Visibility
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalDate

class FakeFriendsRepository(
    initial: ConnectionsState = ConnectionsState.Loaded(emptyList(), emptyList(), emptyList()),
) : FriendsRepository {
    val state = MutableStateFlow(initial)
    override val connections: StateFlow<ConnectionsState> = state

    /** Search results by trimmed query; anything not listed returns no results. */
    val searchResults = mutableMapOf<String, List<Connection>>()
    var searchError: SocialError? = null
    val searches = mutableListOf<String>()

    var requestResult: Outcome<FriendRequestResult, SocialError> = Outcome.Success(FriendRequestResult.Requested)
    var actionError: SocialError? = null
    var blocked: Outcome<List<UserSummary>, SocialError> = Outcome.Success(emptyList())

    /** When set, actions wait for it, so tests can see the in-flight state. */
    var gate: CompletableDeferred<Unit>? = null

    /** Each action as "name:userId", in order. */
    val actions = mutableListOf<String>()
    var refreshCount = 0

    override suspend fun refresh() {
        refreshCount++
    }

    override suspend fun search(query: String): Outcome<List<Connection>, SocialError> {
        searches += query
        gate?.await()
        searchError?.let { return Outcome.Failure(it) }
        return Outcome.Success(searchResults[query].orEmpty())
    }

    override suspend fun sendRequest(userId: String): Outcome<FriendRequestResult, SocialError> {
        record("send", userId)
        return actionError?.let { Outcome.Failure(it) } ?: requestResult
    }

    override suspend fun respond(userId: String, accept: Boolean) = unit(if (accept) "accept" else "decline", userId)

    override suspend fun cancelRequest(userId: String) = unit("cancel", userId)

    override suspend fun removeFriend(userId: String) = unit("remove", userId)

    override suspend fun block(userId: String) = unit("block", userId)

    override suspend fun unblock(userId: String) = unit("unblock", userId)

    override suspend fun blockedUsers(): Outcome<List<UserSummary>, SocialError> = blocked

    private suspend fun unit(name: String, userId: String): Outcome<Unit, SocialError> {
        record(name, userId)
        return actionError?.let { Outcome.Failure(it) } ?: Outcome.Success(Unit)
    }

    private suspend fun record(name: String, userId: String) {
        actions += "$name:$userId"
        gate?.await()
    }
}

class FakePrivacyRepository(
    initial: PrivacyState = PrivacyState.Loaded(DEFAULT_PRIVACY),
) : PrivacyRepository {
    val state = MutableStateFlow(initial)
    override val settings: StateFlow<PrivacyState> = state

    var updateError: SocialError? = null
    var gate: CompletableDeferred<Unit>? = null
    var updateCount = 0
    var refreshCount = 0

    override suspend fun refresh() {
        refreshCount++
    }

    override suspend fun update(transform: (PrivacySettings) -> PrivacySettings): Outcome<PrivacySettings, SocialError> {
        updateCount++
        gate?.await()
        updateError?.let { return Outcome.Failure(it) }
        val current = (state.value as? PrivacyState.Loaded)?.settings ?: return Outcome.Failure(SocialError.Unknown("not_loaded"))
        val saved = transform(current)
        state.value = PrivacyState.Loaded(saved)
        return Outcome.Success(saved)
    }

    companion object {
        val DEFAULT_PRIVACY = PrivacySettings(
            activityVisibility = Visibility.Friends,
            shareOnlineStatus = false,
            discoverable = true,
            acceptFriendRequests = true,
        )
    }
}

class FakePresenceRepository : PresenceRepository {
    val online = MutableStateFlow<Set<String>>(emptySet())
    override val onlineFriendIds: Flow<Set<String>> = online
}

class FakeFriendActivityRepository : FriendActivityRepository {
    /** Days per user, as the server would return them. */
    val days = MutableStateFlow<Map<String, List<DailyPlatformActivity>>>(emptyMap())
    var suggestionsResult: Outcome<List<FriendSuggestion>, SocialError> = Outcome.Success(emptyList())
    var activityCalls = 0

    private val changed = MutableSharedFlow<String>(extraBufferCapacity = 16)
    override val changes: Flow<String> = changed

    fun emitChange(userId: String) {
        changed.tryEmit(userId)
    }

    override suspend fun activity(
        userIds: Collection<String>,
        from: LocalDate,
    ): Outcome<Map<String, List<DailyPlatformActivity>>, SocialError> {
        activityCalls++
        return Outcome.Success(
            days.value.filterKeys { it in userIds }.mapValues { (_, list) -> list.filter { !it.date.isBefore(from) } },
        )
    }

    override suspend fun suggestions(): Outcome<List<FriendSuggestion>, SocialError> = suggestionsResult
}
