package com.reelcounter.core.data.profile

import com.reelcounter.core.data.FakeProfileRemoteDataSource
import com.reelcounter.core.domain.Outcome
import com.reelcounter.core.domain.auth.AuthState
import com.reelcounter.core.domain.profile.AvatarImage
import com.reelcounter.core.domain.profile.ProfileError
import com.reelcounter.core.domain.profile.ProfileField
import com.reelcounter.core.domain.profile.ProfileState
import com.reelcounter.core.domain.profile.ProfileUpdate
import com.reelcounter.core.domain.profile.UsernameAvailability
import com.reelcounter.core.network.RemoteException
import com.reelcounter.core.network.profile.NetworkProfile
import com.reelcounter.core.testing.FakeAuthRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultProfileRepositoryTest {

    private val remote = FakeProfileRemoteDataSource().apply {
        profiles["user-1"] = NetworkProfile(id = "user-1", avatarPath = "user-1/old.webp")
        storedAvatars += "user-1/old.webp"
    }
    private val auth = FakeAuthRepository(AuthState.SignedIn("user-1", "sam@example.com"))
    private val avatar = AvatarImage(byteArrayOf(1, 2, 3), "image/webp")

    private fun TestScope.repository() = DefaultProfileRepository(remote, auth, backgroundScope)

    @Test
    fun `loads the profile when signed in and clears it on sign-out`() = runTest(UnconfinedTestDispatcher()) {
        val repo = repository()
        val loaded = assertIs<ProfileState.Loaded>(repo.profileState.value)
        assertEquals("https://cdn.test/user-1/old.webp", loaded.profile.avatarUrl)
        assertEquals(false, loaded.profile.isComplete)

        auth.state.value = AuthState.SignedOut
        assertEquals(ProfileState.None, repo.profileState.value)
    }

    @Test
    fun `a failed load is reported, and refresh retries`() = runTest(UnconfinedTestDispatcher()) {
        remote.fetchFailure = RemoteException(RemoteException.Kind.Network)
        val repo = repository()
        assertEquals(ProfileState.Failed(ProfileError.Network), repo.profileState.value)

        remote.fetchFailure = null
        repo.refresh()
        assertIs<ProfileState.Loaded>(repo.profileState.value)
    }

    @Test
    fun `a new avatar is uploaded to the user's folder and the old one deleted`() = runTest(UnconfinedTestDispatcher()) {
        val repo = repository()

        val result = repo.saveProfile(ProfileUpdate("sam.k", "Sam", null), avatar)

        val saved = assertIs<Outcome.Success<*>>(result)
        val newPath = remote.profiles.getValue("user-1").avatarPath!!
        assertTrue(newPath.startsWith("user-1/") && newPath.endsWith(".webp"), "unexpected path $newPath")
        assertEquals(setOf(newPath), remote.storedAvatars)
        assertEquals(listOf("user-1/old.webp"), remote.deletedAvatars)
        assertIs<ProfileState.Loaded>(repo.profileState.value)
        assertTrue(saved.value.toString().contains("sam.k"))
    }

    @Test
    fun `if saving fails after upload, the uploaded file is removed and the old one kept`() = runTest(UnconfinedTestDispatcher()) {
        val repo = repository()
        remote.updateFailure = RemoteException(RemoteException.Kind.Database, code = "23505")

        val result = repo.saveProfile(ProfileUpdate("taken", null, null), avatar)

        assertEquals(Outcome.Failure(ProfileError.UsernameTaken), result)
        assertEquals(setOf("user-1/old.webp"), remote.storedAvatars)
    }

    @Test
    fun `invalid input is rejected before any network call`() = runTest(UnconfinedTestDispatcher()) {
        val repo = repository()

        assertEquals(
            Outcome.Failure(ProfileError.InvalidInput(ProfileField.Username)),
            repo.saveProfile(ProfileUpdate("NO", null, null), avatar),
        )
        assertEquals(setOf("user-1/old.webp"), remote.storedAvatars)
    }

    @Test
    fun `username checks skip the server for malformed names`() = runTest(UnconfinedTestDispatcher()) {
        val repo = repository()

        assertEquals(Outcome.Success(UsernameAvailability.Invalid), repo.checkUsername("a"))
        assertEquals(0, remote.checkCalls)

        remote.usernameStatus = "reserved"
        assertEquals(Outcome.Success(UsernameAvailability.Reserved), repo.checkUsername("admin"))
    }
}
