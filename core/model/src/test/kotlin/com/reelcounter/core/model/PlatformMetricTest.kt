package com.reelcounter.core.model

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.minutes

class PlatformMetricTest {

    @Test
    fun `each metric reports its own quality`() {
        assertEquals(
            MetricQuality.VERIFIED,
            PlatformMetric.Verified(count = 3, verifiedThrough = LocalDate.of(2026, 10, 18)).quality,
        )
        assertEquals(
            MetricQuality.ESTIMATED,
            PlatformMetric.Estimated(appTime = 10.minutes, count = 30, low = 20, high = 40).quality,
        )
        assertEquals(MetricQuality.DURATION_ONLY, PlatformMetric.DurationOnly(10.minutes).quality)
        assertEquals(
            MetricQuality.UNAVAILABLE,
            PlatformMetric.Unavailable(UnavailableReason.TRACKING_PAUSED).quality,
        )
    }

    @Test
    fun `estimate outside its range is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            PlatformMetric.Estimated(appTime = 10.minutes, count = 50, low = 20, high = 40)
        }
        assertFailsWith<IllegalArgumentException> {
            PlatformMetric.Estimated(appTime = 10.minutes, count = 0, low = -1, high = 4)
        }
    }

    @Test
    fun `negative values are rejected`() {
        assertFailsWith<IllegalArgumentException> { PlatformMetric.DurationOnly((-1).minutes) }
        assertFailsWith<IllegalArgumentException> {
            PlatformMetric.Verified(count = -1, verifiedThrough = LocalDate.of(2026, 10, 18))
        }
    }

    @Test
    fun `declaration order is precedence, strongest first`() {
        assertEquals(
            MetricQuality.ESTIMATED,
            listOf(MetricQuality.DURATION_ONLY, MetricQuality.ESTIMATED, MetricQuality.UNAVAILABLE).min(),
        )
    }
}
