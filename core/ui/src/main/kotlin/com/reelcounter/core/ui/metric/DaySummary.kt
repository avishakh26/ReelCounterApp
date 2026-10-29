package com.reelcounter.core.ui.metric

import androidx.compose.runtime.Immutable
import com.reelcounter.core.model.DailyPlatformActivity
import com.reelcounter.core.model.PlatformMetric
import java.time.LocalDate
import kotlin.time.Duration

/**
 * One day across all tracked platforms, for dashboards and charts.
 *
 * Every figure is null when nothing was recorded, so a day without data is shown as "No data",
 * never as zero.
 */
@Immutable
data class DaySummary(
    val date: LocalDate,
    /** Reels (estimated) plus videos (verified) across platforms; null when no platform has a count. */
    val count: Int?,
    /** The likely range of [count]. Verified counts contribute their exact value to both ends. */
    val countLow: Int?,
    val countHigh: Int?,
    /** Measured foreground app time across platforms; null when nothing was measured. */
    val appTime: Duration?,
    /** True when any part of [count] is an estimate, so it must be shown with "≈". */
    val isEstimate: Boolean,
) {
    val hasData: Boolean get() = count != null || appTime != null
}

/** Sums one day's platform figures. Platforms without data are skipped, not counted as zero. */
fun List<DailyPlatformActivity>.summarize(date: LocalDate): DaySummary {
    var count: Int? = null
    var low: Int? = null
    var high: Int? = null
    var appTime: Duration? = null
    var isEstimate = false
    forEach { activity ->
        when (val metric = activity.metric) {
            is PlatformMetric.Verified -> {
                count = (count ?: 0) + metric.count
                low = (low ?: 0) + metric.count
                high = (high ?: 0) + metric.count
                metric.appTime?.let { appTime = (appTime ?: Duration.ZERO) + it }
            }
            is PlatformMetric.Counted -> {
                count = (count ?: 0) + metric.count
                low = (low ?: 0) + metric.count
                high = (high ?: 0) + metric.count
                appTime = (appTime ?: Duration.ZERO) + metric.appTime
            }
            is PlatformMetric.Estimated -> {
                count = (count ?: 0) + metric.count
                low = (low ?: 0) + metric.low
                high = (high ?: 0) + metric.high
                appTime = (appTime ?: Duration.ZERO) + metric.appTime
                isEstimate = true
            }
            is PlatformMetric.DurationOnly -> appTime = (appTime ?: Duration.ZERO) + metric.appTime
            is PlatformMetric.Unavailable -> Unit
        }
    }
    return DaySummary(date = date, count = count, countLow = low, countHigh = high, appTime = appTime, isEstimate = isEstimate)
}
