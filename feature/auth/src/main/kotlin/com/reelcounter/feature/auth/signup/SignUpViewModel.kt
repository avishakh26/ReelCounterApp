package com.reelcounter.feature.auth.signup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reelcounter.core.domain.Outcome
import com.reelcounter.core.domain.auth.AuthError
import com.reelcounter.core.domain.auth.AuthRepository
import com.reelcounter.core.domain.auth.EmailIssue
import com.reelcounter.core.domain.auth.EmailRules
import com.reelcounter.core.domain.auth.PasswordIssue
import com.reelcounter.core.domain.auth.PasswordRules
import com.reelcounter.core.domain.auth.SignUpResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SignUpUiState(
    val email: String = "",
    val password: String = "",
    /** The user confirmed they meet the minimum age (16+, decision D4). Required. */
    val ageConfirmed: Boolean = false,
    val emailIssue: EmailIssue? = null,
    val passwordIssue: PasswordIssue? = null,
    val ageConfirmationMissing: Boolean = false,
    val isSubmitting: Boolean = false,
    val error: AuthError? = null,
    /** Set once when a code was emailed; the screen navigates and clears it. */
    val verifyEmail: String? = null,
)

@HiltViewModel
class SignUpViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SignUpUiState())
    val uiState: StateFlow<SignUpUiState> = _uiState.asStateFlow()

    fun onEmailChange(value: String) = _uiState.update { it.copy(email = value, emailIssue = null, error = null) }

    fun onPasswordChange(value: String) = _uiState.update { it.copy(password = value, passwordIssue = null, error = null) }

    fun onAgeConfirmedChange(value: Boolean) = _uiState.update { it.copy(ageConfirmed = value, ageConfirmationMissing = false) }

    fun signUp() {
        val current = _uiState.value
        if (current.isSubmitting) return

        val email = EmailRules.normalize(current.email)
        val emailIssue = EmailRules.validate(email)
        val passwordIssue = PasswordRules.validate(current.password)
        val ageMissing = !current.ageConfirmed
        if (emailIssue != null || passwordIssue != null || ageMissing) {
            _uiState.update {
                it.copy(emailIssue = emailIssue, passwordIssue = passwordIssue, ageConfirmationMissing = ageMissing)
            }
            return
        }

        _uiState.update { it.copy(email = email, isSubmitting = true, error = null) }
        viewModelScope.launch {
            when (val result = authRepository.signUp(email, current.password)) {
                is Outcome.Success -> _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        password = "",
                        // SignedIn needs nothing here: the app moves on to profile setup by itself.
                        verifyEmail = email.takeIf { result.value == SignUpResult.VerificationRequired },
                    )
                }
                is Outcome.Failure -> _uiState.update { it.copy(isSubmitting = false, error = result.error) }
            }
        }
    }

    fun onVerifyEmailHandled() = _uiState.update { it.copy(verifyEmail = null) }
}
