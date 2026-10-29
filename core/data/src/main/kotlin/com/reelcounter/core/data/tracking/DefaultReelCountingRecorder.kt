package com.reelcounter.core.data.tracking

import com.reelcounter.core.common.AppInitializer
import com.reelcounter.core.common.dispatchers.ApplicationScope
import com.reelcounter.core.datastore.TrackingPreferencesStore
import com.reelcounter.core.domain.tracking.CURRENT_DISCLOSURE_VERSION
import com.reelcounter.core.domain.tracking.DayBucketer
import com.reelcounter.core.domain.tracking.PlatformCatalog
import com.reelcounter.core.domain.tracking.ReelCountingRecorder
import com.reelcounter.core.domain.tracking.ReelCountingStatus
import com.reelcounter.core.domain.tracking.TimeSource
import com.reelcounter.core.domain.tracking.TrackingRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stores each reel the accessibility service counts and keeps the day's figures fresh: today's
 * rows are re-derived a moment after counting (quick swipes share one refresh), and the result is
 * uploaded at most once a minute so friends see it without a network call per swipe.
 */
@Singleton
internal class DefaultReelCountingRecorder @Inject constructor(
    private val store: TrackingLocalStore,
    private val preferences: TrackingPreferencesStore,
    private val time: TimeSource,
    private val ingestor: UsageIngestor,
    private val trackingRepository: TrackingRepository,
    private val status: ReelCountingStatus,
    @param:ApplicationScope private val scope: CoroutineScope,
) : ReelCountingRecorder, AppInitializer {

    private var localRefresh: Job? = null
    private var lastUploadMs = 0L

    /**
     * The service can stop without telling us (e.g. killed by the system). Whenever the setting
     * says counting is off, mark the run as stopped, so later use isn't treated as counted.
     */
    override fun initialize() {
        scope.launch {
            status.isEnabled.collect { enabled -> if (!enabled) onCountingStopped() }
        }
    }

    /**
     * Android sometimes unbinds and rebinds accessibility services (updates, memory pressure, other
     * tools). A short gap with none of the tracked apps used missed nothing, so the run continues;
     * if one was used during the gap, those reels weren't counted and the run restarts now.
     */
    override suspend fun onCountingStarted(atMs: Long) {
        val prefs = preferences.preferences.first()
        val since = prefs.countingSinceMs
        val stoppedAt = prefs.countingStoppedAtMs
        val continues = since != null && (stoppedAt == null || !trackedAppUsedBetween(stoppedAt, atMs))
        preferences.setCountingState(sinceMs = if (continues) since else atMs, stoppedAtMs = null)
    }

    override suspend fun onCountingStopped() {
        val prefs = preferences.preferences.first()
        if (prefs.countingSinceMs != null && prefs.countingStoppedAtMs == null) {
            preferences.setCountingState(sinceMs = prefs.countingSinceMs, stoppedAtMs = time.nowMs())
        }
    }

    private suspend fun trackedAppUsedBetween(fromMs: Long, toMs: Long): Boolean {
        ingestor.ingest() // bring the stored sessions up to date first
        return store.sessionsOverlapping(fromMs, toMs).any { PlatformCatalog.forPackage(it.packageName) != null }
    }

    override suspend fun onReelViewed(packageName: String, atMs: Long) {
        val platform = PlatformCatalog.forPackage(packageName) ?: return
        val prefs = preferences.preferences.first()
        // Same rules as app time: nothing is recorded before consent or while paused.
        if (prefs.paused || prefs.disclosureAcceptedVersion < CURRENT_DISCLOSURE_VERSION) return
        if (store.platformSettings().firstOrNull { it.platformId == platform.platform.id }?.isTracked == false) return

        store.addReelView(DayBucketer.dateOf(atMs, time.zone()), platform.platform.id)
        scheduleRefresh()
    }

    @Synchronized
    private fun scheduleRefresh() {
        if (localRefresh?.isActive == true) return
        localRefresh = scope.launch {
            delay(REFRESH_DELAY_MS)
            val now = time.nowMs()
            if (now - lastUploadMs >= UPLOAD_INTERVAL_MS) {
                lastUploadMs = now
                trackingRepository.refresh() // re-derives the rows and requests a sync
            } else {
                ingestor.ingest()
            }
        }
    }

    private companion object {
        const val REFRESH_DELAY_MS = 2_000L
        const val UPLOAD_INTERVAL_MS = 60_000L
    }
}
