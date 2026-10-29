package com.reelcounter.core.domain.social

import com.reelcounter.core.domain.Outcome
import com.reelcounter.core.model.Connection
import com.reelcounter.core.model.DailyPlatformActivity
import com.reelcounter.core.model.PrivacySettings
import com.reelcounter.core.model.UserSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalDate

/**
 * Friends and friend requests. Every action is authorized on the server; the client can't act
 * on anyone else's behalf.
 */
interface FriendsRepository {
    val connections: StateFlow<ConnectionsState>

    suspend fun refresh()

    suspend fun search(query: String): Outcome<List<Connection>, SocialError>

    suspend fun sendRequest(userId: String): Outcome<FriendRequestResult, SocialError>
    suspend fun respond(userId: String, accept: Boolean): Outcome<Unit, SocialError>
    suspend fun cancelRequest(userId: String): Outcome<Unit, SocialError>
    suspend fun removeFriend(userId: String): Outcome<Unit, SocialError>
    suspend fun block(userId: String): Outcome<Unit, SocialError>
    suspend fun unblock(userId: String): Outcome<Unit, SocialError>

    suspend fun blockedUsers(): Outcome<List<UserSummary>, SocialError>
}

sealed interface ConnectionsState {
    data object Loading : ConnectionsState
    data class Loaded(
        val friends: List<UserSummary>,
        val incoming: List<UserSummary>,
        val outgoing: List<UserSummary>,
    ) : ConnectionsState
    data class Failed(val error: SocialError) : ConnectionsState
}

enum class FriendRequestResult {
    Requested,

    /** They had already asked you, so you're now friends. */
    Accepted,
    AlreadyFriends,
    AlreadyRequested,

    /** The person turned off friend requests. */
    NotAccepting,

    /** No such user, or one of you blocked the other (deliberately indistinguishable). */
    NotFound,
}

sealed interface SocialError {
    data object Network : SocialError
    data object RateLimited : SocialError
    data object NotSignedIn : SocialError
    data class Unknown(val code: String?) : SocialError
}

/** The signed-in user's own privacy settings. */
interface PrivacyRepository {
    val settings: StateFlow<PrivacyState>

    suspend fun refresh()

    suspend fun update(transform: (PrivacySettings) -> PrivacySettings): Outcome<PrivacySettings, SocialError>
}

sealed interface PrivacyState {
    data object Loading : PrivacyState
    data class Loaded(val settings: PrivacySettings) : PrivacyState
    data class Failed(val error: SocialError) : PrivacyState
}

/** Which friends are using the app right now. Only friends who opted in ever appear. */
interface PresenceRepository {
    val onlineFriendIds: Flow<Set<String>>
}

/**
 * What friends share of their days, and people the user might know. Everything is filtered on
 * the server by each person's own visibility settings.
 */
interface FriendActivityRepository {
    /** Ids of friends whose figures just changed, while the app is open. */
    val changes: Flow<String>

    /**
     * Each user's per-platform days on or after [from]. Users who share nothing (or have no
     * recorded days) are missing from the map rather than shown as zero.
     */
    suspend fun activity(userIds: Collection<String>, from: LocalDate): Outcome<Map<String, List<DailyPlatformActivity>>, SocialError>

    /** Friends of friends first, then other discoverable people. */
    suspend fun suggestions(): Outcome<List<FriendSuggestion>, SocialError>
}

data class FriendSuggestion(val user: UserSummary, val mutualFriends: Int)
