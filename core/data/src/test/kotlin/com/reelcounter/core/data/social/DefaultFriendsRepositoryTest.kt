package com.reelcounter.core.data.social

import com.reelcounter.core.domain.Outcome
import com.reelcounter.core.domain.auth.AuthState
import com.reelcounter.core.domain.social.ConnectionsState
import com.reelcounter.core.domain.social.FriendRequestResult
import com.reelcounter.core.domain.social.SocialError
import com.reelcounter.core.model.Relationship
import com.reelcounter.core.model.UserSummary
import com.reelcounter.core.network.RemoteException
import com.reelcounter.core.network.SupabaseConfig
import com.reelcounter.core.network.social.NetworkConnection
import com.reelcounter.core.network.social.NetworkPrivacySettings
import com.reelcounter.core.network.social.NetworkUserCard
import com.reelcounter.core.network.social.SocialRemoteDataSource
import com.reelcounter.core.testing.FakeAuthRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultFriendsRepositoryTest {

    private val remote = FakeSocialRemoteDataSource()
    private val auth = FakeAuthRepository(AuthState.SignedIn("user-1", "sam@example.com"))
    private val configured = SupabaseConfig(url = "https://project.supabase.co", publishableKey = "sb_publishable_x")

    private fun TestScope.repository(config: SupabaseConfig = configured) =
        DefaultFriendsRepository(remote, config, auth, backgroundScope)

    @Test
    fun `connections are split into friends, incoming and outgoing on sign-in`() = runTest(UnconfinedTestDispatcher()) {
        remote.connections = listOf(
            NetworkConnection(userId = "a", username = "amy", avatarPath = "a/1.webp", status = "friend"),
            NetworkConnection(userId = "b", username = "ben", status = "incoming"),
            NetworkConnection(userId = "c", username = "cat", displayName = "Cat", status = "outgoing"),
        )

        val loaded = assertIs<ConnectionsState.Loaded>(repository().connections.value)

        assertEquals(listOf(UserSummary("a", "amy", null, "https://cdn.test/a/1.webp")), loaded.friends)
        assertEquals(listOf("b"), loaded.incoming.map { it.id })
        assertEquals(listOf(UserSummary("c", "cat", "Cat", null)), loaded.outgoing)
    }

    @Test
    fun `signing out forgets the lists`() = runTest(UnconfinedTestDispatcher()) {
        val repo = repository()
        assertIs<ConnectionsState.Loaded>(repo.connections.value)

        auth.state.value = AuthState.SignedOut

        assertEquals(ConnectionsState.Loading, repo.connections.value)
    }

    @Test
    fun `a failed refresh keeps the last list, and fails only with nothing to show`() = runTest(UnconfinedTestDispatcher()) {
        remote.failure = RemoteException(RemoteException.Kind.Network)
        val repo = repository()
        assertEquals(ConnectionsState.Failed(SocialError.Network), repo.connections.value)

        remote.failure = null
        repo.refresh()
        assertIs<ConnectionsState.Loaded>(repo.connections.value)

        remote.failure = RemoteException(RemoteException.Kind.Timeout)
        repo.refresh()
        assertIs<ConnectionsState.Loaded>(repo.connections.value)
    }

    @Test
    fun `search results carry the relationship`() = runTest(UnconfinedTestDispatcher()) {
        remote.searchResults = listOf(
            NetworkUserCard(idFromSearch = "a", username = "amy", relationship = "friend"),
            NetworkUserCard(idFromSearch = "b", username = "ben", relationship = "incoming"),
            NetworkUserCard(idFromSearch = "c", username = "cat", relationship = "outgoing"),
            NetworkUserCard(idFromSearch = "d", username = "dan", relationship = null),
        )

        val result = repository().search("a")

        check(result is Outcome.Success)
        assertEquals(
            listOf(Relationship.Friend, Relationship.IncomingRequest, Relationship.OutgoingRequest, Relationship.None),
            result.value.map { it.relationship },
        )
    }

    @Test
    fun `a successful action refreshes the lists, a failed one doesn't`() = runTest(UnconfinedTestDispatcher()) {
        val repo = repository()
        val before = remote.connectionCalls

        assertEquals(Outcome.Success(FriendRequestResult.Requested), repo.sendRequest("b"))
        assertEquals(before + 1, remote.connectionCalls)

        remote.failure = RemoteException(RemoteException.Kind.Database, code = "RC002")
        assertEquals(Outcome.Failure(SocialError.RateLimited), repo.sendRequest("b"))
        assertEquals(before + 1, remote.connectionCalls)
    }

    @Test
    fun `nothing is sent when the backend isn't configured`() = runTest(UnconfinedTestDispatcher()) {
        val repo = repository(SupabaseConfig(url = "https://unconfigured.invalid", publishableKey = "unconfigured"))

        assertIs<Outcome.Failure<*>>(repo.block("b"))
        assertEquals(emptyList(), remote.calls)
    }

    @Test
    fun `server answers map to request results`() {
        assertEquals(FriendRequestResult.Requested, "requested".toRequestResult())
        assertEquals(FriendRequestResult.Accepted, "accepted".toRequestResult())
        assertEquals(FriendRequestResult.AlreadyFriends, "already_friends".toRequestResult())
        assertEquals(FriendRequestResult.AlreadyRequested, "already_requested".toRequestResult())
        assertEquals(FriendRequestResult.NotAccepting, "not_accepting".toRequestResult())
        assertEquals(FriendRequestResult.NotFound, "not_found".toRequestResult())
        assertEquals(FriendRequestResult.NotFound, "something_new".toRequestResult())
    }

    @Test
    fun `errors map to what the user can act on`() {
        assertEquals(SocialError.Network, RemoteException(RemoteException.Kind.Network).toSocialError())
        assertEquals(SocialError.Network, RemoteException(RemoteException.Kind.Timeout).toSocialError())
        assertEquals(SocialError.RateLimited, RemoteException(RemoteException.Kind.Database, code = "RC002").toSocialError())
        assertEquals(SocialError.NotSignedIn, RemoteException(RemoteException.Kind.Database, code = "42501").toSocialError())
        assertEquals(SocialError.Unknown("P0001"), RemoteException(RemoteException.Kind.Database, code = "P0001").toSocialError())
    }
}

