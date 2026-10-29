package com.reelcounter.core.data.tracking

import com.reelcounter.core.domain.sync.SyncScheduler
import com.reelcounter.core.domain.tracking.InstalledApps
import com.reelcounter.core.domain.tracking.PlatformCatalog
import com.reelcounter.core.domain.tracking.PlatformConfiguration
import com.reelcounter.core.domain.tracking.PlatformSettingsRepository
import com.reelcounter.core.domain.tracking.TimeSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class DefaultPlatformSettingsRepository @Inject constructor(
    private val store: TrackingLocalStore,
    private val ingestor: UsageIngestor,
    private val installedApps: InstalledApps,
    private val time: TimeSource,
    private val syncScheduler: SyncScheduler,
) : PlatformSettingsRepository {

    override val platforms: Flow<List<PlatformConfiguration>> = store.observePlatformSettings().map { rows ->
        val byId = rows.associateBy { it.platformId }
        PlatformCatalog.all.map { tracked ->
            val settings = byId[tracked.platform.id]
            PlatformConfiguration(
                platform = tracked.platform,
                isInstalled = installedApps.isAnyInstalled(tracked.packages),
                isTracked = settings?.isTracked ?: true,
                estimationEnabled = settings?.estimationEnabled ?: tracked.estimateByDefault,
                defaults = tracked.profile,
                shortFormShareOverride = settings?.shortFormShare,
                secondsPerReelOverride = settings?.secondsPerReel,
            )
        }
    }

    // Stopping tracking keeps the history already recorded; it only stops new collection.
    override suspend fun setTracked(platformId: String, tracked: Boolean) = update(platformId) { it.copy(isTracked = tracked) }

    override suspend fun setEstimationEnabled(platformId: String, enabled: Boolean) {
        update(platformId) { it.copy(estimationEnabled = enabled) }
        recomputeAndSync()
    }

    override suspend fun setEstimationOverrides(platformId: String, shortFormShare: Double?, secondsPerReel: Double?) {
        update(platformId) { it.copy(shortFormShare = shortFormShare, secondsPerReel = secondsPerReel) }
        recomputeAndSync()
    }

    private suspend fun recomputeAndSync() {
        ingestor.recomputeEstimates()
        syncScheduler.requestSync()
    }

    private suspend fun update(platformId: String, change: (PlatformSettings) -> PlatformSettings) {
        require(PlatformCatalog.forId(platformId) != null) { "Unknown platform $platformId" }
        val current = store.platformSettings().firstOrNull { it.platformId == platformId }
            ?: PlatformSettings(platformId, isTracked = true, estimationEnabled = null, shortFormShare = null, secondsPerReel = null)
        store.savePlatformSettings(change(current), time.nowMs())
    }
}
