package com.reelcounter.feature.friends.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.reelcounter.core.designsystem.component.EmptyState
import com.reelcounter.core.designsystem.component.RcBar
import com.reelcounter.core.designsystem.component.RcBarChart
import com.reelcounter.core.designsystem.component.RcCard
import com.reelcounter.core.designsystem.component.RcDivider
import com.reelcounter.core.designsystem.component.RcIconTile
import com.reelcounter.core.designsystem.component.RcListCard
import com.reelcounter.core.designsystem.component.RcLoadingIndicator
import com.reelcounter.core.designsystem.component.RcSectionHeader
import com.reelcounter.core.designsystem.component.RcStatusPill
import com.reelcounter.core.designsystem.component.RcTone
import com.reelcounter.core.designsystem.component.RcTopAppBar
import com.reelcounter.core.designsystem.icon.RcIcons
import com.reelcounter.core.designsystem.theme.Spacing
import com.reelcounter.core.ui.metric.DayDetail
import com.reelcounter.core.ui.metric.DaySummary
import com.reelcounter.core.ui.metric.PlatformActivityRow
import com.reelcounter.core.ui.metric.formatAppTime
import com.reelcounter.core.ui.metric.formatCompactAppTime
import com.reelcounter.core.ui.metric.formatCount
import com.reelcounter.core.ui.profile.Avatar
import com.reelcounter.feature.friends.R
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import com.reelcounter.core.ui.R as CoreUiR

@Composable
internal fun FriendDetailScreen(
    onBack: () -> Unit,
    viewModel: FriendDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    // Their phone may have synced while this screen was in the background.
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }
    FriendDetailScreen(uiState = uiState, isRefreshing = isRefreshing, onRefresh = viewModel::pullToRefresh, onBack = onBack)
}

@Composable
internal fun FriendDetailScreen(
    uiState: FriendDetailUiState,
    onBack: () -> Unit,
    isRefreshing: Boolean = false,
    onRefresh: () -> Unit = {},
) {
    Scaffold(
        topBar = {
            RcTopAppBar(
                title = (uiState as? FriendDetailUiState.Content)?.user?.nameForDisplay.orEmpty(),
                navigationIcon = RcIcons.Back,
                navigationIconContentDescription = stringResource(R.string.feature_friends_detail_back),
                onNavigationClick = onBack,
            )
        },
    ) { innerPadding ->
        val modifier = Modifier
            .padding(innerPadding)
            .fillMaxSize()
        when (uiState) {
            FriendDetailUiState.Loading -> RcLoadingIndicator(modifier = modifier)
            FriendDetailUiState.NotFriends -> EmptyState(
                title = stringResource(R.string.feature_friends_detail_not_friends_title),
                message = stringResource(R.string.feature_friends_detail_not_friends_message),
                icon = RcIcons.Friends,
                modifier = modifier,
            )
            // Figures also refresh live and whenever the screen resumes.
            is FriendDetailUiState.Content -> PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = onRefresh,
                modifier = modifier,
            ) { FriendContent(uiState) }
        }
    }
}

