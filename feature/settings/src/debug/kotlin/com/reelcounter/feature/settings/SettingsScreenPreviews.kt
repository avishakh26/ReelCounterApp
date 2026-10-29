package com.reelcounter.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewFontScale
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.reelcounter.core.model.UserProfile
import com.reelcounter.core.preview.PreviewTheme

@PreviewLightDark
@Composable
private fun SettingsScreenPreview() = PreviewTheme {
    SettingsScreen(
        profile = UserProfile(id = "1", username = "sam.k", displayName = "Sam Kim", bio = null, avatarUrl = null),
        onEditProfileClick = {},
        onTrackingClick = {},
        onPrivacyClick = {},
        onHowWeCountClick = {},
        onSignOut = {},
    )
}

@PreviewLightDark
@PreviewFontScale
@Composable
private fun HowWeCountScreenPreview() = PreviewTheme {
    HowWeCountScreen(onBackClick = {})
}
