package com.reelcounter.feature.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.reelcounter.core.designsystem.component.RcBanner
import com.reelcounter.core.designsystem.component.RcButton
import com.reelcounter.core.designsystem.component.RcLoadingIndicator
import com.reelcounter.core.designsystem.component.RcTextButton
import com.reelcounter.core.designsystem.component.RcTextField
import com.reelcounter.core.designsystem.component.RcTone
import com.reelcounter.core.designsystem.component.RcTopAppBar
import com.reelcounter.core.designsystem.icon.RcIcons
import com.reelcounter.core.designsystem.theme.Spacing
import com.reelcounter.core.domain.profile.ProfileError
import com.reelcounter.core.domain.profile.ProfileField
import com.reelcounter.core.domain.profile.ProfileTextRules
import com.reelcounter.core.domain.profile.UsernameIssue
import com.reelcounter.core.domain.profile.UsernameRules
import com.reelcounter.core.ui.profile.Avatar

enum class ProfileEditorMode { Setup, Edit }

/** First-run profile setup, shown by the app until the user has a username. */
@Composable
fun ProfileSetupScreen(modifier: Modifier = Modifier) {
    ProfileEditorScreen(mode = ProfileEditorMode.Setup, onBack = {}, modifier = modifier)
}

@Composable
internal fun ProfileEditorScreen(
    mode: ProfileEditorMode,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProfileEditorViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // The photo picker needs no storage permission and only shares the photo the user picks.
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { viewModel.onAvatarPicked(it.toString()) }
    }

    LaunchedEffect(uiState.saved) {
        if (uiState.saved) {
            viewModel.onSavedHandled()
            if (mode == ProfileEditorMode.Edit) onBack()
        }
    }

    ProfileEditorScreen(
        mode = mode,
        uiState = uiState,
        onUsernameChange = viewModel::onUsernameChange,
        onDisplayNameChange = viewModel::onDisplayNameChange,
        onBioChange = viewModel::onBioChange,
        onChangePhoto = {
            pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        },
        onSave = viewModel::save,
        onBack = onBack,
        onSignOut = viewModel::signOut,
        modifier = modifier,
    )
}

