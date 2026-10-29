package com.reelcounter.feature.tracking.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.reelcounter.core.designsystem.component.RcBanner
import com.reelcounter.core.designsystem.component.RcDivider
import com.reelcounter.core.designsystem.component.RcIconTile
import com.reelcounter.core.designsystem.component.RcListCard
import com.reelcounter.core.designsystem.component.RcListRow
import com.reelcounter.core.designsystem.component.RcLoadingIndicator
import com.reelcounter.core.designsystem.component.RcSectionHeader
import com.reelcounter.core.designsystem.component.RcStatusPill
import com.reelcounter.core.designsystem.component.RcSwitchRow
import com.reelcounter.core.designsystem.component.RcTextButton
import com.reelcounter.core.designsystem.component.RcTonalButton
import com.reelcounter.core.designsystem.component.RcTone
import com.reelcounter.core.designsystem.component.RcTopAppBar
import com.reelcounter.core.designsystem.icon.RcIcons
import com.reelcounter.core.designsystem.theme.Spacing
import com.reelcounter.core.domain.tracking.PlatformConfiguration
import com.reelcounter.core.domain.tracking.TrackingStatus
import com.reelcounter.core.ui.metric.PlatformLogo
import com.reelcounter.feature.tracking.R
import com.reelcounter.feature.tracking.openAccessibilitySettings
import com.reelcounter.feature.tracking.openUsageAccessSettings
import kotlin.math.roundToInt

@Composable
internal fun TrackingSettingsScreen(
    onBack: () -> Unit,
    onSetUpTracking: () -> Unit,
    viewModel: TrackingSettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    TrackingSettingsScreen(
        uiState = uiState,
        actions = TrackingSettingsActions(
            onBack = onBack,
            onSetUpTracking = onSetUpTracking,
            onOpenUsageAccess = { context.openUsageAccessSettings() },
            onOpenAccessibility = { context.openAccessibilitySettings() },
            onPausedChange = viewModel::setPaused,
            onTrackedChange = viewModel::setTracked,
            onEstimationChange = viewModel::setEstimationEnabled,
            onShareChange = viewModel::setShortFormShare,
            onSecondsChange = viewModel::setSecondsPerReel,
            onReset = viewModel::resetEstimation,
        ),
    )
}

internal class TrackingSettingsActions(
    val onBack: () -> Unit = {},
    val onSetUpTracking: () -> Unit = {},
    val onOpenUsageAccess: () -> Unit = {},
    val onOpenAccessibility: () -> Unit = {},
    val onPausedChange: (Boolean) -> Unit = {},
    val onTrackedChange: (String, Boolean) -> Unit = { _, _ -> },
    val onEstimationChange: (String, Boolean) -> Unit = { _, _ -> },
    val onShareChange: (String, Double) -> Unit = { _, _ -> },
    val onSecondsChange: (String, Double) -> Unit = { _, _ -> },
    val onReset: (String) -> Unit = {},
)

