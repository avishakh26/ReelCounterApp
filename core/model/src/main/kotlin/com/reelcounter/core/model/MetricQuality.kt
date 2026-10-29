package com.reelcounter.core.model

/**
 * How trustworthy a reel figure is (see docs/phase-1/02-platform-feasibility.md §3).
 *
 * Declaration order is precedence, strongest first, and matches the `metric_quality` enum in
 * Postgres, so `minOf` picks the best quality across devices.
 */
enum class MetricQuality {
    /** A complete count from an approved platform API. Only the server can produce it. */
    VERIFIED,

    /**
     * Videos counted one by one on this device as they filled the screen (ReelCounter's
     * accessibility service), for a day the counter covered completely.
     */
    COUNTED,

    /** Measured app time × a versioned estimation model, possibly adjusted by the user. */
    ESTIMATED,

    /** Measured app time only; no count is shown. */
    DURATION_ONLY,

    /** No usable data for the day. */
    UNAVAILABLE,
}
