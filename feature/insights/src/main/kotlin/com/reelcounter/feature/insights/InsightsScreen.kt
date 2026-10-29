package com.reelcounter.feature.insights

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.reelcounter.core.designsystem.component.EmptyState
import com.reelcounter.core.designsystem.component.RcBar
import com.reelcounter.core.designsystem.component.RcBarChart
import com.reelcounter.core.designsystem.component.RcCard
import com.reelcounter.core.designsystem.component.RcDivider
import com.reelcounter.core.designsystem.component.RcListCard
import com.reelcounter.core.designsystem.component.RcProgressBar
import com.reelcounter.core.designsystem.component.RcScreenHeader
import com.reelcounter.core.designsystem.component.RcSectionHeader
import com.reelcounter.core.designsystem.component.RcSegmentedControl
import com.reelcounter.core.designsystem.component.SkeletonBlock
import com.reelcounter.core.designsystem.icon.RcIcons
import com.reelcounter.core.designsystem.theme.Spacing
import com.reelcounter.core.ui.metric.DaySummary
import com.reelcounter.core.ui.metric.PlatformLogo
import com.reelcounter.core.ui.metric.formatAppTime
import com.reelcounter.core.ui.metric.formatCompactAppTime
import com.reelcounter.core.ui.metric.formatCount
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.minutes
import com.reelcounter.core.ui.R as CoreUiR

@Composable
internal fun InsightsScreen(
    modifier: Modifier = Modifier,
    viewModel: InsightsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    InsightsScreen(uiState = uiState, modifier = modifier)
}

/** Which figure the screen charts. Reel counts exist only where estimation is on. */
private enum class InsightsMetric { Reels, AppTime }

@Composable
internal fun InsightsScreen(uiState: InsightsUiState, modifier: Modifier = Modifier) {
    Scaffold(modifier = modifier) { innerPadding ->
        val contentModifier = Modifier
            .padding(innerPadding)
            .fillMaxSize()
        when (uiState) {
            InsightsUiState.Loading -> InsightsLoading(contentModifier)
            InsightsUiState.Empty -> Column(contentModifier.padding(horizontal = Spacing.screen)) {
                RcScreenHeader(title = stringResource(R.string.feature_insights_title))
                EmptyState(
                    title = stringResource(R.string.feature_insights_empty_title),
                    message = stringResource(R.string.feature_insights_empty_message),
                    icon = RcIcons.Insights,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )
            }
            is InsightsUiState.Content -> InsightsContent(uiState, contentModifier)
        }
    }
}

@Composable
private fun InsightsContent(state: InsightsUiState.Content, modifier: Modifier) {
    var selected by rememberSaveable { mutableIntStateOf(0) }
    val metric = if (state.hasCounts && selected == 0) InsightsMetric.Reels else InsightsMetric.AppTime
    val anyEstimate = (state.thisWeek + state.previousWeek).any { it.isEstimate }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, bottom = Spacing.xxl),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item(key = "header") {
            RcScreenHeader(
                title = stringResource(R.string.feature_insights_title),
                subtitle = stringResource(R.string.feature_insights_subtitle),
                modifier = Modifier.widthIn(max = ContentMaxWidth),
            )
        }
        if (state.hasCounts) {
            item(key = "metric") {
                RcSegmentedControl(
                    options = listOf(
                        stringResource(R.string.feature_insights_metric_reels),
                        stringResource(R.string.feature_insights_metric_time),
                    ),
                    selectedIndex = selected,
                    onSelect = { selected = it },
                    modifier = Modifier
                        .widthIn(max = ContentMaxWidth)
                        .padding(top = Spacing.xs),
                )
            }
        }
        item(key = "summary") { SummaryCard(state, metric, anyEstimate) }
        item(key = "chart-header") { SectionTitle(stringResource(R.string.feature_insights_chart_title)) }
        item(key = "chart") { ChartCard(state.thisWeek, metric) }
        if (state.platforms.isNotEmpty()) {
            item(key = "apps-header") { SectionTitle(stringResource(R.string.feature_insights_by_app_title)) }
            item(key = "apps") { PlatformsCard(state.platforms, metric) }
        }
        item(key = "history-header") { SectionTitle(stringResource(R.string.feature_insights_history_title)) }
        item(key = "history") { HistoryCard(state.previousWeek + state.thisWeek, metric) }
        item(key = "note") {
            Text(
                text = stringResource(R.string.feature_insights_partial_note),
                modifier = Modifier
                    .widthIn(max = ContentMaxWidth)
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.xs),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    RcSectionHeader(
        title = text,
        modifier = Modifier
            .widthIn(max = ContentMaxWidth)
            .padding(top = Spacing.md),
    )
}

