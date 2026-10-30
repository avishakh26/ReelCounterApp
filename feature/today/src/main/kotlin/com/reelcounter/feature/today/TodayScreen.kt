package com.reelcounter.feature.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.reelcounter.core.designsystem.component.RcBanner
import com.reelcounter.core.designsystem.component.RcBar
import com.reelcounter.core.designsystem.component.RcBarChart
import com.reelcounter.core.designsystem.component.RcButton
import com.reelcounter.core.designsystem.component.RcCard
import com.reelcounter.core.designsystem.component.RcDivider
import com.reelcounter.core.designsystem.component.RcIconTile
import com.reelcounter.core.designsystem.component.RcListCard
import com.reelcounter.core.designsystem.component.RcScreenHeader
import com.reelcounter.core.designsystem.component.RcSectionHeader
import com.reelcounter.core.designsystem.component.RcStatusPill
import com.reelcounter.core.designsystem.component.RcTone
import com.reelcounter.core.designsystem.component.SkeletonBlock
import com.reelcounter.core.designsystem.icon.RcIcons
import com.reelcounter.core.designsystem.theme.Spacing
import com.reelcounter.core.domain.tracking.TrackingStatus
import com.reelcounter.core.model.UserProfile
import com.reelcounter.core.ui.metric.DaySummary
import com.reelcounter.core.ui.metric.daysForAverage
import com.reelcounter.core.ui.metric.PlatformActivityRow
import com.reelcounter.core.ui.metric.formatAppTime
import com.reelcounter.core.ui.metric.formatCompactAppTime
import com.reelcounter.core.ui.metric.formatCount
import com.reelcounter.core.ui.profile.Avatar
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import kotlin.time.Duration
import com.reelcounter.core.ui.R as CoreUiR

