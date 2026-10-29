package com.reelcounter.core.testing

import com.reelcounter.core.domain.tracking.CurrentDateProvider
import com.reelcounter.core.domain.tracking.PlatformCatalog
import com.reelcounter.core.domain.tracking.PlatformConfiguration
import com.reelcounter.core.domain.tracking.PlatformSettingsRepository
import com.reelcounter.core.domain.tracking.ReelCountingStatus
import com.reelcounter.core.domain.tracking.RefreshResult
import com.reelcounter.core.domain.tracking.TrackingRepository
import com.reelcounter.core.domain.tracking.TrackingStatus
import com.reelcounter.core.model.DailyPlatformActivity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.time.LocalDate

class FakeTrackingRepository(initialStatus: TrackingStatus = TrackingStatus.NeedsDisclosure) : TrackingRepository {
    val statusFlow = MutableStateFlow(initialStatus)
    override val status: Flow<TrackingStatus> = statusFlow

    val lastUpdated = MutableStateFlow<Long?>(null)
    override val lastUpdatedMs: Flow<Long?> = lastUpdated

    val days = MutableStateFlow<Map<LocalDate, List<DailyPlatformActivity>>>(emptyMap())
    var refreshCount = 0
    var acceptedDisclosureVersion: Int? = null
    var paused: Boolean? = null

    override fun observeDay(date: LocalDate): Flow<List<DailyPlatformActivity>> = days.map { it[date].orEmpty() }

    override suspend fun refresh(): RefreshResult {
        refreshCount++
        return RefreshResult.Updated
    }

    /** NeedsUsageAccess for a first-time user; Active for someone who already granted it. */
    var statusAfterDisclosure: TrackingStatus = TrackingStatus.NeedsUsageAccess

    override suspend fun acceptDisclosure(version: Int) {
        acceptedDisclosureVersion = version
        statusFlow.value = statusAfterDisclosure
    }

    override suspend fun setPaused(paused: Boolean) {
        this.paused = paused
    }

    override suspend fun onTimeChanged() = Unit
}

class FakePlatformSettingsRepository(installed: Set<String> = PlatformCatalog.all.map { it.platform.id }.toSet()) : PlatformSettingsRepository {
    private val configs = MutableStateFlow(
        PlatformCatalog.all.map {
            PlatformConfiguration(
                platform = it.platform,
                isInstalled = it.platform.id in installed,
                isTracked = true,
                estimationEnabled = it.estimateByDefault,
                defaults = it.profile,
                shortFormShareOverride = null,
                secondsPerReelOverride = null,
            )
        },
    )
    override val platforms: Flow<List<PlatformConfiguration>> = configs

    fun current(platformId: String) = configs.value.first { it.platform.id == platformId }

    override suspend fun setTracked(platformId: String, tracked: Boolean) = edit(platformId) { it.copy(isTracked = tracked) }

    override suspend fun setEstimationEnabled(platformId: String, enabled: Boolean) = edit(platformId) { it.copy(estimationEnabled = enabled) }

    override suspend fun setEstimationOverrides(platformId: String, shortFormShare: Double?, secondsPerReel: Double?) =
        edit(platformId) { it.copy(shortFormShareOverride = shortFormShare, secondsPerReelOverride = secondsPerReel) }

    private fun edit(platformId: String, change: (PlatformConfiguration) -> PlatformConfiguration) =
        configs.update { list -> list.map { if (it.platform.id == platformId) change(it) else it } }
}

class FakeCurrentDateProvider(initial: LocalDate) : CurrentDateProvider {
    val date = MutableStateFlow(initial)
    override val today: Flow<LocalDate> = date
}

class FakeReelCountingStatus(enabled: Boolean = false, override val isAvailable: Boolean = true) : ReelCountingStatus {
    val enabled = MutableStateFlow(enabled)
    override val isEnabled: Flow<Boolean> = this.enabled
    override fun isEnabledNow(): Boolean = enabled.value
}
