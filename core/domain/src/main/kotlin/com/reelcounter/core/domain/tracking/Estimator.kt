package com.reelcounter.core.domain.tracking

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Default assumptions for one platform: how much of its app time is short-form video, and how
 * long one video takes on average, each with a plausible range.
 *
 * IMPORTANT: the v1 numbers in [PlatformCatalog] are unvalidated placeholders. Calibration spike S2
 * (docs/phase-1/05-roadmap-and-risks.md) replaces them with measured values and bumps [version].
 */
data class EstimationProfile(
    val version: Int,
    val shortFormShare: Double,
    val shareLow: Double,
    val shareHigh: Double,
    val secondsPerReel: Double,
    val secondsLow: Double,
    val secondsHigh: Double,
    val minSessionSeconds: Int = 3,
) {
    init {
        require(shareLow in 0.0..shortFormShare && shortFormShare <= shareHigh && shareHigh <= 1.0)
        require(secondsLow > 0 && secondsLow <= secondsPerReel && secondsPerReel <= secondsHigh)
    }
}

/** The values one estimate is computed from: the profile, with the user's overrides applied. */
data class EstimationParameters(
    val shortFormShare: Double,
    val shareLow: Double,
    val shareHigh: Double,
    val secondsPerReel: Double,
    val secondsLow: Double,
    val secondsHigh: Double,
    val version: Int,
    val userAdjusted: Boolean,
)

data class ReelEstimate(val count: Int, val low: Int, val high: Int)

object Estimator {
    /** A user's own value is trusted more than the default, so its range is narrower: ±20 %. */
    private const val OVERRIDE_BAND = 0.2

    fun parameters(profile: EstimationProfile, shareOverride: Double? = null, secondsOverride: Double? = null): EstimationParameters {
        val share = shareOverride?.coerceIn(0.01, 1.0)
        val seconds = secondsOverride?.coerceAtLeast(1.0)
        return EstimationParameters(
            shortFormShare = share ?: profile.shortFormShare,
            shareLow = share?.let { it * (1 - OVERRIDE_BAND) } ?: profile.shareLow,
            shareHigh = share?.let { minOf(1.0, it * (1 + OVERRIDE_BAND)) } ?: profile.shareHigh,
            secondsPerReel = seconds ?: profile.secondsPerReel,
            secondsLow = seconds?.let { it * (1 - OVERRIDE_BAND) } ?: profile.secondsLow,
            secondsHigh = seconds?.let { it * (1 + OVERRIDE_BAND) } ?: profile.secondsHigh,
            version = profile.version,
            userAdjusted = share != null || seconds != null,
        )
    }

    /**
     * reels ≈ app time × short-form share ÷ seconds per reel. The range combines the low share
     * with the long duration and vice versa, and always contains the point estimate.
     */
    fun estimate(appTimeMs: Long, parameters: EstimationParameters): ReelEstimate {
        val seconds = appTimeMs.coerceAtLeast(0) / 1_000.0
        val count = (seconds * parameters.shortFormShare / parameters.secondsPerReel).roundToInt()
        val low = floor(seconds * parameters.shareLow / parameters.secondsHigh).toInt()
        val high = ceil(seconds * parameters.shareHigh / parameters.secondsLow).toInt()
        return ReelEstimate(count = count, low = minOf(low, count), high = maxOf(high, count))
    }
}
