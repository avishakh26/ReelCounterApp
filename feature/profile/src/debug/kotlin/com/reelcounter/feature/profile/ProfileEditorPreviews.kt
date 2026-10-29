package com.reelcounter.feature.profile

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewFontScale
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.reelcounter.core.domain.profile.UsernameIssue
import com.reelcounter.core.preview.PreviewTheme

@PreviewLightDark
@Composable
private fun ProfileSetupPreview() = PreviewTheme {
    ProfileEditorScreen(
        mode = ProfileEditorMode.Setup,
        uiState = ProfileEditorUiState(isLoading = false, username = "sam.k", usernameStatus = UsernameStatus.Available),
        onUsernameChange = {}, onDisplayNameChange = {}, onBioChange = {}, onChangePhoto = {}, onSave = {}, onBack = {}, onSignOut = {},
    )
}

@PreviewLightDark
@PreviewFontScale
@Composable
private fun EditProfileTakenPreview() = PreviewTheme {
    ProfileEditorScreen(
        mode = ProfileEditorMode.Edit,
        uiState = ProfileEditorUiState(
            isLoading = false,
            username = "sam",
            usernameStatus = UsernameStatus.Taken,
            displayName = "Sam Kim",
            bio = "Trying to watch fewer reels before bed.",
        ),
        onUsernameChange = {}, onDisplayNameChange = {}, onBioChange = {}, onChangePhoto = {}, onSave = {}, onBack = {}, onSignOut = {},
    )
}

@PreviewLightDark
@Composable
private fun EditProfileInvalidPreview() = PreviewTheme {
    ProfileEditorScreen(
        mode = ProfileEditorMode.Edit,
        uiState = ProfileEditorUiState(
            isLoading = false,
            username = "sam..k",
            usernameStatus = UsernameStatus.Invalid(UsernameIssue.ConsecutivePeriods),
        ),
        onUsernameChange = {}, onDisplayNameChange = {}, onBioChange = {}, onChangePhoto = {}, onSave = {}, onBack = {}, onSignOut = {},
    )
}
