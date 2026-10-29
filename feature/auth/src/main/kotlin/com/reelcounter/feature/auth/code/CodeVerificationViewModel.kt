package com.reelcounter.feature.auth.code

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reelcounter.core.domain.Outcome
import com.reelcounter.core.domain.auth.AuthError
import com.reelcounter.core.domain.auth.AuthRepository
import com.reelcounter.core.domain.auth.OtpRules
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CodeVerificationUiState(
    val email: String,
    val code: String = "",
    val isVerifying: Boolean = false,
    val error: AuthError? = null,
    /** Seconds until another code may be requested; 0 means "Resend" is available. */
    val resendCooldownSeconds: Int = RESEND_COOLDOWN_SECONDS,
    val isResending: Boolean = false,
    /** True briefly after a new code was sent, to confirm it to the user. */
    val codeResent: Boolean = false,
)

internal const val RESEND_COOLDOWN_SECONDS = 60

/**
 * Entering a 6-digit code from an email. Verification starts by itself once all six digits are
 * in, including when pasted. On success the app switches screens when auth state changes.
 * The email comes from the route argument.
 */
abstract class CodeVerificationViewModel(savedStateHandle: SavedStateHandle) : ViewModel() {

    private val _uiState = MutableStateFlow(
        CodeVerificationUiState(email = checkNotNull(savedStateHandle.get<String>("email")) { "email route argument missing" }),
    )
    val uiState: StateFlow<CodeVerificationUiState> = _uiState.asStateFlow()

    private var cooldownJob: Job? = null

    protected abstract suspend fun verify(email: String, code: String): Outcome<Unit, AuthError>

    protected abstract suspend fun resend(email: String): Outcome<Unit, AuthError>

    init {
        // A code was just sent by the previous screen, so the resend timer starts now.
        startCooldown()
    }

    fun onCodeChange(input: String) {
        val code = OtpRules.normalize(input)
        _uiState.update { it.copy(code = code, error = null) }
        if (OtpRules.isComplete(code)) verify()
    }

    fun verify() {
        val current = _uiState.value
        if (current.isVerifying || !OtpRules.isComplete(current.code)) return
        _uiState.update { it.copy(isVerifying = true, error = null, codeResent = false) }
        viewModelScope.launch {
            val result = verify(current.email, current.code)
            _uiState.update {
                when (result) {
                    is Outcome.Success -> it.copy(isVerifying = false)
                    // Clear the code so the next attempt starts fresh.
                    is Outcome.Failure -> it.copy(isVerifying = false, error = result.error, code = "")
                }
            }
        }
    }

    fun resendCode() {
        val current = _uiState.value
        if (current.isResending || current.resendCooldownSeconds > 0) return
        _uiState.update { it.copy(isResending = true, error = null, codeResent = false) }
        viewModelScope.launch {
            val result = resend(current.email)
            _uiState.update {
                when (result) {
                    is Outcome.Success -> it.copy(isResending = false, codeResent = true)
                    is Outcome.Failure -> it.copy(isResending = false, error = result.error)
                }
            }
            if (result is Outcome.Success) startCooldown()
        }
    }

    private fun startCooldown() {
        cooldownJob?.cancel()
        cooldownJob = viewModelScope.launch {
            for (remaining in RESEND_COOLDOWN_SECONDS downTo 0) {
                _uiState.update { it.copy(resendCooldownSeconds = remaining) }
                if (remaining > 0) delay(1_000)
            }
        }
    }
}

@HiltViewModel
class VerifyEmailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val authRepository: AuthRepository,
) : CodeVerificationViewModel(savedStateHandle) {
    override suspend fun verify(email: String, code: String) = authRepository.verifySignUpCode(email, code)
    override suspend fun resend(email: String) = authRepository.resendSignUpCode(email)
}

@HiltViewModel
class ResetCodeViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val authRepository: AuthRepository,
) : CodeVerificationViewModel(savedStateHandle) {
    override suspend fun verify(email: String, code: String) = authRepository.verifyPasswordResetCode(email, code)
    override suspend fun resend(email: String) = authRepository.sendPasswordResetCode(email)
}
