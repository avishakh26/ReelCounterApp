package com.reelcounter.feature.auth.signup

import com.reelcounter.core.domain.Outcome
import com.reelcounter.core.domain.auth.AuthError
import com.reelcounter.core.domain.auth.PasswordIssue
import com.reelcounter.core.domain.auth.SignUpResult
import com.reelcounter.core.testing.FakeAuthRepository
import com.reelcounter.core.testing.MainDispatcherRule
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SignUpViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val auth = FakeAuthRepository()
    private val viewModel = SignUpViewModel(auth)

    private fun fillValidForm(ageConfirmed: Boolean = true) {
        viewModel.onEmailChange("sam@example.com")
        viewModel.onPasswordChange("long-enough")
        viewModel.onAgeConfirmedChange(ageConfirmed)
    }

    @Test
    fun `the age confirmation is required`() {
        fillValidForm(ageConfirmed = false)
        viewModel.signUp()

        assertTrue(viewModel.uiState.value.ageConfirmationMissing)
        assertEquals(emptyList(), auth.calls)
    }

    @Test
    fun `short passwords are rejected before calling the server`() {
        fillValidForm()
        viewModel.onPasswordChange("short")
        viewModel.signUp()

        assertEquals(PasswordIssue.TooShort, viewModel.uiState.value.passwordIssue)
        assertEquals(emptyList(), auth.calls)
    }

    @Test
    fun `a pending verification navigates to the code screen`() {
        fillValidForm()
        viewModel.signUp()

        assertEquals("sam@example.com", viewModel.uiState.value.verifyEmail)
        assertEquals("", viewModel.uiState.value.password)
    }

    @Test
    fun `an auto-confirmed project needs no navigation`() {
        auth.signUpResult = Outcome.Success(SignUpResult.SignedIn)
        fillValidForm()
        viewModel.signUp()

        assertNull(viewModel.uiState.value.verifyEmail)
    }

    @Test
    fun `server errors are shown`() {
        auth.signUpResult = Outcome.Failure(AuthError.WeakPassword(listOf("pwned")))
        fillValidForm()
        viewModel.signUp()

        assertEquals(AuthError.WeakPassword(listOf("pwned")), viewModel.uiState.value.error)
    }
}
