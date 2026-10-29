package com.reelcounter.feature.privacy

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.reelcounter.core.domain.social.PrivacyState
import com.reelcounter.core.model.PrivacySettings
import com.reelcounter.core.model.UserSummary
import com.reelcounter.core.model.Visibility
import com.reelcounter.core.preview.PreviewTheme

private val defaults = PrivacySettings(
    activityVisibility = Visibility.Friends,
    shareOnlineStatus = false,
    discoverable = true,
    acceptFriendRequests = true,
)

@PreviewLightDark
@Composable
private fun PrivacyScreenPreview() = PreviewTheme {
    PrivacyScreen(
        uiState = PrivacyUiState(
            settings = PrivacyState.Loaded(defaults),
            blocked = BlockedState.Loaded(listOf(UserSummary(id = "1", username = "spam.bot", displayName = null, avatarUrl = null))),
        ),
        actions = PrivacyActions(),
    )
}

@PreviewLightDark
@Composable
private fun PrivacyScreenPrivatePreview() = PreviewTheme {
    PrivacyScreen(
        uiState = PrivacyUiState(
            settings = PrivacyState.Loaded(defaults.copy(activityVisibility = Visibility.Private, shareOnlineStatus = true)),
            blocked = BlockedState.Loaded(emptyList()),
        ),
        actions = PrivacyActions(),
    )
}