@Composable
internal fun TrackingSettingsScreen(uiState: TrackingSettingsUiState, actions: TrackingSettingsActions) {
    Scaffold(
        topBar = {
            RcTopAppBar(
                title = stringResource(R.string.feature_tracking_settings_title),
                navigationIcon = RcIcons.Back,
                navigationIconContentDescription = stringResource(R.string.feature_tracking_back),
                onNavigationClick = actions.onBack,
            )
        },
    ) { innerPadding ->
        if (uiState.isLoading) {
            RcLoadingIndicator(modifier = Modifier.padding(innerPadding).fillMaxWidth())
            return@Scaffold
        }
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(start = Spacing.screen, end = Spacing.screen, top = Spacing.sm, bottom = Spacing.xxl),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                StatusSection(uiState.status, actions)
                if (uiState.status == TrackingStatus.Active || uiState.status == TrackingStatus.Paused) {
                    CountingCard(uiState.countingEnabled, actions.onOpenAccessibility)
                }
                RcSectionHeader(
                    title = stringResource(R.string.feature_tracking_apps_heading),
                    modifier = Modifier.padding(top = Spacing.sm),
                )
                uiState.platforms.forEach { PlatformCard(it, actions) }
                Text(
                    text = stringResource(R.string.feature_tracking_assumptions_note),
                    modifier = Modifier.padding(horizontal = Spacing.xs),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Whether reels are counted one by one or estimated, with the way to turn counting on or off. */
@Composable
private fun CountingCard(enabled: Boolean, onOpenAccessibility: () -> Unit) {
    RcListCard(modifier = Modifier.fillMaxWidth()) {
        RcListRow(
            title = stringResource(if (enabled) R.string.feature_tracking_count_status_on else R.string.feature_tracking_count_status_off),
            summary = stringResource(
                if (enabled) R.string.feature_tracking_count_status_on_summary else R.string.feature_tracking_count_status_off_summary,
            ),
            leading = {
                RcIconTile(
                    icon = RcIcons.Reels,
                    containerColor = if (enabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                    contentColor = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            onClick = onOpenAccessibility,
            showChevron = enabled,
            trailing = if (enabled) {
                null
            } else {
                { RcTonalButton(text = stringResource(R.string.feature_tracking_count_turn_on), onClick = onOpenAccessibility) }
            },
        )
    }
}

/** Where tracking stands, with the one action that changes it. */
@Composable
private fun StatusSection(status: TrackingStatus, actions: TrackingSettingsActions) {
    when (status) {
        TrackingStatus.NeedsDisclosure -> RcBanner(
            title = stringResource(R.string.feature_tracking_status_title_setup),
            message = stringResource(R.string.feature_tracking_status_not_set_up),
            icon = RcIcons.Tracking,
            tone = RcTone.Accent,
            actionLabel = stringResource(R.string.feature_tracking_set_up),
            onAction = actions.onSetUpTracking,
        )
        TrackingStatus.NeedsUsageAccess -> RcBanner(
            title = stringResource(R.string.feature_tracking_status_title_no_access),
            message = stringResource(R.string.feature_tracking_status_no_access),
            icon = RcIcons.Error,
            tone = RcTone.Warning,
            actionLabel = stringResource(R.string.feature_tracking_open_settings),
            onAction = actions.onOpenUsageAccess,
        )
        TrackingStatus.Paused, TrackingStatus.Active -> {
            val paused = status == TrackingStatus.Paused
            RcListCard(modifier = Modifier.fillMaxWidth()) {
                RcSwitchRow(
                    title = stringResource(R.string.feature_tracking_pause),
                    summary = stringResource(
                        if (paused) R.string.feature_tracking_paused_summary else R.string.feature_tracking_active_summary,
                    ),
                    icon = if (paused) RcIcons.Paused else RcIcons.Tracking,
                    checked = paused,
                    onCheckedChange = actions.onPausedChange,
                )
            }
        }
    }
}

@Composable
private fun PlatformCard(config: PlatformConfiguration, actions: TrackingSettingsActions) {
    val id = config.platform.id
    RcListCard(modifier = Modifier.fillMaxWidth()) {
        RcSwitchRow(
            title = config.platform.displayName,
            summary = if (config.isInstalled) null else stringResource(R.string.feature_tracking_not_installed),
            checked = config.isTracked,
            onCheckedChange = { actions.onTrackedChange(id, it) },
            leading = { PlatformLogo(platform = config.platform) },
        )
        if (!config.isTracked) return@RcListCard
        RcDivider(startInset = 68.dp)
        RcSwitchRow(
            title = stringResource(R.string.feature_tracking_estimate_title),
            summary = stringResource(
                if (config.estimationEnabled) R.string.feature_tracking_estimate_on else R.string.feature_tracking_estimate_off,
            ),
            icon = RcIcons.Estimated,
            checked = config.estimationEnabled,
            onCheckedChange = { actions.onEstimationChange(id, it) },
        )
        if (!config.estimationEnabled) return@RcListCard

        val share = config.shortFormShareOverride ?: config.defaults.shortFormShare
        val seconds = config.secondsPerReelOverride ?: config.defaults.secondsPerReel
        val adjusted = config.shortFormShareOverride != null || config.secondsPerReelOverride != null
        RcDivider(startInset = 68.dp)
        Column(
            modifier = Modifier.padding(start = Spacing.lg, end = Spacing.lg, top = Spacing.md, bottom = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.feature_tracking_estimates_heading),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                )
                if (adjusted) {
                    RcStatusPill(text = stringResource(com.reelcounter.core.ui.R.string.core_ui_metric_user_adjusted), tone = RcTone.Accent)
                }
            }
            ParameterSlider(
                label = { stringResource(R.string.feature_tracking_share_label, (it * 100).roundToInt()) },
                value = share.toFloat(),
                range = 0.05f..1f,
                steps = 18,
                onValueChangeFinished = { actions.onShareChange(id, it.toDouble()) },
            )
            ParameterSlider(
                label = { stringResource(R.string.feature_tracking_seconds_label, it.roundToInt()) },
                value = seconds.toFloat(),
                range = 5f..90f,
                steps = 84,
                onValueChangeFinished = { actions.onSecondsChange(id, it.roundToInt().toDouble()) },
            )
            Text(
                text = stringResource(
                    R.string.feature_tracking_defaults,
                    (config.defaults.shortFormShare * 100).roundToInt(),
                    config.defaults.secondsPerReel.roundToInt(),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (adjusted) {
                RcTextButton(
                    text = stringResource(R.string.feature_tracking_reset),
                    onClick = { actions.onReset(id) },
                    leadingIcon = RcIcons.Retry,
                )
            }
        }
    }
}

/**
 * Saves only when the user lets go, so dragging doesn't recompute history on every frame. The
 * label follows the thumb while dragging, so the user sees the value they're about to set.
 */
@Composable
private fun ParameterSlider(
    label: @Composable (Float) -> String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChangeFinished: (Float) -> Unit,
) {
    var dragging by remember(value) { mutableFloatStateOf(value) }
    Column {
        Text(text = label(dragging), style = MaterialTheme.typography.bodyMedium)
        Slider(
            value = dragging,
            onValueChange = { dragging = it },
            onValueChangeFinished = { onValueChangeFinished(dragging) },
            valueRange = range,
            steps = steps,
            colors = SliderDefaults.colors(
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                // Dozens of steps would draw a dense row of dots; the label shows the value instead.
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent,
            ),
        )
    }
}
