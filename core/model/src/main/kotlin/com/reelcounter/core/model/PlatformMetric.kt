package com.reelcounter.core.model

import java.time.LocalDate
import kotlin.time.Duration

/**
 * One platform's figure for one local day. Each [MetricQuality] has its own shape, so a count can't
 * exist without its category: an estimate always carries its range, and a duration-only day has
 * no count at all.
 */
sealed interface PlatformMetric {
    val quality: MetricQuality

    /**
     * Videos started, from the platform's own data export, complete through [verifiedThrough].
     * [appTime] is present when this device also measured the app.
     */
    data class Verified(
        val count: Int,
        val verifiedThrough: LocalDate,
        val appTime: Duration? = null,
    ) : PlatformMetric {
        init {
            require(count >= 0) { "Verified count must not be negative: $count" }
            require(appTime == null || !appTime.isNegative()) { "App time must not be negative: $appTime" }
        }

        override val quality: MetricQuality get() = MetricQuality.VERIFIED
    }

    /**
     * Videos counted one at a time as each new one filled the screen in the app's short-video
     * feed, with the app's measured [appTime].
     */
    data class Counted(
        val count: Int,
        val appTime: Duration,
    ) : PlatformMetric {
        init {
            require(count >= 0) { "Counted reels must not be negative: $count" }
            require(!appTime.isNegative()) { "App time must not be negative: $appTime" }
        }

        override val quality: MetricQuality get() = MetricQuality.COUNTED
    }

    /** Measured [appTime] turned into a reel estimate with its plausible range [low]..[high]. */
    data class Estimated(
        val appTime: Duration,
        val count: Int,
        val low: Int,
        val high: Int,
        val userAdjusted: Boolean = false,
    ) : PlatformMetric {
        init {
            require(!appTime.isNegative()) { "App time must not be negative: $appTime" }
            require(low >= 0 && count in low..high) { "Estimate $count must lie within [$low, $high]" }
        }

        override val quality: MetricQuality get() = MetricQuality.ESTIMATED
    }

    /** Measured app time with no reel count, because no trustworthy model applies. */
    data class DurationOnly(val appTime: Duration) : PlatformMetric {
        init {
            require(!appTime.isNegative()) { "App time must not be negative: $appTime" }
        }

        override val quality: MetricQuality get() = MetricQuality.DURATION_ONLY
    }

    /** Nothing was measured; [reason] says why. */
    data class Unavailable(val reason: UnavailableReason) : PlatformMetric {
        override val quality: MetricQuality get() = MetricQuality.UNAVAILABLE
    }
}

enum class UnavailableReason {
    /** Usage access hasn't been granted, or was revoked. */
    PERMISSION_MISSING,

    /** The user paused tracking. */
    TRACKING_PAUSED,

    /** The platform's app isn't installed on this device. */
    APP_NOT_INSTALLED,

    /** No ingest run covered this period before the system discarded its usage events. */
    COVERAGE_GAP,

    /** Use the device can't see: web browsers, other devices, work profile or Private Space. */
    NOT_OBSERVABLE,
}

/** What the app shows for one platform on one local [date]. */
data class DailyPlatformActivity(
    val platform: Platform,
    val date: LocalDate,
    val metric: PlatformMetric,
)