@Composable
private fun FriendContent(state: FriendDetailUiState.Content) {
    val name = state.user.nameForDisplay
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, top = Spacing.sm, bottom = Spacing.xxl),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item(key = "profile") { ProfileHeader(state) }
        if (state.loaded && !state.sharesAnything) {
            item(key = "empty") {
                EmptyState(
                    title = stringResource(R.string.feature_friends_detail_empty_title),
                    message = stringResource(R.string.feature_friends_detail_empty_message, name),
                    icon = RcIcons.Unavailable,
                )
            }
            return@LazyColumn
        }
        item(key = "hero") { HeroCard(state) }
        item(key = "week-title") { Section(stringResource(R.string.feature_friends_detail_week)) }
        item(key = "week") { WeekCard(state.week) }
        if (state.todayActivities.isNotEmpty()) {
            item(key = "apps-title") { Section(stringResource(R.string.feature_friends_detail_by_app)) }
            item(key = "apps") {
                RcListCard(modifier = Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth()) {
                    state.todayActivities.forEachIndexed { index, activity ->
                        if (index > 0) RcDivider(startInset = 68.dp)
                        PlatformActivityRow(activity = activity)
                    }
                }
            }
        }
        item(key = "note") {
            Text(
                text = stringResource(R.string.feature_friends_detail_live_note, name),
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
private fun Section(title: String) {
    RcSectionHeader(title = title, modifier = Modifier.widthIn(max = ContentMaxWidth).padding(top = Spacing.md))
}

@Composable
private fun ProfileHeader(state: FriendDetailUiState.Content) {
    Row(
        modifier = Modifier
            .widthIn(max = ContentMaxWidth)
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(image = state.user.avatarUrl, name = state.user.nameForDisplay, size = 64.dp)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(
                text = state.user.nameForDisplay,
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "@${state.user.username}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (state.online) RcStatusPill(text = stringResource(R.string.feature_friends_online), tone = RcTone.Positive)
            }
        }
    }
}

/** Their main figure today: estimated reels when there are any, otherwise app time. */
@Composable
private fun HeroCard(state: FriendDetailUiState.Content) {
    val today = state.today
    val count = today.count
    val low = today.countLow
    val high = today.countHigh
    val appTime = today.appTime
    RcCard(
        modifier = Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth(),
        contentPadding = PaddingValues(Spacing.xl),
    ) {
        Text(
            text = stringResource(
                when {
                    count != null && today.isEstimate -> R.string.feature_friends_detail_hero_estimated
                    count != null -> R.string.feature_friends_detail_hero_verified
                    appTime != null -> R.string.feature_friends_detail_hero_time
                    else -> R.string.feature_friends_detail_hero_empty
                },
            ),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        when {
            count != null -> {
                Row(modifier = Modifier.padding(top = Spacing.md), verticalAlignment = Alignment.Bottom) {
                    if (today.isEstimate) {
                        Text(
                            text = "≈",
                            modifier = Modifier.padding(end = Spacing.xs, bottom = Spacing.xs),
                            style = MaterialTheme.typography.headlineLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(text = formatCount(count), style = MaterialTheme.typography.displayLarge, color = MaterialTheme.colorScheme.primary)
                }
                if (today.isEstimate && low != null && high != null && low != high) {
                    Text(
                        text = stringResource(CoreUiR.string.core_ui_metric_estimate_range, formatCount(low), formatCount(high)),
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
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.lg), thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
            Stat(
                icon = RcIcons.AppTime,
                label = stringResource(R.string.feature_friends_detail_stat_time),
                value = appTime?.let { formatAppTime(it) } ?: stringResource(R.string.feature_friends_detail_no_data),
                modifier = Modifier.weight(1f),
            )
            Stat(
                icon = RcIcons.Insights,
                label = stringResource(R.string.feature_friends_detail_stat_yesterday),
                value = state.yesterday?.let { dayValue(it) } ?: stringResource(R.string.feature_friends_detail_no_data),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** A day's total in the hero's unit, or "No data". */
@Composable
private fun dayValue(day: DaySummary): String {
    val count = day.count
    val appTime = day.appTime
    return when {
        count != null && day.isEstimate -> stringResource(CoreUiR.string.core_ui_metric_approx, formatCount(count)) + " " +
            pluralStringResource(CoreUiR.plurals.core_ui_metric_unit_reels, count)
        count != null -> formatCount(count) + " " + pluralStringResource(CoreUiR.plurals.core_ui_metric_unit_reels, count)
        appTime != null -> formatAppTime(appTime)
        else -> stringResource(R.string.feature_friends_detail_no_data)
    }
}

@Composable
private fun Stat(icon: ImageVector, label: String, value: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RcIconTile(icon = icon, size = 32.dp)
        Column {
            Text(text = label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(text = value, style = MaterialTheme.typography.titleSmall, maxLines = 1)
        }
    }
}

/** Seven days as bars: reels when any day has a count, otherwise app time. Missing days are stubs. */
@Composable
private fun WeekCard(week: List<DaySummary>) {
    val locale = LocalConfiguration.current.locales[0]
    var selectedDay by rememberSaveable(week.lastOrNull()?.date) { mutableStateOf<Int?>(null) }
    val useCounts = week.any { it.count != null }
    RcCard(modifier = Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth(), mergeSemantics = false) {
        val bars = week.mapIndexed { index, day ->
            val dayName = remember(day.date, locale) { day.date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale) }
            val fullDate = remember(day.date, locale) { DateTimeFormatter.ofPattern("EEEE d MMMM", locale).format(day.date) }
            val count = day.count
            val appTime = day.appTime
            if (useCounts) {
                RcBar(
                    label = dayName,
                    value = count?.toFloat(),
                    valueLabel = count?.let { formatCount(it) }.orEmpty(),
                    description = when {
                        count == null -> stringResource(R.string.feature_friends_bar_no_data, fullDate)
                        day.isEstimate -> pluralStringResource(R.plurals.feature_friends_bar_reels, count, fullDate, formatCount(count))
                        else -> pluralStringResource(R.plurals.feature_friends_bar_videos, count, fullDate, formatCount(count))
                    },
                    highlighted = index == week.lastIndex,
                )
            } else {
                RcBar(
                    label = dayName,
                    value = appTime?.inWholeMinutes?.toFloat(),
                    valueLabel = appTime?.let { formatCompactAppTime(it) }.orEmpty(),
                    description = appTime?.let { stringResource(R.string.feature_friends_bar_time, fullDate, formatAppTime(it)) }
                        ?: stringResource(R.string.feature_friends_bar_no_data, fullDate),
                    highlighted = index == week.lastIndex,
                )
            }
        }
        RcBarChart(
            bars = bars,
            selectedIndex = selectedDay,
            onBarClick = { index -> selectedDay = index.takeUnless { it == selectedDay } },
        )
        val picked = selectedDay?.let { week.getOrNull(it) }
        if (picked != null) {
            DayDetail(day = picked, modifier = Modifier.padding(top = Spacing.md))
        } else {
            Text(
                text = stringResource(CoreUiR.string.core_ui_day_tap_hint),
                modifier = Modifier.padding(top = Spacing.md),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private val ContentMaxWidth = 640.dp
