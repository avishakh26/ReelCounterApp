package com.reelcounter.feature.tracking.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reelcounter.core.domain.tracking.CURRENT_DISCLOSURE_VERSION
import com.reelcounter.core.domain.tracking.PlatformSettingsRepository
import com.reelcounter.core.domain.tracking.ReelCountingStatus
import com.reelcounter.core.domain.tracking.TrackingRepository
import com.reelcounter.core.domain.tracking.TrackingStatus
import com.reelcounter.core.model.Platform
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SetupStep { Disclosure, ChooseApps, GrantAccess, CountReels, Done }

data class PlatformChoice(val platform: Platform, val isInstalled: Boolean, val selected: Boolean)

data class TrackingSetupUiState(
    /** null while the starting step is worked out. */
    val step: SetupStep? = null,
    val choices: List<PlatformChoice> = emptyList(),
    val isWorking: Boolean = false,
    /** This build includes reel counting; without it the CountReels step is skipped. */
    val countingAvailable: Boolean = true,
) {
    val canContinueFromApps: Boolean get() = choices.any { it.selected } && !isWorking
}

/**
 * First-time tracking setup: the prominent disclosure (Play User Data policy: shown before any
 * data access and accepted with an explicit tap), choosing apps, granting usage access in system
 * settings, then (optional) turning on reel counting in accessibility settings. Resumes at
 * whichever step is still missing.
 */
@HiltViewModel
class TrackingSetupViewModel @Inject constructor(
    private val trackingRepository: TrackingRepository,
    private val platformSettings: PlatformSettingsRepository,
    private val countingStatus: ReelCountingStatus,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TrackingSetupUiState(countingAvailable = countingStatus.isAvailable))
    val uiState: StateFlow<TrackingSetupUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val platforms = platformSettings.platforms.first()
            val choices = platforms.map {
                // Pre-select installed apps; an app that isn't installed has nothing to measure yet.
                PlatformChoice(it.platform, it.isInstalled, selected = it.isTracked && it.isInstalled)
            }
            val step = when (trackingRepository.status.first()) {
                TrackingStatus.NeedsDisclosure -> SetupStep.Disclosure
                TrackingStatus.NeedsUsageAccess -> SetupStep.GrantAccess
                TrackingStatus.Paused, TrackingStatus.Active -> SetupStep.Done
            }
            _uiState.update { it.copy(step = step, choices = choices) }
        }
        // Usage access is granted in system settings; continue as soon as it's on.
        viewModelScope.launch {
            trackingRepository.status.collect { status ->
                if (status == TrackingStatus.Active && _uiState.value.step == SetupStep.GrantAccess) finish()
            }
        }
        // Reel counting is turned on in accessibility settings; continue as soon as it's on.
        viewModelScope.launch {
            countingStatus.isEnabled.collect { enabled ->
                if (enabled && _uiState.value.step == SetupStep.CountReels) _uiState.update { it.copy(step = SetupStep.Done) }
            }
        }
    }

    /** Keeps estimates from app time instead of counting reels; can be turned on later in settings. */
    fun skipCounting() = _uiState.update { it.copy(step = SetupStep.Done) }

    fun acceptDisclosure() {
        _uiState.update { it.copy(isWorking = true) }
        viewModelScope.launch {
            trackingRepository.acceptDisclosure(CURRENT_DISCLOSURE_VERSION)
            // Someone re-accepting an updated disclosure usually still has usage access; their app
            // choices are kept, so there's nothing left to set up.
            if (trackingRepository.status.first() == TrackingStatus.Active) {
                finish()
            } else {
                _uiState.update { it.copy(isWorking = false, step = SetupStep.ChooseApps) }
            }
        }
    }

    fun toggle(platformId: String) = _uiState.update { state ->
        state.copy(choices = state.choices.map { if (it.platform.id == platformId) it.copy(selected = !it.selected) else it })
    }

    fun confirmApps() {
        val state = _uiState.value
        if (!state.canContinueFromApps) return
        _uiState.update { it.copy(isWorking = true) }
        viewModelScope.launch {
            state.choices.forEach { platformSettings.setTracked(it.platform.id, it.selected) }
            if (trackingRepository.status.first() == TrackingStatus.Active) {
                finish()
            } else {
                _uiState.update { it.copy(isWorking = false, step = SetupStep.GrantAccess) }
            }
        }
    }

    private fun finish() {
        _uiState.update { it.copy(isWorking = true) }
        viewModelScope.launch {
            // First read straight away, so Today has numbers when the user lands there.
            trackingRepository.refresh()
            val next = if (!countingStatus.isAvailable || countingStatus.isEnabledNow()) SetupStep.Done else SetupStep.CountReels
            _uiState.update { it.copy(isWorking = false, step = next) }
        }
    }
}
