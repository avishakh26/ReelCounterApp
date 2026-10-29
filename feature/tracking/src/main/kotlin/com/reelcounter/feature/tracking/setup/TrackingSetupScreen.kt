package com.reelcounter.feature.tracking.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.reelcounter.core.designsystem.component.RcBanner
import com.reelcounter.core.designsystem.component.RcButton
import com.reelcounter.core.designsystem.component.RcCard
import com.reelcounter.core.designsystem.component.RcDivider
import com.reelcounter.core.designsystem.component.RcIconTile
import com.reelcounter.core.designsystem.component.RcListCard
import com.reelcounter.core.designsystem.component.RcLoadingIndicator
import com.reelcounter.core.designsystem.component.RcTextButton
import com.reelcounter.core.designsystem.component.RcTone
import com.reelcounter.core.designsystem.component.RcTopAppBar
import com.reelcounter.core.designsystem.icon.RcIcons
import com.reelcounter.core.designsystem.theme.Spacing
import com.reelcounter.core.ui.metric.PlatformLogo
import com.reelcounter.feature.tracking.R
import com.reelcounter.feature.tracking.openAccessibilitySettings
import com.reelcounter.feature.tracking.openAppDetailsSettings
import com.reelcounter.feature.tracking.openUsageAccessSettings

@Composable
internal fun TrackingSetupScreen(
    onFinished: () -> Unit,
    viewModel: TrackingSetupViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(uiState.step) {
        if (uiState.step == SetupStep.Done) onFinished()
    }

    TrackingSetupScreen(
        uiState = uiState,
        onAcceptDisclosure = viewModel::acceptDisclosure,
        onDecline = onFinished,
        onToggle = viewModel::toggle,
        onConfirmApps = viewModel::confirmApps,
        onOpenSettings = { context.openUsageAccessSettings() },
        onOpenAccessibility = { context.openAccessibilitySettings() },
        onOpenAppInfo = { context.openAppDetailsSettings() },
        onSkipCounting = viewModel::skipCounting,
    )
}

@Composable
internal fun TrackingSetupScreen(
    uiState: TrackingSetupUiState,
    onAcceptDisclosure: () -> Unit,
    onDecline: () -> Unit,
    onToggle: (String) -> Unit,
    onConfirmApps: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAccessibility: () -> Unit = {},
    onOpenAppInfo: () -> Unit = {},
    onSkipCounting: () -> Unit = {},
) {
    Scaffold(
        topBar = {
            RcTopAppBar(
                title = stringResource(R.string.feature_tracking_setup_title),
                navigationIcon = RcIcons.Back,
                navigationIconContentDescription = stringResource(R.string.feature_tracking_back),
                onNavigationClick = onDecline,
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .fillMaxSize(),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.xl, vertical = Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.lg),
            ) {
                uiState.step?.let { step -> StepIndicator(step, uiState.countingAvailable) }
                when (uiState.step) {
                    null, SetupStep.Done -> RcLoadingIndicator(modifier = Modifier.fillMaxWidth())
                    SetupStep.Disclosure -> DisclosureStep(uiState.isWorking, uiState.countingAvailable, onAcceptDisclosure, onDecline)
                    SetupStep.ChooseApps -> ChooseAppsStep(uiState, onToggle, onConfirmApps)
                    SetupStep.GrantAccess -> GrantAccessStep(uiState.isWorking, onOpenSettings)
                    SetupStep.CountReels -> CountReelsStep(onOpenAccessibility, onOpenAppInfo, onSkipCounting)
                }
            }
        }
    }
}

/**
 * The prominent disclosure. Shown before usage access is requested, describes exactly what is
 * collected and how it's used, and needs an affirmative tap. Changing what's collected means
 * updating this text AND bumping CURRENT_DISCLOSURE_VERSION, so everyone sees it again.
 */
