package com.reelcounter.feature.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reelcounter.core.domain.tracking.CurrentDateProvider
import com.reelcounter.core.domain.tracking.TrackingRepository
import com.reelcounter.core.model.DailyPlatformActivity
import com.reelcounter.core.model.Platform
import com.reelcounter.core.model.PlatformMetric
import com.reelcounter.core.ui.metric.DaySummary
import com.reelcounter.core.ui.metric.summarize
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import javax.inject.Inject
import kotlin.time.Duration

sealed interface InsightsUiState {
    data object Loading : InsightsUiState

    /** Nothing was recorded in the whole period, so there is nothing honest to chart. */
    data object Empty : InsightsUiState

    data class Content(
        /** The seven days ending today, oldest first. */
        val thisWeek: List<DaySummary>,
        /** The seven days before those, oldest first. */
        val previousWeek: List<DaySummary>,
        /** Each tracked platform's totals over [thisWeek], largest first. */
        val platforms: List<PlatformTotal>,
    ) : InsightsUiState {
        /** Reel counts are shown when any day in the period has one; otherwise only app time. */
        val hasCounts: Boolean get() = (thisWeek + previousWeek).any { it.count != null }
    }
}

/** One platform's figures summed over a week. Null when the platform recorded nothing. */
data class PlatformTotal(
    val platform: Platform,
    val count: Int?,
    val appTime: Duration?,
    val isEstimate: Boolean,
)

/**
 * Weekly trends from the same per-day figures as the Today screen. Nothing is computed here that
 * the tracking engine didn't record: days without data stay empty and are left out of averages.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class InsightsViewModel @Inject constructor(
    trackingRepository: TrackingRepository,
    currentDateProvider: CurrentDateProvider,
) : ViewModel() {

    val uiState: StateFlow<InsightsUiState> = currentDateProvider.today.flatMapLatest { today ->
        val dates = (PERIOD_DAYS - 1 downTo 0).map { today.minusDays(it.toLong()) }
        combine(dates.map { trackingRepository.observeDay(it) }) { days -> buildState(dates, days.toList()) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InsightsUiState.Loading)

    private companion object {
        const val PERIOD_DAYS = 14
    }
}

internal fun buildState(dates: List<LocalDate>, days: List<List<DailyPlatformActivity>>): InsightsUiState {
    val summaries = dates.zip(days) { date, activities -> activities.summarize(date) }
    if (summaries.none { it.hasData }) return InsightsUiState.Empty
    val half = summaries.size / 2
    val thisWeekActivities = days.drop(half).flatten()
    val platforms = thisWeekActivities
        .groupBy { it.platform }
        .map { (platform, activities) -> platformTotal(platform, activities) }
        .sortedWith(compareByDescending<PlatformTotal> { it.count ?: -1 }.thenByDescending { it.appTime ?: Duration.ZERO })
    return InsightsUiState.Content(
        thisWeek = summaries.drop(half),
        previousWeek = summaries.take(half),
        platforms = platforms,
    )
}

private fun platformTotal(platform: Platform, activities: List<DailyPlatformActivity>): PlatformTotal {
    var count: Int? = null
    var appTime: Duration? = null
    var isEstimate = false
    activities.forEach { activity ->
        when (val metric = activity.metric) {
            is PlatformMetric.Verified -> {
                count = (count ?: 0) + metric.count
                metric.appTime?.let { appTime = (appTime ?: Duration.ZERO) + it }
            }
            is PlatformMetric.Counted -> {
                count = (count ?: 0) + metric.count
                appTime = (appTime ?: Duration.ZERO) + metric.appTime
            }
            is PlatformMetric.Estimated -> {
                count = (count ?: 0) + metric.count
                appTime = (appTime ?: Duration.ZERO) + metric.appTime
                isEstimate = true
            }
            is PlatformMetric.DurationOnly -> appTime = (appTime ?: Duration.ZERO) + metric.appTime
            is PlatformMetric.Unavailable -> Unit
        }
    }
    return PlatformTotal(platform = platform, count = count, appTime = appTime, isEstimate = isEstimate)
}