@Composable
internal fun TodayScreen(
    onSetUpTracking: () -> Unit,
    onOpenInsights: () -> Unit,
    onOpenProfile: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TodayViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Read the usage log every time the screen comes back, so figures are fresh even when
    // background work was delayed by the system (docs/phase-1/04-technical-limitations.md L2, L5).
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    TodayScreen(
        uiState = uiState,
        onRefresh = viewModel::refresh,
        onSetUpTracking = onSetUpTracking,
        onOpenInsights = onOpenInsights,
        onOpenProfile = onOpenProfile,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TodayScreen(
    uiState: TodayUiState,
    onRefresh: () -> Unit,
    onSetUpTracking: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenInsights: () -> Unit = {},
    onOpenProfile: () -> Unit = {},
) {
    Scaffold(modifier = modifier) { innerPadding ->
        val contentModifier = Modifier
            .padding(innerPadding)
            .fillMaxSize()
        when (uiState) {
            TodayUiState.Loading -> TodayLoading(contentModifier)
            is TodayUiState.NeedsSetup -> TodaySetup(uiState.greetingName, uiState.countingAvailable, onSetUpTracking, contentModifier)
            is TodayUiState.Content -> PullToRefreshBox(
                isRefreshing = uiState.isRefreshing,
                onRefresh = onRefresh,
                modifier = contentModifier,
            ) {
                TodayContent(uiState, onSetUpTracking, onOpenInsights, onOpenProfile)
            }
        }
    }
}

@Composable
private fun TodayContent(
    state: TodayUiState.Content,
    onSetUpTracking: () -> Unit,
    onOpenInsights: () -> Unit,
    onOpenProfile: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, bottom = Spacing.xxl),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item(key = "header") {
            TodayHeader(date = state.date, profile = state.profile, onOpenProfile = onOpenProfile)
        }
        statusNotice(state.status, onSetUpTracking)
        item(key = "hero") { HeroCard(state) }
        item(key = "week-header") {
            RcSectionHeader(
                title = stringResource(R.string.feature_today_week_title),
                actionLabel = stringResource(R.string.feature_today_week_action),
                onAction = onOpenInsights,
                modifier = Modifier
                    .widthIn(max = ContentMaxWidth)
                    .padding(top = Spacing.md),
            )
        }
        item(key = "week") { WeekCard(state.week) }
        if (state.activities.isNotEmpty()) {
            item(key = "apps-header") {
                RcSectionHeader(
                    title = stringResource(R.string.feature_today_by_app_title),
                    modifier = Modifier
                        .widthIn(max = ContentMaxWidth)
                        .padding(top = Spacing.md),
                )
            }
            item(key = "apps") {
                RcListCard(modifier = Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth()) {
                    state.activities.forEachIndexed { index, activity ->
                        if (index > 0) RcDivider(startInset = 68.dp)
                        PlatformActivityRow(activity = activity)
                    }
                }
            }
        }
        item(key = "note") {
            Text(
                text = stringResource(
                    if (state.countingEnabled) R.string.feature_today_counted_note else R.string.feature_today_estimate_note,
                ),
                modifier = Modifier
                    .widthIn(max = ContentMaxWidth)
                    .fillMaxWidth()
                    .padding(top = Spacing.xs, start = Spacing.xs, end = Spacing.xs),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TodayHeader(date: LocalDate, profile: UserProfile?, onOpenProfile: () -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    val dateText = remember(date, locale) { DateTimeFormatter.ofPattern("EEEE, d MMMM", locale).format(date) }
    RcScreenHeader(
        title = greeting(profile?.greetingName()),
        eyebrow = dateText,
        modifier = Modifier.widthIn(max = ContentMaxWidth),
        trailing = {
            val openProfileLabel = stringResource(R.string.feature_today_open_profile)
            IconButton(
                onClick = onOpenProfile,
                modifier = Modifier
                    .size(52.dp)
                    .semantics { contentDescription = openProfileLabel },
            ) {
                Avatar(
                    image = profile?.avatarUrl,
                    name = profile?.displayName ?: profile?.username.orEmpty(),
                    size = 40.dp,
                )
            }
        },
    )
}

@Composable
private fun greeting(name: String?): String {
    val hour = remember { LocalTime.now().hour }
    val base = stringResource(
        when (hour) {
            in 5..11 -> R.string.feature_today_greeting_morning
            in 12..17 -> R.string.feature_today_greeting_afternoon
            else -> R.string.feature_today_greeting_evening
        },
    )
    return if (name != null) stringResource(R.string.feature_today_greeting_named, base, name) else base
}

private fun LazyListScope.statusNotice(status: TrackingStatus, onSetUpTracking: () -> Unit) {
    when (status) {
        TrackingStatus.NeedsUsageAccess -> item(key = "status") {
            RcBanner(
                title = stringResource(R.string.feature_today_access_off_title),
                message = stringResource(R.string.feature_today_access_off),
                icon = RcIcons.Error,
                tone = RcTone.Warning,
                actionLabel = stringResource(R.string.feature_today_turn_on),
                onAction = onSetUpTracking,
                modifier = Modifier.widthIn(max = ContentMaxWidth),
            )
        }
        TrackingStatus.Paused -> item(key = "status") {
            RcBanner(
                title = stringResource(R.string.feature_today_paused_title),
                message = stringResource(R.string.feature_today_paused),
                icon = RcIcons.Paused,
                tone = RcTone.Neutral,
                modifier = Modifier.widthIn(max = ContentMaxWidth),
            )
        }
        else -> Unit
    }
}

/** The day's main figure: estimated reels when there are any, otherwise app time. */
@Composable
private fun HeroCard(state: TodayUiState.Content) {
    val today = state.today
    val count = today.count
    val countLow = today.countLow
    val countHigh = today.countHigh
    val appTime = today.appTime
    RcCard(
        modifier = Modifier
            .widthIn(max = ContentMaxWidth)
            .fillMaxWidth(),
        contentPadding = PaddingValues(Spacing.xl),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(
                    when {
                        count != null && today.isEstimate -> R.string.feature_today_hero_estimated
                        count != null -> R.string.feature_today_hero_verified
                        appTime != null -> R.string.feature_today_hero_app_time
                        else -> R.string.feature_today_hero_empty
                    },
                ),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TrackingStatusPill(state.status)
        }

        when {
            count != null -> {
                Row(
                    modifier = Modifier.padding(top = Spacing.md),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    if (today.isEstimate) {
                        Text(
                            text = "≈",
                            modifier = Modifier.padding(end = Spacing.xs, bottom = Spacing.xs),
                            style = MaterialTheme.typography.headlineLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = formatCount(count),
                        style = MaterialTheme.typography.displayLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                if (today.isEstimate && countLow != null && countHigh != null && countLow != countHigh) {
                    Text(
                        text = stringResource(
                            CoreUiR.string.core_ui_metric_estimate_range,
                            formatCount(countLow),
                            formatCount(countHigh),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            appTime != null -> Text(
                text = formatAppTime(appTime),
                modifier = Modifier.padding(top = Spacing.md),
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            else -> Text(
                text = stringResource(R.string.feature_today_hero_empty_message),
                modifier = Modifier.padding(top = Spacing.sm),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        HorizontalDivider(
            modifier = Modifier.padding(vertical = Spacing.lg),
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
            HeroStat(
                icon = RcIcons.AppTime,
                label = stringResource(R.string.feature_today_stat_app_time),
                value = appTime?.let { formatAppTime(it) } ?: stringResource(R.string.feature_today_stat_no_data),
                modifier = Modifier.weight(1f),
            )
            HeroStat(
                icon = RcIcons.Insights,
                label = stringResource(R.string.feature_today_stat_yesterday),
                value = state.yesterday?.let { yesterdayValue(it, preferCount = count != null || appTime == null) }
                    ?: stringResource(R.string.feature_today_stat_no_data),
                modifier = Modifier.weight(1f),
            )
        }

        Text(
            text = lastUpdatedText(state.lastUpdatedMs),
            modifier = Modifier.padding(top = Spacing.lg),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Yesterday's total in the same unit as today's headline, so the two compare at a glance. */
@Composable
private fun yesterdayValue(day: DaySummary, preferCount: Boolean): String? {
    val count = day.count
    val appTime = day.appTime
    return when {
        preferCount && count != null -> if (day.isEstimate) {
            stringResource(CoreUiR.string.core_ui_metric_approx, formatCount(count)) + " " +
                pluralStringResource(CoreUiR.plurals.core_ui_metric_unit_reels, count)
        } else {
            formatCount(count) + " " + pluralStringResource(CoreUiR.plurals.core_ui_metric_unit_reels, count)
        }
        appTime != null -> formatAppTime(appTime)
        count != null -> formatCount(count)
        else -> null
    }
}

@Composable
private fun HeroStat(icon: ImageVector, label: String, value: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RcIconTile(icon = icon, size = 32.dp)
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(text = value, style = MaterialTheme.typography.titleSmall, maxLines = 1)
        }
    }
}

@Composable
private fun TrackingStatusPill(status: TrackingStatus) {
    when (status) {
        TrackingStatus.Active -> RcStatusPill(text = stringResource(R.string.feature_today_status_on), tone = RcTone.Positive)
        TrackingStatus.Paused -> RcStatusPill(text = stringResource(R.string.feature_today_status_paused), tone = RcTone.Neutral)
        TrackingStatus.NeedsUsageAccess, TrackingStatus.NeedsDisclosure ->
            RcStatusPill(text = stringResource(R.string.feature_today_status_no_access), tone = RcTone.Warning)
    }
}

/** The last seven days as bars: reel counts when any day has one, otherwise app time. */
@Composable
private fun WeekCard(week: List<DaySummary>) {
    val locale = LocalConfiguration.current.locales[0]
    val useCounts = week.any { it.count != null }
    val anyEstimate = week.any { it.isEstimate }
    val daysWithData = week.count { if (useCounts) it.count != null else it.appTime != null }

    RcCard(
        modifier = Modifier
            .widthIn(max = ContentMaxWidth)
            .fillMaxWidth(),
        mergeSemantics = false,
    ) {
        val bars = week.mapIndexed { index, day ->
            val isToday = index == week.lastIndex
            val dayName = remember(day.date, locale) { day.date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale) }
            val fullDate = remember(day.date, locale) { DateTimeFormatter.ofPattern("EEEE d MMMM", locale).format(day.date) }
            val count = day.count
            val value: Float?
            val valueLabel: String
            val description: String
            if (useCounts) {
                value = count?.toFloat()
                valueLabel = count?.let { formatCount(it) }.orEmpty()
                description = when {
                    count == null -> stringResource(R.string.feature_today_bar_no_data, fullDate)
                    day.isEstimate -> pluralStringResource(R.plurals.feature_today_bar_reels, count, fullDate, formatCount(count))
                    else -> pluralStringResource(R.plurals.feature_today_bar_videos, count, fullDate, formatCount(count))
                }
            } else {
                value = day.appTime?.inWholeMinutes?.toFloat()
                valueLabel = day.appTime?.let { formatCompactAppTime(it) }.orEmpty()
                description = day.appTime?.let { stringResource(R.string.feature_today_bar_time, fullDate, formatAppTime(it)) }
                    ?: stringResource(R.string.feature_today_bar_no_data, fullDate)
            }
            RcBar(label = dayName, value = value, valueLabel = valueLabel, description = description, highlighted = isToday)
        }
        RcBarChart(bars = bars, modifier = Modifier.padding(top = Spacing.xs))

        Text(
            text = if (daysWithData == 0) {
                stringResource(R.string.feature_today_week_empty)
            } else {
                weekSummary(week, useCounts, anyEstimate) + " · " +
                    stringResource(R.string.feature_today_week_days_with_data, daysWithData)
            },
            modifier = Modifier.padding(top = Spacing.md),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The average over completed days that have data. Days without data are left out rather than
 * counted as zero, and today counts only once it's over (see [daysForAverage]).
 */
@Composable
private fun weekSummary(week: List<DaySummary>, useCounts: Boolean, anyEstimate: Boolean): String =
    if (useCounts) {
        val counts = week.daysForAverage { it.count != null }.mapNotNull { it.count }
        val average = counts.sum() / counts.size
        stringResource(
            if (anyEstimate) R.string.feature_today_week_average_reels else R.string.feature_today_week_average_videos,
            formatCount(average),
        )
    } else {
        val times = week.daysForAverage { it.appTime != null }.mapNotNull { it.appTime }
        val average = times.fold(Duration.ZERO, Duration::plus) / times.size
        stringResource(R.string.feature_today_week_average_time, formatAppTime(average))
    }

@Composable
private fun TodaySetup(
    greetingName: String?,
    countingAvailable: Boolean,
    onSetUpTracking: () -> Unit,
    modifier: Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, bottom = Spacing.xxl),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item(key = "header") {
            RcScreenHeader(title = greeting(greetingName), modifier = Modifier.widthIn(max = ContentMaxWidth))
        }
        item(key = "setup") {
            RcCard(
                modifier = Modifier
                    .widthIn(max = ContentMaxWidth)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(Spacing.xl),
                mergeSemantics = false,
            ) {
                RcIconTile(
                    icon = RcIcons.Tracking,
                    size = 48.dp,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(R.string.feature_today_setup_title),
                    modifier = Modifier.padding(top = Spacing.lg),
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    text = stringResource(R.string.feature_today_setup_message),
                    modifier = Modifier.padding(top = Spacing.sm),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Column(
                    modifier = Modifier.padding(vertical = Spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    SetupPoint(RcIcons.AppTime, stringResource(R.string.feature_today_setup_point_time))
                    SetupPoint(
                        RcIcons.Reels,
                        stringResource(
                            if (countingAvailable) R.string.feature_today_setup_point_estimates else R.string.feature_today_setup_point_estimates_only,
                        ),
                    )
                    SetupPoint(RcIcons.Paused, stringResource(R.string.feature_today_setup_point_control))
                }
                RcButton(
                    text = stringResource(R.string.feature_today_setup_action),
                    onClick = onSetUpTracking,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun SetupPoint(icon: ImageVector, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
        Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Text(text = text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun TodayLoading(modifier: Modifier) {
    Column(
        modifier = modifier.padding(horizontal = Spacing.screen, vertical = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        SkeletonBlock(modifier = Modifier.fillMaxWidth(0.35f).height(14.dp))
        SkeletonBlock(modifier = Modifier.fillMaxWidth(0.6f).height(28.dp))
        Box(modifier = Modifier.padding(top = Spacing.md)) {
            SkeletonBlock(modifier = Modifier.fillMaxWidth().height(220.dp), shape = MaterialTheme.shapes.large)
        }
        SkeletonBlock(modifier = Modifier.fillMaxWidth().height(180.dp), shape = MaterialTheme.shapes.large)
    }
}

@Composable
private fun lastUpdatedText(lastUpdatedMs: Long?): String {
    if (lastUpdatedMs == null) return stringResource(R.string.feature_today_not_updated)
    val locale = LocalConfiguration.current.locales[0]
    val time = remember(lastUpdatedMs, locale) {
        DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale)
            .format(Instant.ofEpochMilli(lastUpdatedMs).atZone(ZoneId.systemDefault()))
    }
    return stringResource(R.string.feature_today_updated_at, time)
}

/** Content stays readable on tablets and in landscape instead of stretching edge to edge. */
private val ContentMaxWidth = 640.dp
