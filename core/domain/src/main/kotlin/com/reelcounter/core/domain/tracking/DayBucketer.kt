package com.reelcounter.core.domain.tracking

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** When the device's time zone changed, so sessions are dated in the zone they happened in. */
data class ZoneChange(val atMs: Long, val zoneId: ZoneId)

/**
 * The zone in effect at any instant. Before the first recorded change, [initial] applies.
 * Zone changes are recorded as they happen (time-zone broadcasts and every ingest).
 */
class ZoneTimeline(changes: List<ZoneChange>, private val initial: ZoneId) {
    private val sorted = changes.sortedBy { it.atMs }

    fun zoneAt(epochMs: Long): ZoneId = sorted.lastOrNull { it.atMs <= epochMs }?.zoneId ?: sorted.firstOrNull()?.zoneId ?: initial
}

/** The part of a session that falls on one local calendar day. */
data class DaySegment(val date: LocalDate, val durationMs: Long)

/**
 * Splits time ranges at local midnight. Uses the zone's rules for each day, so a DST day is
 * 23 or 25 hours long and a session across midnight counts on both days.
 */
object DayBucketer {

    fun split(startMs: Long, endMs: Long, zone: ZoneId): List<DaySegment> {
        if (endMs <= startMs) return emptyList()
        val segments = mutableListOf<DaySegment>()
        var cursorMs = startMs
        while (cursorMs < endMs) {
            val date = Instant.ofEpochMilli(cursorMs).atZone(zone).toLocalDate()
            val nextMidnightMs = startOfDayMs(date.plusDays(1), zone)
            val segmentEnd = minOf(nextMidnightMs, endMs)
            segments += DaySegment(date, segmentEnd - cursorMs)
            cursorMs = segmentEnd
        }
        return segments
    }

    fun startOfDayMs(date: LocalDate, zone: ZoneId): Long = date.atStartOfDay(zone).toInstant().toEpochMilli()

    fun dateOf(epochMs: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(epochMs).atZone(zone).toLocalDate()
}
