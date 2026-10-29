package com.reelcounter.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.reelcounter.core.designsystem.component.RcAlertDialog
import com.reelcounter.core.designsystem.component.RcCard
import com.reelcounter.core.designsystem.component.RcListCard
import com.reelcounter.core.designsystem.component.RcListRow
import com.reelcounter.core.designsystem.component.RcScreenHeader
import com.reelcounter.core.designsystem.component.RcSectionHeader
import com.reelcounter.core.designsystem.icon.RcIcons
import com.reelcounter.core.designsystem.theme.Spacing
import com.reelcounter.core.domain.auth.AuthError
import com.reelcounter.core.model.UserProfile
import com.reelcounter.core.ui.profile.Avatar

@Composable
internal fun SettingsScreen(
    onEditProfileClick: () -> Unit,
    onTrackingClick: () -> Unit,
    onPrivacyClick: () -> Unit,
    onHowWeCountClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val deletion by viewModel.deletion.collectAsStateWithLifecycle()
    SettingsScreen(
        profile = profile,
        deletion = deletion,
        onDeleteAccount = viewModel::deleteAccount,
        onDismissDeletionError = viewModel::dismissDeletionError,
        onEditProfileClick = onEditProfileClick,
        onTrackingClick = onTrackingClick,
        onPrivacyClick = onPrivacyClick,
        onHowWeCountClick = onHowWeCountClick,
        onSignOut = viewModel::signOut,
        modifier = modifier,
    )
}

// Data export joins this list in Phase 8.
@Composable
internal fun SettingsScreen(
    profile: UserProfile?,
    deletion: AccountDeletion,
    onDeleteAccount: () -> Unit,
    onDismissDeletionError: () -> Unit,
    onEditProfileClick: () -> Unit,
    onTrackingClick: () -> Unit,
    onPrivacyClick: () -> Unit,
    onHowWeCountClick: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmSignOut by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    Scaffold(modifier = modifier) { innerPadding ->
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
                    .padding(start = Spacing.screen, end = Spacing.screen, bottom = Spacing.xxl),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                RcScreenHeader(title = stringResource(R.string.feature_settings_title))

                if (profile != null) {
                    AccountCard(profile = profile, onClick = onEditProfileClick)
                }

                Section(stringResource(R.string.feature_settings_section_tracking)) {
                    RcListRow(
                        title = stringResource(R.string.feature_settings_tracking),
                        summary = stringResource(R.string.feature_settings_tracking_summary),
                        icon = RcIcons.Tracking,
                        onClick = onTrackingClick,
                    )
                }

                Section(stringResource(R.string.feature_settings_section_privacy)) {
                    RcListRow(
                        title = stringResource(R.string.feature_settings_privacy),
                        summary = stringResource(R.string.feature_settings_privacy_summary),
                        icon = RcIcons.Privacy,
                        onClick = onPrivacyClick,
                    )
                }

                Section(stringResource(R.string.feature_settings_section_about_numbers)) {
                    RcListRow(
                        title = stringResource(R.string.feature_settings_how_we_count),
                        summary = stringResource(R.string.feature_settings_how_we_count_summary),
                        icon = RcIcons.Info,
                        onClick = onHowWeCountClick,
                    )
                }

                Section(stringResource(R.string.feature_settings_section_account)) {
                    RcListRow(
                        title = stringResource(R.string.feature_settings_sign_out),
                        icon = RcIcons.SignOut,
                        isDestructive = true,
                        showChevron = false,
                        onClick = { confirmSignOut = true },
                    )
                    RcListRow(
                        title = stringResource(
                            if (deletion == AccountDeletion.Deleting) R.string.feature_settings_deleting else R.string.feature_settings_delete_account,
                        ),
                        icon = RcIcons.Delete,
                        isDestructive = true,
                        showChevron = false,
                        enabled = deletion != AccountDeletion.Deleting,
                        onClick = { confirmDelete = true },
                    )
                }
            }
        }
    }

    if (confirmSignOut) {
        RcAlertDialog(
            title = stringResource(R.string.feature_settings_sign_out_title),
            text = stringResource(R.string.feature_settings_sign_out_message),
            confirmText = stringResource(R.string.feature_settings_sign_out),
            dismissText = stringResource(R.string.feature_settings_cancel),
            icon = RcIcons.SignOut,
            onConfirm = {
                confirmSignOut = false
                onSignOut()
            },
            onDismiss = { confirmSignOut = false },
        )
    }

    if (confirmDelete) {
        RcAlertDialog(
            title = stringResource(R.string.feature_settings_delete_title),
            text = stringResource(R.string.feature_settings_delete_message),
            confirmText = stringResource(R.string.feature_settings_delete_confirm),
            dismissText = stringResource(R.string.feature_settings_cancel),
            icon = RcIcons.Delete,
            isDestructive = true,
            onConfirm = {
                confirmDelete = false
                onDeleteAccount()
            },
            onDismiss = { confirmDelete = false },
        )
    }

    if (deletion is AccountDeletion.Failed) {
        RcAlertDialog(
            title = stringResource(R.string.feature_settings_delete_failed_title),
            text = stringResource(
                if (deletion.error == AuthError.Network) R.string.feature_settings_delete_failed_offline else R.string.feature_settings_delete_failed,
            ),
            confirmText = stringResource(R.string.feature_settings_ok),
            onConfirm = onDismissDeletionError,
            onDismiss = onDismissDeletionError,
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        RcSectionHeader(title = title, modifier = Modifier.padding(top = Spacing.sm))
        RcListCard(modifier = Modifier.fillMaxWidth()) { content() }
    }
}

/** The signed-in account, with the way into profile editing. */
@Composable
private fun AccountCard(profile: UserProfile, onClick: () -> Unit) {
    val name = profile.displayName ?: profile.username.orEmpty()
    RcCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Spacing.sm),
        onClick = onClick,
        contentPadding = PaddingValues(Spacing.lg),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.lg), verticalAlignment = Alignment.CenterVertically) {
            Avatar(image = profile.avatarUrl, name = name, size = 56.dp)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                profile.username?.let {
                    Text(
                        text = "@$it",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Text(
                text = stringResource(R.string.feature_settings_edit_profile),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Icon(
                imageVector = RcIcons.ChevronForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