private class FakeSocialRemoteDataSource : SocialRemoteDataSource {
    var connections = emptyList<NetworkConnection>()
    var searchResults = emptyList<NetworkUserCard>()
    var failure: RemoteException? = null
    val calls = mutableListOf<String>()
    var connectionCalls = 0

    private fun record(call: String) {
        calls += call
        failure?.let { throw it }
    }

    override suspend fun search(prefix: String): List<NetworkUserCard> {
        record("search")
        return searchResults
    }

    override suspend fun connections(): List<NetworkConnection> {
        connectionCalls++
        record("connections")
        return connections
    }

    override suspend fun blockedUsers(): List<NetworkUserCard> {
        record("blockedUsers")
        return emptyList()
    }

    override suspend fun sendFriendRequest(target: String) = "requested".also { record("send") }

    override suspend fun respondFriendRequest(requester: String, accept: Boolean) = "accepted".also { record("respond") }

    override suspend fun cancelFriendRequest(target: String) = "cancelled".also { record("cancel") }

    override suspend fun removeFriend(target: String) = "removed".also { record("remove") }

    override suspend fun block(target: String) = "blocked".also { record("block") }

    override suspend fun unblock(target: String) = "unblocked".also { record("unblock") }

    override suspend fun privacySettings(userId: String): NetworkPrivacySettings? = null

    override suspend fun updatePrivacySettings(userId: String, settings: NetworkPrivacySettings) = settings

    override fun avatarUrl(path: String) = "https://cdn.test/$path"
}
