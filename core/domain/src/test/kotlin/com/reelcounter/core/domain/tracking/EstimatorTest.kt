package com.reelcounter.core.domain.tracking

import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EstimatorTest {

    private val profile = EstimationProfile(version = 1, shortFormShare = 0.5, shareLow = 0.25, shareHigh = 0.75, secondsPerReel = 20.0, secondsLow = 10.0, secondsHigh = 40.0)
    private val tenMinutes = 10 * 60_000L

    @Test
    fun `defaults give app time times share over seconds per reel`() {
        // 600 s × 0.5 / 20 s = 15; range 600×0.25/40 = 3 … 600×0.75/10 = 45
        assertEquals(ReelEstimate(count = 15, low = 3, high = 45), Estimator.estimate(tenMinutes, Estimator.parameters(profile)))
    }

    @Test
    fun `no app time means zero reels`() {
        assertEquals(ReelEstimate(0, 0, 0), Estimator.estimate(0, Estimator.parameters(profile)))
    }

    @Test
    fun `user overrides narrow the range around the user's own values`() {
        val parameters = Estimator.parameters(profile, shareOverride = 0.8, secondsOverride = 15.0)
        assertTrue(parameters.userAdjusted)
        // 600 × 0.8 / 15 = 32; range 600×0.64/18 = 21.3 → 21 … 600×0.96/12 = 48
        assertEquals(ReelEstimate(count = 32, low = 21, high = 48), Estimator.estimate(tenMinutes, parameters))
    }

    @Test
    fun `defaults alone are not user-adjusted`() {
        assertFalse(Estimator.parameters(profile).userAdjusted)
    }

    @Test
    fun `out-of-range overrides are clamped`() {
        val parameters = Estimator.parameters(profile, shareOverride = 3.0, secondsOverride = 0.0)
        assertEquals(1.0, parameters.shortFormShare)
        assertEquals(1.0, parameters.secondsPerReel)
        assertEquals(1.0, parameters.shareHigh)
    }

    @Test
    fun `the range always contains the estimate`() {
        listOf(0L, 1_000L, 59_999L, tenMinutes, 7 * 3_600_000L).forEach { ms ->
            PlatformCatalog.all.forEach { platform ->
                val e = Estimator.estimate(ms, Estimator.parameters(platform.profile))
                assertTrue(e.low <= e.count && e.count <= e.high, "$platform at $ms ms gave $e")
            }
        }
    }

    @Test
    fun `sessions shorter than the minimum are ignored when aggregating`() {
        val day = LocalDate.parse("2026-10-19")
        val start = day.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() + 3_600_000
        val sessions = listOf(
            UsageSession("com.instagram.android", start, start + 2_000), // accidental open
            UsageSession("com.instagram.android", start + 10_000, start + 70_000),
        )
        val totals = DailyAggregator.aggregate(sessions, ZoneTimeline(emptyList(), ZoneOffset.UTC), setOf(day))
        assertEquals(DayTotals(60_000, 1), totals[DayPlatformKey(day, "instagram")])
    }
}
