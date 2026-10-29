package com.reelcounter.core.data.auth

import com.reelcounter.core.data.FakeAuthRemoteDataSource
import com.reelcounter.core.data.FakePasswordResetFlag
import com.reelcounter.core.domain.Outcome
import com.reelcounter.core.domain.auth.AuthError
import com.reelcounter.core.domain.auth.AuthState
import com.reelcounter.core.domain.auth.SignUpResult
import com.reelcounter.core.network.RemoteException
import com.reelcounter.core.network.SupabaseConfig
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultAuthRepositoryTest {

    private val configured = SupabaseConfig(url = "https://project.supabase.co", publishableKey = "sb_publishable_x")
    private val remote = FakeAuthRemoteDataSource()
    private val log = mutableListOf<String>()
    private val flag = FakePasswordResetFlag(log)

    private fun TestScope.repository(config: SupabaseConfig = configured) =
        DefaultAuthRepository(remote, config, flag, backgroundScope)

    @Test
    fun `an unconfigured build reports NotConfigured and makes no calls`() = runTest(UnconfinedTestDispatcher()) {
        val repo = repository(SupabaseConfig(url = "https://unconfigured.invalid", publishableKey = "unconfigured"))

        assertEquals(AuthState.NotConfigured, repo.authState.value)
        assertEquals(Outcome.Failure(AuthError.NotConfigured), repo.signIn("a@b.co", "password1"))
        assertEquals(emptyList(), remote.calls)
    }

    @Test
    fun `signing in moves to SignedIn`() = runTest(UnconfinedTestDispatcher()) {
        val repo = repository()
        assertEquals(AuthState.SignedOut, repo.authState.value)

        repo.signIn("sam@example.com", "password1")

        assertEquals(AuthState.SignedIn("user-1", "sam@example.com"), repo.authState.value)
    }

    @Test
    fun `sign-up records the age gate and reports whether verification is needed`() = runTest(UnconfinedTestDispatcher()) {
        val repo = repository()

        assertEquals(Outcome.Success(SignUpResult.VerificationRequired), repo.signUp("sam@example.com", "password1"))
        assertEquals(mapOf("age_gate" to "16+"), remote.signUpMetadata.single())

        remote.signUpNeedsVerification = false
        assertEquals(Outcome.Success(SignUpResult.SignedIn), repo.signUp("sam@example.com", "password1"))
    }

    @Test
    fun `a reset code signs in into PasswordResetPending until the new password is saved`() = runTest(UnconfinedTestDispatcher()) {
        val repo = repository()

        assertEquals(Outcome.Success(Unit), repo.verifyPasswordResetCode("sam@example.com", "123456"))
        assertIs<AuthState.PasswordResetPending>(repo.authState.value)
        // The flag is raised before verifying, so the main app never flashes up.
        assertEquals("flag=true", log.first())

        assertEquals(Outcome.Success(Unit), repo.completePasswordReset("new-password"))
        assertEquals(AuthState.SignedIn("user-1", "sam@example.com"), repo.authState.value)
    }

    @Test
    fun `a wrong reset code clears the pending flag and maps the error`() = runTest(UnconfinedTestDispatcher()) {
        val repo = repository()
        remote.failure = RemoteException(RemoteException.Kind.Auth, code = "otp_expired")

        assertEquals(Outcome.Failure(AuthError.InvalidOrExpiredCode), repo.verifyPasswordResetCode("sam@example.com", "000000"))
        assertEquals(listOf("flag=true", "flag=false"), log)
        assertEquals(AuthState.SignedOut, repo.authState.value)
    }

    @Test
    fun `signing out offline still signs out and clears a pending reset`() = runTest(UnconfinedTestDispatcher()) {
        val repo = repository()
        repo.verifyPasswordResetCode("sam@example.com", "123456")
        remote.failure = RemoteException(RemoteException.Kind.Network)

        repo.signOut()

        assertEquals(AuthState.SignedOut, repo.authState.value)
        assertEquals(false, flag.isPending.value)
    }
}
