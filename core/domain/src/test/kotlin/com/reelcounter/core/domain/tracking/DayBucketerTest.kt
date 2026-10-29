package com.reelcounter.core.domain.tracking

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals

class DayBucketerTest {

    private val berlin = ZoneId.of("Europe/Berlin")
    private val tokyo = ZoneId.of("Asia/Tokyo")
    private val hour = 3_600_000L

    private fun at(text: String, zone: ZoneId = berlin): Long =
        LocalDateTime.parse(text).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun `a session across midnight is split between the two days`() {
        val segments = DayBucketer.split(at("2026-10-19T23:30"), at("2026-10-20T00:45"), berlin)
        assertEquals(
            listOf(DaySegment(LocalDate.parse("2026-10-19"), 30 * 60_000L), DaySegment(LocalDate.parse("2026-10-20"), 45 * 60_000L)),
            segments,
        )
    }

    @Test
    fun `the autumn DST day is 25 hours long`() {
        // Clocks go back at 03:00 on 25 Oct 2026 in Berlin.
        val day = LocalDate.parse("2026-10-25")
        val start = DayBucketer.startOfDayMs(day, berlin)
        val end = DayBucketer.startOfDayMs(day.plusDays(1), berlin)
        assertEquals(25 * hour, end - start)
        assertEquals(listOf(DaySegment(day, 25 * hour)), DayBucketer.split(start, end, berlin))
    }

    @Test
    fun `the spring DST day is 23 hours long`() {
        val day = LocalDate.parse("2026-03-29")
        val length = DayBucketer.startOfDayMs(day.plusDays(1), berlin) - DayBucketer.startOfDayMs(day, berlin)
        assertEquals(23 * hour, length)
    }

    @Test
    fun `the same instant can fall on different dates in different zones`() {
        val instant = at("2026-10-19T20:00") // Berlin evening = Tokyo early morning next day
        assertEquals(LocalDate.parse("2026-10-19"), DayBucketer.dateOf(instant, berlin))
        assertEquals(LocalDate.parse("2026-10-20"), DayBucketer.dateOf(instant, tokyo))
    }

    @Test
    fun `the zone timeline applies each change from its time onward`() {
        val flight = at("2026-10-19T12:00")
        val zones = ZoneTimeline(listOf(ZoneChange(0, berlin), ZoneChange(flight, tokyo)), initial = berlin)
        assertEquals(berlin, zones.zoneAt(flight - 1))
        assertEquals(tokyo, zones.zoneAt(flight))
    }

    @Test
    fun `before the first recorded zone, the first recorded zone applies`() {
        val zones = ZoneTimeline(listOf(ZoneChange(1_000, tokyo)), initial = berlin)
        assertEquals(tokyo, zones.zoneAt(0))
        assertEquals(berlin, ZoneTimeline(emptyList(), initial = berlin).zoneAt(0))
    }

    @Test
    fun `travel dates sessions in the zone where they happened`() {
        val ig = "com.instagram.android"
        val flight = at("2026-10-19T12:00")
        val zones = ZoneTimeline(listOf(ZoneChange(0, berlin), ZoneChange(flight, tokyo)), initial = berlin)
        // 23:30 Tokyo on the 19th, after landing: still the 19th locally (it's 16:30 in Berlin).
        val tokyoEvening = at("2026-10-19T23:30", tokyo)
        val sessions = listOf(UsageSession(ig, tokyoEvening, tokyoEvening + 10 * 60_000L))

        val totals = DailyAggregator.aggregate(sessions, zones, setOf(LocalDate.parse("2026-10-19"), LocalDate.parse("2026-10-20")))

        assertEquals(mapOf(DayPlatformKey(LocalDate.parse("2026-10-19"), "instagram") to DayTotals(10 * 60_000L, 1)), totals)
    }
}