/** Total, daily average and change versus the previous seven days. */
@Composable
private fun SummaryCard(state: InsightsUiState.Content, metric: InsightsMetric, anyEstimate: Boolean) {
    val thisTotal = total(state.thisWeek, metric)
    val previousTotal = total(state.previousWeek, metric)
    val average = average(state.thisWeek, metric)

    RcCard(
        modifier = Modifier
            .widthIn(max = ContentMaxWidth)
            .fillMaxWidth(),
        contentPadding = PaddingValues(vertical = Spacing.lg, horizontal = Spacing.lg),
        mergeSemantics = false,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            SummaryTile(
                label = stringResource(R.string.feature_insights_total),
                value = thisTotal?.let { formatValue(it, metric, anyEstimate) } ?: stringResource(R.string.feature_insights_no_data),
                emphasized = true,
                modifier = Modifier.weight(1f),
            )
            SummaryTile(
                label = stringResource(R.string.feature_insights_average),
                value = average?.let { formatValue(it, metric, anyEstimate) } ?: stringResource(R.string.feature_insights_no_data),
                modifier = Modifier.weight(1f),
            )
            ChangeTile(thisTotal = thisTotal, previousTotal = previousTotal, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun SummaryTile(label: String, value: String, modifier: Modifier = Modifier, emphasized: Boolean = false) {
    Column(modifier = modifier.semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            color = if (emphasized) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The change in percent. Only shown when the earlier period has data to compare against. */
@Composable
private fun ChangeTile(thisTotal: Double?, previousTotal: Double?, modifier: Modifier = Modifier) {
    val percent = if (thisTotal != null && previousTotal != null && previousTotal > 0.0) {
        ((thisTotal - previousTotal) / previousTotal * 100).roundToInt()
    } else {
        null
    }
    val value = when {
        percent == null -> stringResource(R.string.feature_insights_change_none)
        percent > 0 -> stringResource(R.string.feature_insights_change_up, percent)
        percent < 0 -> stringResource(R.string.feature_insights_change_down, abs(percent))
        else -> stringResource(R.string.feature_insights_change_same)
    }
    val description = when {
        percent == null || percent == 0 -> value
        percent > 0 -> stringResource(R.string.feature_insights_change_up_description, percent)
        else -> stringResource(R.string.feature_insights_change_down_description, abs(percent))
    }
    val label = stringResource(R.string.feature_insights_change)
    Column(
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = "$label: $description" },
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = value,
            style = if (percent == null) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.titleLarge,
            color = if (percent == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
        )
    }
}

@Composable
private fun ChartCard(week: List<DaySummary>, metric: InsightsMetric) {
    val locale = LocalConfiguration.current.locales[0]
    RcCard(
        modifier = Modifier
            .widthIn(max = ContentMaxWidth)
            .fillMaxWidth(),
        mergeSemantics = false,
    ) {
        val bars = week.mapIndexed { index, day ->
            val dayName = remember(day.date, locale) { day.date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale) }
            val fullDate = remember(day.date, locale) { DateTimeFormatter.ofPattern("EEEE d MMMM", locale).format(day.date) }
            val count = day.count
            val appTime = day.appTime
            when (metric) {
                InsightsMetric.Reels -> RcBar(
                    label = dayName,
                    value = count?.toFloat(),
                    valueLabel = count?.let { formatCount(it) }.orEmpty(),
                    description = when {
                        count == null -> stringResource(R.string.feature_insights_bar_no_data, fullDate)
                        day.isEstimate -> stringResource(R.string.feature_insights_bar_reels, fullDate, formatCount(count))
                        else -> stringResource(R.string.feature_insights_bar_videos, fullDate, formatCount(count))
                    },
                    highlighted = index == week.lastIndex,
                )
                InsightsMetric.AppTime -> RcBar(
                    label = dayName,
                    value = appTime?.inWholeMinutes?.toFloat(),
                    valueLabel = appTime?.let { formatCompactAppTime(it) }.orEmpty(),
                    description = appTime?.let { stringResource(R.string.feature_insights_bar_time, fullDate, formatAppTime(it)) }
                        ?: stringResource(R.string.feature_insights_bar_no_data, fullDate),
                    highlighted = index == week.lastIndex,
                )
            }
        }
        RcBarChart(bars = bars, chartHeight = 150.dp)
    }
}

@Composable
private fun PlatformsCard(platforms: List<PlatformTotal>, metric: InsightsMetric) {
    val weekTotal = platforms.sumOf { platformValue(it, metric) ?: 0.0 }
    RcListCard(
        modifier = Modifier
            .widthIn(max = ContentMaxWidth)
            .fillMaxWidth(),
    ) {
        platforms.forEachIndexed { index, total ->
            if (index > 0) RcDivider(startInset = 68.dp)
            val value = platformValue(total, metric)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics(mergeDescendants = true) {}
                    .padding(horizontal = Spacing.lg, vertical = Spacing.md),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PlatformLogo(platform = total.platform)
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = total.platform.displayName,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = value?.let { formatValue(it, metric, total.isEstimate) }
                                ?: stringResource(R.string.feature_insights_no_data),
                            style = MaterialTheme.typography.titleSmall,
                            color = if (value == null) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    if (value != null && weekTotal > 0.0) {
                        val share = (value / weekTotal).toFloat()
                        RcProgressBar(fraction = share)
                        Text(
                            text = stringResource(R.string.feature_insights_platform_share, (share * 100).roundToInt()),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Text(
                            text = stringResource(
                                if (value == null) R.string.feature_insights_platform_no_data else R.string.feature_insights_platform_zero,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/** Every day of the period, newest first, with "No data" where nothing was recorded. */
@Composable
private fun HistoryCard(days: List<DaySummary>, metric: InsightsMetric) {
    val locale = LocalConfiguration.current.locales[0]
    val newestFirst = days.asReversed()
    val today = newestFirst.firstOrNull()?.date
    RcListCard(
        modifier = Modifier
            .widthIn(max = ContentMaxWidth)
            .fillMaxWidth(),
    ) {
        newestFirst.forEachIndexed { index, day ->
            if (index > 0) RcDivider()
            val value = dayValue(day, metric)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .semantics(mergeDescendants = true) {}
                    .padding(horizontal = Spacing.lg, vertical = Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = dayLabel(day.date, today, locale),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = value?.let { formatValue(it, metric, day.isEstimate) } ?: stringResource(R.string.feature_insights_no_data),
                    style = MaterialTheme.typography.titleSmall,
                    color = if (value == null) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun dayLabel(date: LocalDate, today: LocalDate?, locale: java.util.Locale): String = when (date) {
    today -> stringResource(R.string.feature_insights_today)
    today?.minusDays(1) -> stringResource(R.string.feature_insights_yesterday)
    else -> remember(date, locale) { DateTimeFormatter.ofPattern("EEE d MMM", locale).format(date) }
}

@Composable
private fun InsightsLoading(modifier: Modifier) {
    Column(
        modifier = modifier.padding(horizontal = Spacing.screen, vertical = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        SkeletonBlock(modifier = Modifier.fillMaxWidth(0.4f).height(30.dp))
        SkeletonBlock(modifier = Modifier.fillMaxWidth().height(40.dp), shape = MaterialTheme.shapes.medium)
        SkeletonBlock(modifier = Modifier.fillMaxWidth().height(96.dp), shape = MaterialTheme.shapes.large)
        SkeletonBlock(modifier = Modifier.fillMaxWidth().height(200.dp), shape = MaterialTheme.shapes.large)
    }
}

// Values are Doubles internally: a count, or app time in minutes.

private fun dayValue(day: DaySummary, metric: InsightsMetric): Double? = when (metric) {
    InsightsMetric.Reels -> day.count?.toDouble()
    InsightsMetric.AppTime -> day.appTime?.inWholeMinutes?.toDouble()
}

private fun platformValue(total: PlatformTotal, metric: InsightsMetric): Double? = when (metric) {
    InsightsMetric.Reels -> total.count?.toDouble()
    InsightsMetric.AppTime -> total.appTime?.inWholeMinutes?.toDouble()
}

private fun total(days: List<DaySummary>, metric: InsightsMetric): Double? =
    days.mapNotNull { dayValue(it, metric) }.takeIf { it.isNotEmpty() }?.sum()

private fun average(days: List<DaySummary>, metric: InsightsMetric): Double? =
    days.mapNotNull { dayValue(it, metric) }.takeIf { it.isNotEmpty() }?.average()

@Composable
private fun formatValue(value: Double, metric: InsightsMetric, isEstimate: Boolean): String = when (metric) {
    InsightsMetric.Reels -> {
        val count = formatCount(value.roundToInt())
        if (isEstimate) stringResource(CoreUiR.string.core_ui_metric_approx, count) else count
    }
    InsightsMetric.AppTime -> formatAppTime(value.roundToInt().minutes)
}

private val ContentMaxWidth = 640.dp
