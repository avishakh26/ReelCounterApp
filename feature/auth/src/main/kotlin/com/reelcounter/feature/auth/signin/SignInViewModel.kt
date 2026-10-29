package com.reelcounter.feature.auth.signin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reelcounter.core.domain.Outcome
import com.reelcounter.core.domain.auth.AuthError
import com.reelcounter.core.domain.auth.AuthRepository
import com.reelcounter.core.domain.auth.EmailIssue
import com.reelcounter.core.domain.auth.EmailRules
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SignInUiState(
    val email: String = "",
    val password: String = "",
    val emailIssue: EmailIssue? = null,
    val passwordMissing: Boolean = false,
    val isSubmitting: Boolean = false,
    val error: AuthError? = null,
    /** Set once when the email still needs verifying; the screen navigates and clears it. */
    val verifyEmail: String? = null,
)

/** Successful sign-in needs no navigation here: the app switches screens when auth state changes. */
@HiltViewModel
class SignInViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SignInUiState())
    val uiState: StateFlow<SignInUiState> = _uiState.asStateFlow()

    fun onEmailChange(value: String) = _uiState.update { it.copy(email = value, emailIssue = null, error = null) }

    fun onPasswordChange(value: String) = _uiState.update { it.copy(password = value, passwordMissing = false, error = null) }

    fun signIn() {
        val current = _uiState.value
        if (current.isSubmitting) return

        val email = EmailRules.normalize(current.email)
        val emailIssue = EmailRules.validate(email)
        val passwordMissing = current.password.isEmpty()
        if (emailIssue != null || passwordMissing) {
            _uiState.update { it.copy(emailIssue = emailIssue, passwordMissing = passwordMissing) }
            return
        }

        _uiState.update { it.copy(email = email, isSubmitting = true, error = null) }
        viewModelScope.launch {
            when (val result = authRepository.signIn(email, current.password)) {
                is Outcome.Success -> _uiState.update { it.copy(isSubmitting = false, password = "") }
                is Outcome.Failure ->
                    if (result.error == AuthError.EmailNotConfirmed) {
                        // Send a fresh code and continue straight to verification.
                        authRepository.resendSignUpCode(email)
                        _uiState.update { it.copy(isSubmitting = false, verifyEmail = email) }
                    } else {
                        _uiState.update { it.copy(isSubmitting = false, error = result.error) }
                    }
            }
        }
    }

    fun onVerifyEmailHandled() = _uiState.update { it.copy(verifyEmail = null) }
}
