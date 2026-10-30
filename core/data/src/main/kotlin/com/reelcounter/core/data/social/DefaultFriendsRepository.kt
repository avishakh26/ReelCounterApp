package com.reelcounter.core.data.social

import com.reelcounter.core.common.dispatchers.ApplicationScope
import com.reelcounter.core.data.error.isServerOutdated
import com.reelcounter.core.domain.Outcome
import com.reelcounter.core.domain.auth.AuthRepository
import com.reelcounter.core.domain.auth.AuthState
import com.reelcounter.core.domain.map
import com.reelcounter.core.domain.social.ConnectionsState
import com.reelcounter.core.domain.social.FriendRequestResult
import com.reelcounter.core.domain.social.FriendsRepository
import com.reelcounter.core.domain.social.SocialError
import com.reelcounter.core.model.Connection
import com.reelcounter.core.model.Relationship
import com.reelcounter.core.model.UserSummary
import com.reelcounter.core.network.RemoteException
import com.reelcounter.core.network.SupabaseConfig
import com.reelcounter.core.network.social.NetworkUserCard
import com.reelcounter.core.network.social.SocialRemoteDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class DefaultFriendsRepository @Inject constructor(
    private val remote: SocialRemoteDataSource,
    private val config: SupabaseConfig,
    authRepository: AuthRepository,
    @ApplicationScope scope: CoroutineScope,
) : FriendsRepository {

    private val state = MutableStateFlow<ConnectionsState>(ConnectionsState.Loading)
    override val connections: StateFlow<ConnectionsState> = state.asStateFlow()

    init {
        scope.launch {
            authRepository.authState
                .map { (it as? AuthState.SignedIn)?.userId }
                .distinctUntilChanged()
                .collectLatest { userId -> if (userId == null) state.value = ConnectionsState.Loading else refresh() }
        }
    }

    override suspend fun refresh() {
        when (val result = call { remote.connections() }) {
            is Outcome.Success -> state.value = ConnectionsState.Loaded(
                friends = result.value.filter { it.status == "friend" }.map { it.toSummary() },
                incoming = result.value.filter { it.status == "incoming" }.map { it.toSummary() },
                outgoing = result.value.filter { it.status == "outgoing" }.map { it.toSummary() },
            )
            // Keep showing the last list if a refresh fails (e.g. offline); fail only with nothing to show.
            is Outcome.Failure -> if (state.value !is ConnectionsState.Loaded) state.value = ConnectionsState.Failed(result.error)
        }
    }

    override suspend fun search(query: String): Outcome<List<Connection>, SocialError> =
        call { remote.search(query) }.map { cards -> cards.map { Connection(it.toSummary(), it.relationship.toRelationship()) } }

    override suspend fun sendRequest(userId: String): Outcome<FriendRequestResult, SocialError> =
        action { remote.sendFriendRequest(userId) }.map { it.toRequestResult() }

    override suspend fun respond(userId: String, accept: Boolean) = action { remote.respondFriendRequest(userId, accept) }.map { }

    override suspend fun cancelRequest(userId: String) = action { remote.cancelFriendRequest(userId) }.map { }

    override suspend fun removeFriend(userId: String) = action { remote.removeFriend(userId) }.map { }

    override suspend fun block(userId: String) = action { remote.block(userId) }.map { }

    override suspend fun unblock(userId: String) = action { remote.unblock(userId) }.map { }

    override suspend fun blockedUsers(): Outcome<List<UserSummary>, SocialError> =
        call { remote.blockedUsers() }.map { cards -> cards.map { it.toSummary() } }

    /** Runs a friend action, then refreshes the lists so every screen reflects the server. */
    private suspend fun action(block: suspend () -> String): Outcome<String, SocialError> =
        call(block).also { if (it is Outcome.Success) refresh() }

    private suspend fun <T> call(block: suspend () -> T): Outcome<T, SocialError> {
        if (!config.isConfigured) return Outcome.Failure(SocialError.Unknown("not_configured"))
        return try {
            Outcome.Success(block())
        } catch (e: RemoteException) {
            Outcome.Failure(e.toSocialError())
        }
    }

    private fun NetworkUserCard.toSummary() = UserSummary(id, username, displayName, avatarPath?.let(remote::avatarUrl))

    private fun com.reelcounter.core.network.social.NetworkConnection.toSummary() =
        UserSummary(userId, username, displayName, avatarPath?.let(remote::avatarUrl))
}

internal fun RemoteException.toSocialError(): SocialError = when (kind) {
    RemoteException.Kind.Network, RemoteException.Kind.Timeout -> SocialError.Network
    RemoteException.Kind.Database -> when (code) {
        "RC002" -> SocialError.RateLimited // private.consume_rate_limit
        "42501" -> SocialError.NotSignedIn
        else -> if (isServerOutdated()) SocialError.ServerOutdated else SocialError.Unknown(code)
    }
    RemoteException.Kind.Auth, RemoteException.Kind.Server, RemoteException.Kind.Unknown -> SocialError.Unknown(code)
}

internal fun String?.toRelationship(): Relationship = when (this) {
    "friend" -> Relationship.Friend
    "incoming" -> Relationship.IncomingRequest
    "outgoing" -> Relationship.OutgoingRequest
    else -> Relationship.None
}

internal fun String.toRequestResult(): FriendRequestResult = when (this) {
    "requested" -> FriendRequestResult.Requested
    "accepted" -> FriendRequestResult.Accepted
    "already_friends" -> FriendRequestResult.AlreadyFriends
    "already_requested" -> FriendRequestResult.AlreadyRequested
    "not_accepting" -> FriendRequestResult.NotAccepting
    else -> FriendRequestResult.NotFound
}
