package com.reelcounter.feature.auth.reset

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reelcounter.core.domain.Outcome
import com.reelcounter.core.domain.auth.AuthError
import com.reelcounter.core.domain.auth.AuthRepository
import com.reelcounter.core.domain.auth.PasswordIssue
import com.reelcounter.core.domain.auth.PasswordRules
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SetNewPasswordUiState(
    val password: String = "",
    val confirmation: String = "",
    val passwordIssue: PasswordIssue? = null,
    val mismatch: Boolean = false,
    val isSubmitting: Boolean = false,
    val error: AuthError? = null,
)

/**
 * The last step of a password reset. The user is already signed in by the reset code; saving
 * the password takes them into the app, cancelling signs them out again.
 */
@HiltViewModel
class SetNewPasswordViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SetNewPasswordUiState())
    val uiState: StateFlow<SetNewPasswordUiState> = _uiState.asStateFlow()

    fun onPasswordChange(value: String) = _uiState.update { it.copy(password = value, passwordIssue = null, error = null) }

    fun onConfirmationChange(value: String) = _uiState.update { it.copy(confirmation = value, mismatch = false) }

    fun save() {
        val current = _uiState.value
        if (current.isSubmitting) return
        val issue = PasswordRules.validate(current.password)
        val mismatch = current.password != current.confirmation
        if (issue != null || mismatch) {
            _uiState.update { it.copy(passwordIssue = issue, mismatch = issue == null && mismatch) }
            return
        }
        _uiState.update { it.copy(isSubmitting = true, error = null) }
        viewModelScope.launch {
            when (val result = authRepository.completePasswordReset(current.password)) {
                is Outcome.Success -> _uiState.update { it.copy(isSubmitting = false) }
                is Outcome.Failure -> _uiState.update { it.copy(isSubmitting = false, error = result.error) }
            }
        }
    }

    fun cancel() {
        viewModelScope.launch { authRepository.signOut() }
    }
}