@Composable
internal fun ProfileEditorScreen(
    mode: ProfileEditorMode,
    uiState: ProfileEditorUiState,
    onUsernameChange: (String) -> Unit,
    onDisplayNameChange: (String) -> Unit,
    onBioChange: (String) -> Unit,
    onChangePhoto: () -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            when (mode) {
                ProfileEditorMode.Setup -> RcTopAppBar(
                    title = stringResource(R.string.feature_profile_setup_title),
                    actions = { RcTextButton(text = stringResource(R.string.feature_profile_sign_out), onClick = onSignOut) },
                )
                ProfileEditorMode.Edit -> RcTopAppBar(
                    title = stringResource(R.string.feature_profile_edit_title),
                    navigationIcon = RcIcons.Back,
                    navigationIconContentDescription = stringResource(R.string.feature_profile_back),
                    onNavigationClick = onBack,
                )
            }
        },
    ) { innerPadding ->
        if (uiState.isLoading) {
            RcLoadingIndicator(modifier = Modifier.padding(innerPadding).fillMaxSize())
            return@Scaffold
        }
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .imePadding()
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
                if (mode == ProfileEditorMode.Setup) {
                    Text(
                        text = stringResource(R.string.feature_profile_setup_intro),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                AvatarPicker(uiState = uiState, onChangePhoto = onChangePhoto)
                RcTextField(
                    value = uiState.username,
                    onValueChange = onUsernameChange,
                    label = stringResource(R.string.feature_profile_username),
                    prefix = "@",
                    supportingText = usernameSupportingText(uiState.usernameStatus),
                    errorText = usernameErrorText(uiState.usernameStatus),
                    enabled = !uiState.isSaving,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        autoCorrectEnabled = false,
                        keyboardType = KeyboardType.Ascii,
                        imeAction = ImeAction.Next,
                    ),
                )
                RcTextField(
                    value = uiState.displayName,
                    onValueChange = onDisplayNameChange,
                    label = stringResource(R.string.feature_profile_display_name),
                    supportingText = stringResource(R.string.feature_profile_optional),
                    maxLength = ProfileTextRules.DISPLAY_NAME_MAX,
                    enabled = !uiState.isSaving,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                )
                RcTextField(
                    value = uiState.bio,
                    onValueChange = onBioChange,
                    label = stringResource(R.string.feature_profile_bio),
                    supportingText = stringResource(R.string.feature_profile_optional),
                    maxLength = ProfileTextRules.BIO_MAX,
                    singleLine = false,
                    minLines = 3,
                    enabled = !uiState.isSaving,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )
                uiState.error?.let { error ->
                    RcBanner(message = stringResource(error.messageRes()), icon = RcIcons.Error, tone = RcTone.Critical)
                }
                RcButton(
                    text = stringResource(
                        if (mode == ProfileEditorMode.Setup) R.string.feature_profile_continue else R.string.feature_profile_save,
                    ),
                    onClick = onSave,
                    enabled = uiState.canSave,
                    loading = uiState.isSaving,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun AvatarPicker(uiState: ProfileEditorUiState, onChangePhoto: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        val canChange = !uiState.isSaving && !uiState.isProcessingAvatar
        Box(
            modifier = Modifier.clickable(enabled = canChange, role = Role.Button, onClick = onChangePhoto),
            contentAlignment = Alignment.Center,
        ) {
            Avatar(
                image = uiState.avatar,
                name = uiState.displayName.ifBlank { uiState.username },
                size = 104.dp,
            )
            if (uiState.isProcessingAvatar) CircularProgressIndicator(modifier = Modifier.size(104.dp))
            // Camera badge: shows the photo can be changed. The button below says it in words.
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(34.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                border = BorderStroke(3.dp, MaterialTheme.colorScheme.background),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(imageVector = RcIcons.ChangePhoto, contentDescription = null, modifier = Modifier.size(16.dp))
                }
            }
        }
        RcTextButton(
            text = stringResource(R.string.feature_profile_change_photo),
            onClick = onChangePhoto,
            enabled = canChange,
        )
    }
}

@Composable
private fun usernameSupportingText(status: UsernameStatus): String = when (status) {
    UsernameStatus.Checking -> stringResource(R.string.feature_profile_username_checking)
    UsernameStatus.Available -> stringResource(R.string.feature_profile_username_available)
    UsernameStatus.CheckFailed -> stringResource(R.string.feature_profile_username_check_failed)
    // While typing the first characters, "too short" is a hint rather than an error.
    is UsernameStatus.Invalid, UsernameStatus.Empty, UsernameStatus.Unchanged, UsernameStatus.Taken, UsernameStatus.Reserved ->
        stringResource(R.string.feature_profile_username_rules, UsernameRules.MIN_LENGTH, UsernameRules.MAX_LENGTH)
}

@Composable
private fun usernameErrorText(status: UsernameStatus): String? = when (status) {
    UsernameStatus.Taken -> stringResource(R.string.feature_profile_username_taken)
    UsernameStatus.Reserved -> stringResource(R.string.feature_profile_username_reserved)
    is UsernameStatus.Invalid -> when (status.issue) {
        UsernameIssue.TooShort -> null
        UsernameIssue.TooLong -> pluralStringResource(
            R.plurals.feature_profile_username_too_long,
            UsernameRules.MAX_LENGTH,
            UsernameRules.MAX_LENGTH,
        )
        UsernameIssue.InvalidCharacters -> stringResource(R.string.feature_profile_username_invalid_characters)
        UsernameIssue.MustStartOrEndWithLetterOrDigit -> stringResource(R.string.feature_profile_username_edges)
        UsernameIssue.ConsecutivePeriods -> stringResource(R.string.feature_profile_username_periods)
    }
    else -> null
}

private fun ProfileError.messageRes(): Int = when (this) {
    ProfileError.UsernameTaken -> R.string.feature_profile_username_taken
    ProfileError.UsernameReserved -> R.string.feature_profile_username_reserved
    is ProfileError.InvalidInput -> when (field) {
        ProfileField.Username -> R.string.feature_profile_error_invalid_username
        ProfileField.DisplayName -> R.string.feature_profile_error_invalid_display_name
        ProfileField.Bio -> R.string.feature_profile_error_invalid_bio
        ProfileField.Avatar -> R.string.feature_profile_error_image
    }
    ProfileError.ImageUnreadable -> R.string.feature_profile_error_image
    ProfileError.NotSignedIn -> R.string.feature_profile_error_signed_out
    ProfileError.Network -> R.string.feature_profile_error_network
    is ProfileError.Unknown -> R.string.feature_profile_error_unknown
}
