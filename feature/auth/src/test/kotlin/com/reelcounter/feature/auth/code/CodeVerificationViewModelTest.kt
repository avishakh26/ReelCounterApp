package com.reelcounter.feature.auth.code

import androidx.lifecycle.SavedStateHandle
import com.reelcounter.core.domain.Outcome
import com.reelcounter.core.domain.auth.AuthError
import com.reelcounter.core.testing.FakeAuthRepository
import com.reelcounter.core.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class CodeVerificationViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val auth = FakeAuthRepository()

    private fun verifyEmailViewModel() = VerifyEmailViewModel(SavedStateHandle(mapOf("email" to "sam@example.com")), auth)

    @Test
    fun `six digits verify automatically, including pasted codes with spaces`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = verifyEmailViewModel()

        viewModel.onCodeChange("123 45")
        assertEquals(emptyList(), auth.calls)

        viewModel.onCodeChange("123 456")
        assertEquals(listOf("verifySignUpCode:sam@example.com:123456"), auth.calls)
    }

    @Test
    fun `a wrong code shows an error and clears the field`() = runTest(mainDispatcherRule.testDispatcher) {
        auth.result = Outcome.Failure(AuthError.InvalidOrExpiredCode)
        val viewModel = verifyEmailViewModel()

        viewModel.onCodeChange("000000")

        assertEquals(AuthError.InvalidOrExpiredCode, viewModel.uiState.value.error)
        assertEquals("", viewModel.uiState.value.code)
    }

    @Test
    fun `resending waits for the cooldown, then restarts it`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = verifyEmailViewModel()
        assertEquals(RESEND_COOLDOWN_SECONDS, viewModel.uiState.value.resendCooldownSeconds)

        viewModel.resendCode()
        assertEquals(emptyList(), auth.calls)

        advanceTimeBy(RESEND_COOLDOWN_SECONDS * 1_000L + 1)
        assertEquals(0, viewModel.uiState.value.resendCooldownSeconds)

        viewModel.resendCode()
        assertEquals(listOf("resendSignUpCode:sam@example.com"), auth.calls)
        assertEquals(true, viewModel.uiState.value.codeResent)
        assertEquals(RESEND_COOLDOWN_SECONDS, viewModel.uiState.value.resendCooldownSeconds)
    }

    @Test
    fun `the reset-code variant verifies a recovery code`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = ResetCodeViewModel(SavedStateHandle(mapOf("email" to "sam@example.com")), auth)

        viewModel.onCodeChange("654321")

        assertEquals(listOf("verifyPasswordResetCode:sam@example.com:654321"), auth.calls)
    }
}
