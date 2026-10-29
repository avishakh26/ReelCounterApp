package com.reelcounter.core.data.tracking

import com.reelcounter.core.domain.tracking.DayTotals
import com.reelcounter.core.domain.tracking.Estimator
import com.reelcounter.core.domain.tracking.ReelEstimate
import com.reelcounter.core.domain.tracking.TrackedPlatform
import com.reelcounter.core.model.MetricQuality
import java.time.LocalDate

/**
 * Turns a platform-day's measured totals into its figure: the reels counted one by one when the
 * counter covered the whole day's use ([counted] not null), otherwise the estimate from app time
 * per the platform's estimation settings (decision D1 for the defaults).
 */
internal object DailyRowCalculator {

    fun calculate(
        date: LocalDate,
        platform: TrackedPlatform,
        totals: DayTotals,
        settings: PlatformSettings?,
        tzId: String,
        revision: Long,
        counted: Int? = null,
    ): DailyRow {
        if (counted != null) {
            return DailyRow(
                date = date,
                platformId = platform.platform.id,
                foregroundMs = totals.foregroundMs,
                sessionCount = totals.sessionCount,
                quality = MetricQuality.COUNTED,
                estimate = ReelEstimate(count = counted, low = counted, high = counted),
                estimationVersion = null,
                userAdjusted = false,
                tzId = tzId,
                isFinal = false,
                revision = revision,
                dirty = true,
            )
        }
        val estimationEnabled = settings?.estimationEnabled ?: platform.estimateByDefault
        if (!estimationEnabled) {
            return DailyRow(
                date = date,
                platformId = platform.platform.id,
                foregroundMs = totals.foregroundMs,
                sessionCount = totals.sessionCount,
                quality = MetricQuality.DURATION_ONLY,
                estimate = null,
                estimationVersion = null,
                userAdjusted = false,
                tzId = tzId,
                isFinal = false,
                revision = revision,
                dirty = true,
            )
        }
        val parameters = Estimator.parameters(platform.profile, settings?.shortFormShare, settings?.secondsPerReel)
        return DailyRow(
            date = date,
            platformId = platform.platform.id,
            foregroundMs = totals.foregroundMs,
            sessionCount = totals.sessionCount,
            quality = MetricQuality.ESTIMATED,
            estimate = Estimator.estimate(totals.foregroundMs, parameters),
            estimationVersion = parameters.version,
            userAdjusted = parameters.userAdjusted,
            tzId = tzId,
            isFinal = false,
            revision = revision,
            dirty = true,
        )
    }
}
