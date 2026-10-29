package com.reelcounter.core.data.tracking

import com.reelcounter.core.datastore.TrackingPreferencesStore
import com.reelcounter.core.domain.sync.SyncScheduler
import com.reelcounter.core.domain.tracking.CURRENT_DISCLOSURE_VERSION
import com.reelcounter.core.domain.tracking.InstalledApps
import com.reelcounter.core.domain.tracking.PlatformCatalog
import com.reelcounter.core.domain.tracking.RefreshResult
import com.reelcounter.core.domain.tracking.TimeSource
import com.reelcounter.core.domain.tracking.TrackingRepository
import com.reelcounter.core.domain.tracking.TrackingStatus
import com.reelcounter.core.domain.tracking.UsageAccessMonitor
import com.reelcounter.core.model.DailyPlatformActivity
import com.reelcounter.core.model.MetricQuality
import com.reelcounter.core.model.PlatformMetric
import com.reelcounter.core.model.UnavailableReason
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.milliseconds

@Singleton
internal class DefaultTrackingRepository @Inject constructor(
    private val ingestor: UsageIngestor,
    private val store: TrackingLocalStore,
    private val preferences: TrackingPreferencesStore,
    usageAccess: UsageAccessMonitor,
    private val installedApps: InstalledApps,
    private val time: TimeSource,
    private val syncScheduler: SyncScheduler,
) : TrackingRepository {

    override val status: Flow<TrackingStatus> = combine(preferences.preferences, usageAccess.isGranted) { prefs, granted ->
        when {
            prefs.disclosureAcceptedVersion < CURRENT_DISCLOSURE_VERSION -> TrackingStatus.NeedsDisclosure
            !granted -> TrackingStatus.NeedsUsageAccess
            prefs.paused -> TrackingStatus.Paused
            else -> TrackingStatus.Active
        }
    }.distinctUntilChanged()

    // 0 means the state row exists but no read has completed yet.
    override val lastUpdatedMs: Flow<Long?> = store.observeLastSuccess().map { it?.takeIf { ms -> ms > 0 } }

    override fun observeDay(date: LocalDate): Flow<List<DailyPlatformActivity>> = combine(
        store.observeDay(date),
        store.observePlatformSettings(),
        status,
    ) { rows, settings, status ->
        val rowsByPlatform = rows.associateBy { it.platformId }
        val settingsById = settings.associateBy { it.platformId }
        PlatformCatalog.all
            .filter { settingsById[it.platform.id]?.isTracked ?: true }
            .map { tracked ->
                val row = rowsByPlatform[tracked.platform.id]
                // Recorded data always wins; "not tracked" reasons apply only where nothing was recorded.
                val metric = row?.toMetric() ?: PlatformMetric.Unavailable(
                    when {
                        status == TrackingStatus.NeedsDisclosure || status == TrackingStatus.NeedsUsageAccess ->
                            UnavailableReason.PERMISSION_MISSING
                        status == TrackingStatus.Paused -> UnavailableReason.TRACKING_PAUSED
                        !installedApps.isAnyInstalled(tracked.packages) -> UnavailableReason.APP_NOT_INSTALLED
                        // Tracking was active but this day was never read (e.g. the log expired).
                        else -> UnavailableReason.COVERAGE_GAP
                    },
                )
                DailyPlatformActivity(tracked.platform, date, metric)
            }
    }

    override suspend fun refresh(): RefreshResult = ingestor.ingest().also { result ->
        // Upload whatever changed. The sync itself checks sign-in and consent, and waits for a network.
        if (result == RefreshResult.Updated) syncScheduler.requestSync()
    }

    override suspend fun acceptDisclosure(version: Int) = preferences.acceptDisclosure(version, time.nowMs())

    override suspend fun setPaused(paused: Boolean) {
        if (paused) {
            // Record everything up to this moment, then stop extending open sessions.
            ingestor.ingest()
            ingestor.closeOpenSessions()
            preferences.setPaused(true, time.nowMs())
        } else {
            preferences.setPaused(false, time.nowMs())
            ingestor.ingest()
        }
    }

    override suspend fun onTimeChanged() {
        ingestor.recordZoneIfChanged(time.nowMs(), time.zone())
        ingestor.ingest()
    }
}

private fun DailyRow.toMetric(): PlatformMetric {
    val appTime = foregroundMs.milliseconds
    val estimate = estimate
    return when {
        quality == MetricQuality.COUNTED && estimate != null -> PlatformMetric.Counted(count = estimate.count, appTime = appTime)
        quality == MetricQuality.ESTIMATED && estimate != null -> PlatformMetric.Estimated(
            appTime = appTime,
            count = estimate.count,
            low = estimate.low,
            high = estimate.high,
            userAdjusted = userAdjusted,
        )
        else -> PlatformMetric.DurationOnly(appTime)
    }
}
