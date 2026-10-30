package com.reelcounter.core.data.tracking

import com.reelcounter.core.datastore.TrackingPreferencesStore
import com.reelcounter.core.domain.tracking.CURRENT_DISCLOSURE_VERSION
import com.reelcounter.core.domain.tracking.DailyAggregator
import com.reelcounter.core.domain.tracking.DayBucketer
import com.reelcounter.core.domain.tracking.DayPlatformKey
import com.reelcounter.core.domain.tracking.DayTotals
import com.reelcounter.core.domain.tracking.InstalledApps
import com.reelcounter.core.domain.tracking.PlatformCatalog
import com.reelcounter.core.domain.tracking.RefreshResult
import com.reelcounter.core.domain.tracking.SessionBuilder
import com.reelcounter.core.domain.tracking.TimeSource
import com.reelcounter.core.domain.tracking.UsageAccessMonitor
import com.reelcounter.core.domain.tracking.UsageEventSource
import com.reelcounter.core.domain.tracking.UsageSession
import com.reelcounter.core.domain.tracking.ZoneChange
import com.reelcounter.core.domain.tracking.ZoneTimeline
import com.reelcounter.core.model.MetricQuality
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads the system usage log and turns it into stored sessions and daily rows
 * (docs/phase-1/01-system-architecture.md §2a).
 *
 * Every run is idempotent:
 * - It re-reads from a little before the last watermark, plus any still-open session.
 * - Sessions from the first event read onward are replaced by the freshly derived ones.
 * - Whole non-final days are recomputed from the stored sessions.
 * Running twice, or overlapping runs, therefore can't double-count.
 */
