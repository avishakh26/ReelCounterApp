package com.reelcounter.feature.tracking.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reelcounter.core.domain.tracking.PlatformConfiguration
import com.reelcounter.core.domain.tracking.PlatformSettingsRepository
import com.reelcounter.core.domain.tracking.ReelCountingStatus
import com.reelcounter.core.domain.tracking.TrackingRepository
import com.reelcounter.core.domain.tracking.TrackingStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TrackingSettingsUiState(
    val isLoading: Boolean = true,
    val status: TrackingStatus = TrackingStatus.NeedsDisclosure,
    val platforms: List<PlatformConfiguration> = emptyList(),
    /** Whether the reel-counting accessibility service is on (exact counts) or not (estimates). */
    val countingEnabled: Boolean = false,
)

@HiltViewModel
class TrackingSettingsViewModel @Inject constructor(
    private val trackingRepository: TrackingRepository,
    private val platformSettings: PlatformSettingsRepository,
    countingStatus: ReelCountingStatus,
) : ViewModel() {

    val uiState: StateFlow<TrackingSettingsUiState> =
        combine(trackingRepository.status, platformSettings.platforms, countingStatus.isEnabled) { status, platforms, counting ->
            TrackingSettingsUiState(isLoading = false, status = status, platforms = platforms, countingEnabled = counting)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TrackingSettingsUiState())

    fun setPaused(paused: Boolean) = launch { trackingRepository.setPaused(paused) }

    fun setTracked(platformId: String, tracked: Boolean) = launch { platformSettings.setTracked(platformId, tracked) }

    fun setEstimationEnabled(platformId: String, enabled: Boolean) = launch { platformSettings.setEstimationEnabled(platformId, enabled) }

    fun setShortFormShare(platformId: String, share: Double) = launch {
        val current = uiState.value.platforms.firstOrNull { it.platform.id == platformId } ?: return@launch
        platformSettings.setEstimationOverrides(platformId, share, current.secondsPerReelOverride)
    }

    fun setSecondsPerReel(platformId: String, seconds: Double) = launch {
        val current = uiState.value.platforms.firstOrNull { it.platform.id == platformId } ?: return@launch
        platformSettings.setEstimationOverrides(platformId, current.shortFormShareOverride, seconds)
    }

    fun resetEstimation(platformId: String) = launch { platformSettings.setEstimationOverrides(platformId, null, null) }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
