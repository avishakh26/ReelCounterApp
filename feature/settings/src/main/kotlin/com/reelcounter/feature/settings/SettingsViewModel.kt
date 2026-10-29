package com.reelcounter.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reelcounter.core.domain.auth.AuthRepository
import com.reelcounter.core.domain.profile.ProfileRepository
import com.reelcounter.core.domain.profile.ProfileState
import com.reelcounter.core.model.UserProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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

    /** The app returns to sign-in on its own once the session is gone. */
    fun signOut() {
        viewModelScope.launch { authRepository.signOut() }
    }
}