@Singleton
internal class UsageIngestor @Inject constructor(
    private val store: TrackingLocalStore,
    private val source: UsageEventSource,
    private val usageAccess: UsageAccessMonitor,
    private val preferences: TrackingPreferencesStore,
    private val time: TimeSource,
    private val installedApps: InstalledApps,
) {
    private val mutex = Mutex()

    suspend fun ingest(): RefreshResult = mutex.withLock {
        val prefs = preferences.preferences.first()
        if (prefs.disclosureAcceptedVersion < CURRENT_DISCLOSURE_VERSION || !usageAccess.isGrantedNow()) {
            return RefreshResult.NotPermitted
        }
        if (prefs.paused) return RefreshResult.Paused
        if (!source.isUserUnlocked()) return RefreshResult.DeviceLocked

        val now = time.nowMs()
        val zone = time.zone()
        // Always from the injected time source, never the system clock directly.
        val today = DayBucketer.dateOf(now, zone)
        recordZoneIfChanged(now, zone)

        val settings = store.platformSettings().associateBy { it.platformId }
        val tracked = PlatformCatalog.all.filter { settings[it.platform.id]?.isTracked ?: true }
        val trackedPackages = tracked.flatMap { it.packages }.toSet()

        val state = store.ingestState()
        var windowStart = state?.let { it.watermarkMs - OVERLAP_MS } ?: DayBucketer.startOfDayMs(today.minusDays(BACKFILL_DAYS), zone)
        store.earliestOpenSessionStart()?.let { windowStart = minOf(windowStart, it) }
        // Always look back at least the overlap from *now*. Normally a no-op; after the clock is
        // turned back, the old watermark is in the future and new events would otherwise be skipped.
        windowStart = minOf(windowStart, now - OVERLAP_MS)
        // Never read time from before the last resume: it was spent while tracking was paused.
        prefs.resumedAtMs?.let { windowStart = maxOf(windowStart, it) }
        windowStart = minOf(windowStart, now)

        val events = source.query(windowStart, now)
        val sessions = SessionBuilder.build(events, trackedPackages, windowEndMs = now)
        val firstEventMs = events.minOfOrNull { it.timestampMs }

        // If more time passed than the system keeps events for, the log starts later than
        // requested; only the part actually read counts as covered.
        val coverageStart = if (now - windowStart > RETENTION_ASSUMPTION_MS && firstEventMs != null) {
            maxOf(windowStart, firstEventMs)
        } else {
            windowStart
        }

        store.transaction {
            // One revision for everything this read writes; strictly greater than any before it,
            // even if the clock was turned back, so the server never mistakes it for stale data.
            val revision = store.nextRevision(now)

            // Replace only what this read can fully re-derive: sessions starting at or after the
            // first event returned. With no events at all, nothing is replaced.
            if (firstEventMs != null) {
                val replaceFrom = maxOf(windowStart, firstEventMs)
                store.replaceSessionsFrom(replaceFrom, sessions.filter { it.startMs >= replaceFrom })
            }

            val coveredDates = datesBetween(DayBucketer.dateOf(coverageStart, zone), today)
            val finalDates = store.dailyRows(coveredDates).filter { it.isFinal }.map { it.date }.toSet()
            val datesToCompute = coveredDates - finalDates
            if (datesToCompute.isNotEmpty()) {
                writeDailyRows(datesToCompute, tracked.map { it.platform.id }.toSet(), settings, zone, revision, prefs.activeCountingSinceMs, now)
            }
            updateCoverage(coveredDates - finalDates, coverageStart, now, zone)

            store.finalizeBefore(today.minusDays(FINAL_AFTER_DAYS), revision = revision)
            store.pruneSessionsEndedBefore(DayBucketer.startOfDayMs(today.minusDays(SESSION_RETENTION_DAYS), zone))
            store.saveIngestState(IngestState(watermarkMs = now, lastSuccessMs = now, lastRevision = revision))
        }
        RefreshResult.Updated
    }

    /** Re-applies estimation settings to recent days after the user changes them. */
    suspend fun recomputeEstimates() = mutex.withLock {
        store.transaction {
            val settings = store.platformSettings().associateBy { it.platformId }
            val revision = store.nextRevision(time.nowMs())
            val updated = store.nonFinalRows().mapNotNull { row ->
                val platform = PlatformCatalog.forId(row.platformId) ?: return@mapNotNull null
                // Counted days stay counted: estimation settings don't apply to them.
                val counted = row.estimate?.count?.takeIf { row.quality == MetricQuality.COUNTED }
                val recalculated = DailyRowCalculator.calculate(
                    row.date, platform, DayTotals(row.foregroundMs, row.sessionCount), settings[row.platformId], row.tzId, revision, counted,
                )
                recalculated.takeUnless { it.sameFiguresAs(row) }
            }
            store.saveDailyRows(updated)
        }
    }

    /** Ends provisional sessions where they are; called before pausing. */
    suspend fun closeOpenSessions() = mutex.withLock { store.closeOpenSessions() }

    suspend fun recordZoneIfChanged(now: Long, zone: ZoneId) {
        if (store.latestZone()?.zoneId != zone) store.recordZone(ZoneChange(now, zone))
    }

    private suspend fun writeDailyRows(
        dates: Set<LocalDate>,
        trackedIds: Set<String>,
        settings: Map<String, PlatformSettings>,
        zone: ZoneId,
        revision: Long,
        countingSinceMs: Long?,
        nowMs: Long,
    ) {
        // A day's sessions can start the evening before (other zones, midnight crossings).
        val rangeStart = DayBucketer.startOfDayMs(dates.min().minusDays(1), zone)
        val rangeEnd = DayBucketer.startOfDayMs(dates.max().plusDays(2), zone)
        val sessions = store.sessionsOverlapping(rangeStart, rangeEnd)
        val totals = DailyAggregator.aggregate(sessions, ZoneTimeline(store.zoneChanges(), zone), dates)
        val counts = store.reelCounts(dates)
        val firstUse = firstUseByDay(sessions, dates, zone)

        // A platform-day is counted only if the counter was already running when that day's use of
        // the app began (or, with no use, by the end of the day so far). Otherwise part of the day
        // was never counted, and the estimate from app time is the honest figure.
        fun countedFor(date: LocalDate, platformId: String): Int? {
            val since = countingSinceMs ?: return null
            val reference = firstUse[DayPlatformKey(date, platformId)]
                ?: minOf(DayBucketer.startOfDayMs(date.plusDays(1), zone), nowMs)
            return if (since <= reference) counts[date to platformId] ?: 0 else null
        }

        val existing = store.dailyRows(dates).associateBy { it.date to it.platformId }
        val installed = trackedIds.filter { id -> PlatformCatalog.forId(id)?.let { installedApps.isAnyInstalled(it.packages) } == true }.toSet()
        val rows = dates.flatMap { date ->
            trackedIds.mapNotNull { platformId ->
                val platform = PlatformCatalog.forId(platformId) ?: return@mapNotNull null
                val dayTotals = totals[DayPlatformKey(date, platformId)]
                // An app that isn't on the phone wasn't used: no row ("No data") rather than a 0 that
                // reads like a measurement. Time recorded before it was uninstalled is still written.
                if (dayTotals == null && platformId !in installed) return@mapNotNull null
                val row = DailyRowCalculator.calculate(
                    date = date,
                    platform = platform,
                    totals = dayTotals ?: DayTotals(0, 0),
                    settings = settings[platformId],
                    tzId = zone.id,
                    revision = revision,
                    counted = countedFor(date, platformId),
                )
                // Unchanged rows aren't rewritten, so they don't need re-uploading (Phase 5).
                row.takeUnless { existing[date to platformId]?.sameFiguresAs(it) == true }
            }
        }
        store.saveDailyRows(rows)
    }

    /** When each platform was first in the foreground on each day (sessions clipped to the day). */
    private fun firstUseByDay(sessions: List<UsageSession>, dates: Set<LocalDate>, zone: ZoneId): Map<DayPlatformKey, Long> {
        val first = mutableMapOf<DayPlatformKey, Long>()
        for (session in sessions) {
            val platformId = PlatformCatalog.forPackage(session.packageName)?.platform?.id ?: continue
            for (date in dates) {
                val dayStart = DayBucketer.startOfDayMs(date, zone)
                val dayEnd = DayBucketer.startOfDayMs(date.plusDays(1), zone)
                if (session.endMs <= dayStart || session.startMs >= dayEnd) continue
                val key = DayPlatformKey(date, platformId)
                val start = maxOf(session.startMs, dayStart)
                first[key] = minOf(first[key] ?: start, start)
            }
        }
        return first
    }

    private suspend fun updateCoverage(dates: Set<LocalDate>, coverageStart: Long, now: Long, zone: ZoneId) {
        val existing = store.coverage(dates).associateBy { it.date }
        store.saveCoverage(
            dates.map { date ->
                val from = maxOf(DayBucketer.startOfDayMs(date, zone), coverageStart)
                val to = minOf(DayBucketer.startOfDayMs(date.plusDays(1), zone), now)
                val previous = existing[date]
                DayCoverage(date, minOf(from, previous?.fromMs ?: from), maxOf(to, previous?.toMs ?: to))
            },
        )
    }

    private fun datesBetween(first: LocalDate, last: LocalDate): Set<LocalDate> =
        generateSequence(first) { it.plusDays(1) }.takeWhile { !it.isAfter(last) }.toSet()

    internal companion object {
        /** Re-read a little before the last read, in case events were logged late. */
        const val OVERLAP_MS = 10 * 60_000L

        /** On the very first read, also fill in yesterday from the system log. */
        // Android keeps roughly the last week of usage events; older days come back empty.
        const val BACKFILL_DAYS = 6L

        /** Assumed minimum time the system keeps raw events (unverified; spike S1 measures it). */
        const val RETENTION_ASSUMPTION_MS = 3 * 24 * 3_600_000L

        /** Days before today − 2 are frozen (docs/phase-1/04-technical-limitations.md L2). */
        const val FINAL_AFTER_DAYS = 2L

        const val SESSION_RETENTION_DAYS = 35L
    }
}
