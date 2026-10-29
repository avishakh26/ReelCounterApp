package com.reelcounter.feature.privacy

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.reelcounter.core.designsystem.component.ErrorState
import com.reelcounter.core.designsystem.component.RcDivider
import com.reelcounter.core.designsystem.component.RcListCard
import com.reelcounter.core.designsystem.component.RcListRow
import com.reelcounter.core.designsystem.component.RcLoadingIndicator
import com.reelcounter.core.designsystem.component.RcRadioRow
import com.reelcounter.core.designsystem.component.RcSectionHeader
import com.reelcounter.core.designsystem.component.RcSwitchRow
import com.reelcounter.core.designsystem.component.RcTextButton
import com.reelcounter.core.designsystem.component.RcTopAppBar
import com.reelcounter.core.designsystem.icon.RcIcons
import com.reelcounter.core.designsystem.theme.Spacing
import com.reelcounter.core.domain.social.PrivacyState
import com.reelcounter.core.domain.social.SocialError
import com.reelcounter.core.model.PrivacySettings
import com.reelcounter.core.model.Visibility
import com.reelcounter.core.ui.profile.Avatar

@Composable
internal fun PrivacyScreen(
    onBack: () -> Unit,
    viewModel: PrivacyViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    PrivacyScreen(
        uiState = uiState,
        actions = PrivacyActions(
            onBack = onBack,
            onVisibilityChange = viewModel::setActivityVisibility,
            onShareOnlineStatusChange = viewModel::setShareOnlineStatus,
            onDiscoverableChange = viewModel::setDiscoverable,
            onAcceptRequestsChange = viewModel::setAcceptFriendRequests,
            onUnblock = viewModel::unblock,
            onRetry = viewModel::retry,
            onErrorShown = viewModel::onErrorShown,
        ),
    )
}

internal class PrivacyActions(
    val onBack: () -> Unit = {},
    val onVisibilityChange: (Visibility) -> Unit = {},
    val onShareOnlineStatusChange: (Boolean) -> Unit = {},
    val onDiscoverableChange: (Boolean) -> Unit = {},
    val onAcceptRequestsChange: (Boolean) -> Unit = {},
    val onUnblock: (String) -> Unit = {},
    val onRetry: () -> Unit = {},
    val onErrorShown: () -> Unit = {},
)

