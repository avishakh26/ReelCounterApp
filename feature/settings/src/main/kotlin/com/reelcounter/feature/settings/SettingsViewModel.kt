package com.reelcounter.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reelcounter.core.domain.Outcome
import com.reelcounter.core.domain.auth.AuthError
import com.reelcounter.core.domain.auth.AuthRepository
import com.reelcounter.core.domain.profile.ProfileRepository
import com.reelcounter.core.domain.profile.ProfileState
import com.reelcounter.core.model.UserProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    profileRepository: ProfileRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    val profile: StateFlow<UserProfile?> = profileRepository.profileState
        .map { (it as? ProfileState.Loaded)?.profile }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _deletion = MutableStateFlow<AccountDeletion>(AccountDeletion.Idle)
    val deletion: StateFlow<AccountDeletion> = _deletion.asStateFlow()

    /** The app returns to sign-in on its own once the session is gone. */
    fun signOut() {
        viewModelScope.launch { authRepository.signOut() }
    }

    /** Deletes the account for good; on success the app returns to sign-in on its own. */
    fun deleteAccount() {
        if (_deletion.value == AccountDeletion.Deleting) return
        _deletion.value = AccountDeletion.Deleting
        viewModelScope.launch {
            _deletion.value = when (val outcome = authRepository.deleteAccount()) {
                is Outcome.Success -> AccountDeletion.Idle
                is Outcome.Failure -> AccountDeletion.Failed(outcome.error)
            }
        }
    }

    fun dismissDeletionError() {
        if (_deletion.value is AccountDeletion.Failed) _deletion.value = AccountDeletion.Idle
    }
}

sealed interface AccountDeletion {
    data object Idle : AccountDeletion
    data object Deleting : AccountDeletion
    data class Failed(val error: AuthError) : AccountDeletion
}