@Composable
private fun ColumnScope.DisclosureStep(
    isWorking: Boolean,
    countingAvailable: Boolean,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
) {
    StepHeading(stringResource(R.string.feature_tracking_disclosure_title))
    Text(
        text = stringResource(R.string.feature_tracking_disclosure_intro),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    RcCard(modifier = Modifier.fillMaxWidth(), mergeSemantics = false) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
            listOfNotNull(
                RcIcons.AppTime to R.string.feature_tracking_disclosure_collects,
                RcIcons.Unavailable to R.string.feature_tracking_disclosure_not_collected,
                RcIcons.Block to R.string.feature_tracking_disclosure_other_apps,
                // Only builds that include the accessibility service can count reels.
                (RcIcons.Reels to R.string.feature_tracking_disclosure_counting).takeIf { countingAvailable },
                RcIcons.Synced to R.string.feature_tracking_disclosure_storage,
                RcIcons.Friends to R.string.feature_tracking_disclosure_sharing,
                RcIcons.Estimated to R.string.feature_tracking_disclosure_estimates,
                RcIcons.Paused to R.string.feature_tracking_disclosure_control,
            ).forEach { (icon, text) -> Point(icon, stringResource(text)) }
        }
    }
    RcButton(
        text = stringResource(R.string.feature_tracking_disclosure_accept),
        onClick = onAccept,
        loading = isWorking,
        modifier = Modifier.fillMaxWidth(),
    )
    RcTextButton(
        text = stringResource(R.string.feature_tracking_not_now),
        onClick = onDecline,
        enabled = !isWorking,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ColumnScope.ChooseAppsStep(uiState: TrackingSetupUiState, onToggle: (String) -> Unit, onConfirm: () -> Unit) {
    StepHeading(stringResource(R.string.feature_tracking_choose_title))
    Text(
        text = stringResource(R.string.feature_tracking_choose_body),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    RcListCard(modifier = Modifier.fillMaxWidth()) {
        uiState.choices.forEachIndexed { index, choice ->
            if (index > 0) RcDivider(startInset = 68.dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(
                        value = choice.selected,
                        enabled = !uiState.isWorking,
                        role = Role.Checkbox,
                        onValueChange = { onToggle(choice.platform.id) },
                    )
                    .padding(horizontal = Spacing.lg, vertical = Spacing.md),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PlatformLogo(platform = choice.platform)
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = choice.platform.displayName, style = MaterialTheme.typography.titleMedium)
                    if (!choice.isInstalled) {
                        Text(
                            text = stringResource(R.string.feature_tracking_not_installed),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Checkbox(checked = choice.selected, onCheckedChange = null)
            }
        }
    }
    RcButton(
        text = stringResource(R.string.feature_tracking_continue),
        onClick = onConfirm,
        enabled = uiState.canContinueFromApps,
        loading = uiState.isWorking,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ColumnScope.GrantAccessStep(isWorking: Boolean, onOpenSettings: () -> Unit) {
    StepHeading(stringResource(R.string.feature_tracking_access_title))
    Text(
        text = stringResource(R.string.feature_tracking_access_body),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    RcCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
            listOf(
                R.string.feature_tracking_access_step_1,
                R.string.feature_tracking_access_step_2,
                R.string.feature_tracking_access_step_3,
            ).forEachIndexed { index, step ->
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
                    StepNumber(index + 1)
                    Text(text = stringResource(step), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
    RcButton(
        text = stringResource(R.string.feature_tracking_open_settings),
        onClick = onOpenSettings,
        loading = isWorking,
        modifier = Modifier.fillMaxWidth(),
    )
    Text(
        text = stringResource(R.string.feature_tracking_access_auto_continue),
        modifier = Modifier.fillMaxWidth(),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

/**
 * Optional: turn on the reel-counting accessibility service for exact counts. Explains what it can
 * and can't see, and the extra "restricted settings" step Android 13+ needs for sideloaded apps.
 */
@Composable
private fun ColumnScope.CountReelsStep(onOpenAccessibility: () -> Unit, onOpenAppInfo: () -> Unit, onSkip: () -> Unit) {
    StepHeading(stringResource(R.string.feature_tracking_count_title))
    Text(
        text = stringResource(R.string.feature_tracking_count_body),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    RcCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
            listOf(
                R.string.feature_tracking_count_step_1,
                R.string.feature_tracking_count_step_2,
                R.string.feature_tracking_count_step_3,
            ).forEachIndexed { index, step ->
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
                    StepNumber(index + 1)
                    Text(text = stringResource(step), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
    RcBanner(
        title = stringResource(R.string.feature_tracking_count_restricted_title),
        message = stringResource(R.string.feature_tracking_count_restricted_body),
        icon = RcIcons.Info,
        tone = RcTone.Neutral,
        actionLabel = stringResource(R.string.feature_tracking_count_app_info),
        onAction = onOpenAppInfo,
    )
    Point(RcIcons.Privacy, stringResource(R.string.feature_tracking_count_privacy))
    RcButton(
        text = stringResource(R.string.feature_tracking_count_open),
        onClick = onOpenAccessibility,
        modifier = Modifier.fillMaxWidth(),
    )
    RcTextButton(
        text = stringResource(R.string.feature_tracking_count_skip),
        onClick = onSkip,
        modifier = Modifier.fillMaxWidth(),
    )
    Text(
        text = stringResource(R.string.feature_tracking_access_auto_continue),
        modifier = Modifier.fillMaxWidth(),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

/** "Step 1 of 4" with a segmented progress line. */
@Composable
private fun StepIndicator(step: SetupStep, countingAvailable: Boolean) {
    val steps = listOfNotNull(
        SetupStep.Disclosure,
        SetupStep.ChooseApps,
        SetupStep.GrantAccess,
        SetupStep.CountReels.takeIf { countingAvailable },
    )
    val current = steps.indexOf(step)
    if (current < 0) return
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(
            text = stringResource(R.string.feature_tracking_step, current + 1, steps.size),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            steps.indices.forEach { index ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(4.dp)
                        .background(
                            if (index <= current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                            RoundedCornerShape(2.dp),
                        ),
                )
            }
        }
    }
}

@Composable
private fun StepNumber(number: Int) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = number.toString(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun StepHeading(text: String) {
    Text(text = text, modifier = Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineSmall)
}

@Composable
private fun Point(icon: ImageVector, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
        RcIconTile(
            icon = icon,
            size = 32.dp,
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.primary,
        )
        Text(text = text, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
    }
}
