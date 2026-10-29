package com.reelcounter.feature.auth.signin

import com.reelcounter.core.domain.Outcome
import com.reelcounter.core.domain.auth.AuthError
import com.reelcounter.core.domain.auth.EmailIssue
import com.reelcounter.core.testing.FakeAuthRepository
import com.reelcounter.core.testing.MainDispatcherRule
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SignInViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val auth = FakeAuthRepository()
    private val viewModel = SignInViewModel(auth)

    @Test
    fun `missing input is flagged without calling the server`() {
        viewModel.onEmailChange("not-an-email")
        viewModel.signIn()

        val state = viewModel.uiState.value
        assertEquals(EmailIssue.Invalid, state.emailIssue)
        assertTrue(state.passwordMissing)
        assertEquals(emptyList(), auth.calls)
    }

    @Test
    fun `successful sign-in trims the email and forgets the password`() {
        viewModel.onEmailChange("  sam@example.com ")
        viewModel.onPasswordChange("password1")
        viewModel.signIn()

        assertEquals(listOf("signIn:sam@example.com"), auth.calls)
        assertEquals("", viewModel.uiState.value.password)
        assertFalse(viewModel.uiState.value.isSubmitting)
    }

    @Test
    fun `wrong credentials show an error`() {
        auth.result = Outcome.Failure(AuthError.InvalidCredentials)
        viewModel.onEmailChange("sam@example.com")
        viewModel.onPasswordChange("wrong-password")
        viewModel.signIn()

        assertEquals(AuthError.InvalidCredentials, viewModel.uiState.value.error)
    }

    @Test
    fun `an unconfirmed email gets a fresh code and goes to verification`() {
        auth.result = Outcome.Failure(AuthError.EmailNotConfirmed)
        viewModel.onEmailChange("sam@example.com")
        viewModel.onPasswordChange("password1")
        viewModel.signIn()

        assertEquals(listOf("signIn:sam@example.com", "resendSignUpCode:sam@example.com"), auth.calls)
        assertEquals("sam@example.com", viewModel.uiState.value.verifyEmail)

        viewModel.onVerifyEmailHandled()
        assertEquals(null, viewModel.uiState.value.verifyEmail)
    }
}
