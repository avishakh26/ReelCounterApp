package com.reelcounter.feature.auth.reset

import androidx.lifecycle.SavedStateHandle
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

data class ForgotPasswordUiState(
    val email: String = "",
    val emailIssue: EmailIssue? = null,
    val isSubmitting: Boolean = false,
    val error: AuthError? = null,
    /** Set once after the code was sent; the screen navigates and clears it. */
    val codeSentTo: String? = null,
)

@HiltViewModel
class ForgotPasswordViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val authRepository: AuthRepository,
) : ViewModel() {

    // Prefilled with whatever was typed on the sign-in screen.
    private val _uiState = MutableStateFlow(ForgotPasswordUiState(email = savedStateHandle.get<String>("email").orEmpty()))
    val uiState: StateFlow<ForgotPasswordUiState> = _uiState.asStateFlow()

    fun onEmailChange(value: String) = _uiState.update { it.copy(email = value, emailIssue = null, error = null) }

    fun sendCode() {
        val current = _uiState.value
        if (current.isSubmitting) return
        val email = EmailRules.normalize(current.email)
        EmailRules.validate(email)?.let { issue ->
            _uiState.update { it.copy(emailIssue = issue) }
            return
        }
        _uiState.update { it.copy(email = email, isSubmitting = true, error = null) }
        viewModelScope.launch {
            when (val result = authRepository.sendPasswordResetCode(email)) {
                is Outcome.Success -> _uiState.update { it.copy(isSubmitting = false, codeSentTo = email) }
                is Outcome.Failure -> _uiState.update { it.copy(isSubmitting = false, error = result.error) }
            }
        }
    }

    fun onCodeSentHandled() = _uiState.update { it.copy(codeSentTo = null) }
}
