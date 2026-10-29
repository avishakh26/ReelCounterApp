package com.reelcounter.core.domain.tracking

import java.time.LocalDate

data class DayPlatformKey(val date: LocalDate, val platformId: String)

data class DayTotals(val foregroundMs: Long, val sessionCount: Int)

/**
 * Sums sessions into per-day, per-platform totals. Each session is dated in the zone in effect
 * when it started. Sessions shorter than the platform's minimum (accidental opens) are ignored.
 */
object DailyAggregator {

    fun aggregate(
        sessions: List<UsageSession>,
        zones: ZoneTimeline,
        dates: Set<LocalDate>,
    ): Map<DayPlatformKey, DayTotals> {
        val foreground = mutableMapOf<DayPlatformKey, Long>()
        val counts = mutableMapOf<DayPlatformKey, Int>()
        for (session in sessions) {
            val platform = PlatformCatalog.forPackage(session.packageName) ?: continue
            if (session.durationMs < platform.profile.minSessionSeconds * 1_000L) continue
            val zone = zones.zoneAt(session.startMs)
            for (segment in DayBucketer.split(session.startMs, session.endMs, zone)) {
                if (segment.date !in dates) continue
                val key = DayPlatformKey(segment.date, platform.platform.id)
                foreground[key] = (foreground[key] ?: 0L) + segment.durationMs
                counts[key] = (counts[key] ?: 0) + 1
            }
        }
        return foreground.mapValues { (key, ms) -> DayTotals(ms, counts.getValue(key)) }
    }
}
