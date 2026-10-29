package com.reelcounter.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.reelcounter.app.AppState
import com.reelcounter.app.R
import com.reelcounter.core.common.network.NetworkMonitor
import com.reelcounter.core.designsystem.component.EmptyState
import com.reelcounter.core.designsystem.component.ErrorState
import com.reelcounter.core.designsystem.component.RcLoadingIndicator
import com.reelcounter.core.designsystem.component.RcTextButton
import com.reelcounter.core.designsystem.icon.RcIcons
import com.reelcounter.core.designsystem.theme.Spacing
import com.reelcounter.core.domain.profile.ProfileError
import com.reelcounter.feature.auth.AuthFlow
import com.reelcounter.feature.auth.reset.SetNewPasswordScreen
import com.reelcounter.feature.profile.ProfileSetupScreen

/**
 * Picks the top-level experience from [appState]. Each branch is a separate composition, so
 * signing in or out discards the previous flow's back stack instead of leaving screens behind.
 */
@Composable
fun ReelCounterRoot(
    appState: AppState,
    networkMonitor: NetworkMonitor,
    onRetryProfile: () -> Unit,
    onSignOut: () -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize()) {
        when (appState) {
            AppState.Loading -> RcLoadingIndicator(modifier = Modifier.fillMaxSize())
            AppState.NotConfigured -> EmptyState(
                title = stringResource(R.string.not_configured_title),
                message = stringResource(R.string.not_configured_message),
                icon = RcIcons.Info,
                modifier = Modifier.fillMaxSize(),
            )
            AppState.SignedOut -> AuthFlow()
            AppState.SettingNewPassword -> SetNewPasswordScreen()
            AppState.NeedsProfile -> ProfileSetupScreen()
            is AppState.ProfileUnavailable -> ProfileUnavailable(appState.error, onRetryProfile, onSignOut)
            AppState.Ready -> ReelCounterApp(appState = rememberReelCounterAppState(networkMonitor = networkMonitor))
        }
    }
}

@Composable
private fun ProfileUnavailable(error: ProfileError, onRetry: () -> Unit, onSignOut: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ErrorState(
            title = stringResource(R.string.profile_unavailable_title),
            message = stringResource(
                if (error == ProfileError.Network) R.string.profile_unavailable_offline else R.string.profile_unavailable_message,
            ),
            retryLabel = stringResource(R.string.retry),
            onRetry = onRetry,
        )
        RcTextButton(text = stringResource(R.string.sign_out), onClick = onSignOut)
    }
}