@Composable
internal fun PrivacyScreen(uiState: PrivacyUiState, actions: PrivacyActions) {
    val snackbar = remember { SnackbarHostState() }
    val errorText = uiState.error?.let { stringResource(it.saveErrorText()) }
    LaunchedEffect(uiState.error) {
        if (errorText != null) {
            snackbar.showSnackbar(errorText)
            actions.onErrorShown()
        }
    }

    Scaffold(
        topBar = {
            RcTopAppBar(
                title = stringResource(R.string.feature_privacy_title),
                navigationIcon = RcIcons.Back,
                navigationIconContentDescription = stringResource(R.string.feature_privacy_back),
                onNavigationClick = actions.onBack,
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { innerPadding ->
        val settings = when (val state = uiState.settings) {
            PrivacyState.Loading -> {
                RcLoadingIndicator(modifier = Modifier.padding(innerPadding).fillMaxWidth())
                return@Scaffold
            }
            is PrivacyState.Failed -> {
                ErrorState(
                    title = stringResource(R.string.feature_privacy_load_failed),
                    message = stringResource(state.error.loadErrorText()),
                    retryLabel = stringResource(R.string.feature_privacy_retry),
                    onRetry = actions.onRetry,
                    modifier = Modifier.padding(innerPadding),
                )
                return@Scaffold
            }
            is PrivacyState.Loaded -> state.settings
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
                RcSectionHeader(title = stringResource(R.string.feature_privacy_activity_heading))
                ActivityVisibilityCard(settings.activityVisibility, actions.onVisibilityChange)

                RcSectionHeader(
                    title = stringResource(R.string.feature_privacy_social_heading),
                    modifier = Modifier.padding(top = Spacing.sm),
                )
                SocialCard(settings, actions)

                RcSectionHeader(
                    title = stringResource(R.string.feature_privacy_blocked_heading),
                    modifier = Modifier.padding(top = Spacing.sm),
                )
                BlockedCard(uiState.blocked, uiState.unblockingIds, actions)
            }
        }
    }
}

@Composable
private fun ActivityVisibilityCard(selected: Visibility, onSelect: (Visibility) -> Unit) {
    // "Everyone" isn't offered yet; it only appears if the account already has it, so the
    // current setting is never hidden.
    val options = buildList {
        add(Visibility.Friends)
        add(Visibility.Private)
        if (selected == Visibility.Public) add(Visibility.Public)
    }
    RcListCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.selectableGroup()) {
            options.forEachIndexed { index, option ->
                if (index > 0) RcDivider()
                RcRadioRow(
                    title = stringResource(option.titleText()),
                    summary = stringResource(option.summaryText()),
                    selected = option == selected,
                    onClick = { onSelect(option) },
                )
            }
        }
    }
    Text(
        text = stringResource(R.string.feature_privacy_activity_note),
        modifier = Modifier.padding(horizontal = Spacing.xs),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun SocialCard(settings: PrivacySettings, actions: PrivacyActions) {
    RcListCard(modifier = Modifier.fillMaxWidth()) {
        RcSwitchRow(
            title = stringResource(R.string.feature_privacy_online),
            summary = stringResource(R.string.feature_privacy_online_summary),
            icon = RcIcons.Visible,
            checked = settings.shareOnlineStatus,
            onCheckedChange = actions.onShareOnlineStatusChange,
        )
        RcDivider(startInset = 68.dp)
        RcSwitchRow(
            title = stringResource(R.string.feature_privacy_discoverable),
            summary = stringResource(R.string.feature_privacy_discoverable_summary),
            icon = RcIcons.Search,
            checked = settings.discoverable,
            onCheckedChange = actions.onDiscoverableChange,
        )
        RcDivider(startInset = 68.dp)
        RcSwitchRow(
            title = stringResource(R.string.feature_privacy_requests),
            summary = stringResource(R.string.feature_privacy_requests_summary),
            icon = RcIcons.AddFriend,
            checked = settings.acceptFriendRequests,
            onCheckedChange = actions.onAcceptRequestsChange,
        )
    }
}

@Composable
private fun BlockedCard(blocked: BlockedState, unblockingIds: Set<String>, actions: PrivacyActions) {
    RcListCard(modifier = Modifier.fillMaxWidth()) {
        when (blocked) {
            BlockedState.Loading -> RcLoadingIndicator(modifier = Modifier.fillMaxWidth())
            is BlockedState.Failed -> RcListRow(
                title = stringResource(blocked.error.loadErrorText()),
                icon = RcIcons.Error,
                showChevron = false,
                trailing = { RcTextButton(text = stringResource(R.string.feature_privacy_retry), onClick = actions.onRetry) },
            )
            is BlockedState.Loaded -> if (blocked.users.isEmpty()) {
                RcListRow(
                    title = stringResource(R.string.feature_privacy_blocked_empty),
                    icon = RcIcons.Shield,
                    showChevron = false,
                )
            } else {
                blocked.users.forEachIndexed { index, user ->
                    if (index > 0) RcDivider(startInset = 72.dp)
                    RcListRow(
                        title = user.nameForDisplay,
                        summary = "@${user.username}",
                        showChevron = false,
                        leading = { Avatar(image = user.avatarUrl, name = user.nameForDisplay, size = 40.dp) },
                        trailing = {
                            RcTextButton(
                                text = stringResource(R.string.feature_privacy_unblock),
                                onClick = { actions.onUnblock(user.id) },
                                enabled = user.id !in unblockingIds,
                            )
                        },
                    )
                }
            }
        }
    }
}

private fun Visibility.titleText(): Int = when (this) {
    Visibility.Friends -> R.string.feature_privacy_activity_friends
    Visibility.Private -> R.string.feature_privacy_activity_private
    Visibility.Public -> R.string.feature_privacy_activity_public
}

private fun Visibility.summaryText(): Int = when (this) {
    Visibility.Friends -> R.string.feature_privacy_activity_friends_summary
    Visibility.Private -> R.string.feature_privacy_activity_private_summary
    Visibility.Public -> R.string.feature_privacy_activity_public_summary
}

private fun SocialError.loadErrorText(): Int = when (this) {
    SocialError.Network -> R.string.feature_privacy_error_offline
    else -> R.string.feature_privacy_error_unknown
}

private fun SocialError.saveErrorText(): Int = when (this) {
    SocialError.Network -> R.string.feature_privacy_save_failed_offline
    else -> R.string.feature_privacy_save_failed
}
