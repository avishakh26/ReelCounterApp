package com.reelcounter.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reelcounter.core.domain.auth.AuthRepository
import com.reelcounter.core.domain.auth.AuthState
import com.reelcounter.core.domain.profile.ProfileError
import com.reelcounter.core.domain.profile.ProfileRepository
import com.reelcounter.core.domain.profile.ProfileState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Which top-level experience the app shows. Derived entirely from auth and profile state. */
sealed interface AppState {
    data object Loading : AppState
    data object NotConfigured : AppState
    data object SignedOut : AppState
    data object SettingNewPassword : AppState
    data object NeedsProfile : AppState
    data class ProfileUnavailable(val error: ProfileError) : AppState
    data object Ready : AppState
}

@HiltViewModel
class MainActivityViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val profileRepository: ProfileRepository,
) : ViewModel() {

    val appState: StateFlow<AppState> = combine(authRepository.authState, profileRepository.profileState) { auth, profile ->
        when (auth) {
            AuthState.Initializing -> AppState.Loading
            AuthState.NotConfigured -> AppState.NotConfigured
            AuthState.SignedOut -> AppState.SignedOut
            is AuthState.PasswordResetPending -> AppState.SettingNewPassword
            is AuthState.SignedIn -> when (profile) {
                ProfileState.None, ProfileState.Loading -> AppState.Loading
                is ProfileState.Loaded -> if (profile.profile.isComplete) AppState.Ready else AppState.NeedsProfile
                is ProfileState.Failed -> AppState.ProfileUnavailable(profile.error)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AppState.Loading)

    fun retryProfile() {
        viewModelScope.launch { profileRepository.refresh() }
    }

    fun signOut() {
        viewModelScope.launch { authRepository.signOut() }
    }
}
