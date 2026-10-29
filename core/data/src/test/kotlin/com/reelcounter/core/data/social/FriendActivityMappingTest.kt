package com.reelcounter.core.data.social

import com.reelcounter.core.model.PlatformMetric
import com.reelcounter.core.network.social.NetworkDailyTotal
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.minutes

class FriendActivityMappingTest {

    private fun row(
        quality: String,
        ms: Long? = 30 * 60_000,
        reels: Int? = null,
        low: Int? = null,
        high: Int? = null,
        verified: Int? = null,
    ) = NetworkDailyTotal(
        userId = "u1",
        platformId = "tiktok",
        activityDate = "2026-10-28",
        foregroundMs = ms,
        estimatedReels = reels,
        estimatedReelsLow = low,
        estimatedReelsHigh = high,
        verifiedReels = verified,
        bestQuality = quality,
    )

    @Test
    fun `an estimate keeps its count, range and app time`() {
        val activity = row("ESTIMATED", reels = 80, low = 60, high = 100).toActivity()
        val metric = assertIs<PlatformMetric.Estimated>(activity.metric)
        assertEquals(80, metric.count)
        assertEquals(60, metric.low)
        assertEquals(100, metric.high)
        assertEquals(30.minutes, metric.appTime)
        assertEquals(LocalDate.parse("2026-10-28"), activity.date)
        assertEquals("TikTok", activity.platform.displayName)
    }

    @Test
    fun `app time without a count stays app time only, never a zero count`() {
        val metric = row("DURATION_ONLY").toActivity().metric
        assertEquals(30.minutes, assertIs<PlatformMetric.DurationOnly>(metric).appTime)
    }

    @Test
    fun `a verified count wins over an estimate from another device`() {
        val metric = row("VERIFIED", reels = 50, low = 40, high = 60, verified = 72).toActivity().metric
        assertEquals(72, assertIs<PlatformMetric.Verified>(metric).count)
    }

    @Test
    fun `a day with nothing measured is unavailable`() {
        assertIs<PlatformMetric.Unavailable>(row("UNAVAILABLE", ms = null).toActivity().metric)
    }
}
