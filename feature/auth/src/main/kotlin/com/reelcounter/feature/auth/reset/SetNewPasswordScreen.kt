package com.reelcounter.feature.auth.reset

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.reelcounter.core.designsystem.component.RcButton
import com.reelcounter.core.designsystem.component.RcPasswordField
import com.reelcounter.core.designsystem.component.RcTextButton
import com.reelcounter.core.designsystem.icon.RcIcons
import com.reelcounter.feature.auth.R
import com.reelcounter.feature.auth.ui.AuthScreenLayout
import com.reelcounter.feature.auth.ui.FormError
import com.reelcounter.feature.auth.ui.message
import com.reelcounter.feature.auth.ui.messageRes
import com.reelcounter.feature.auth.ui.passwordHint

/** Shown by the app while a password reset is pending (AuthState.PasswordResetPending). */
@Composable
fun SetNewPasswordScreen(
    modifier: Modifier = Modifier,
    viewModel: SetNewPasswordViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    SetNewPasswordScreen(
        uiState = uiState,
        onPasswordChange = viewModel::onPasswordChange,
        onConfirmationChange = viewModel::onConfirmationChange,
        onSave = viewModel::save,
        onCancel = viewModel::cancel,
        modifier = modifier,
    )
}

@Composable
internal fun SetNewPasswordScreen(
    uiState: SetNewPasswordUiState,
    onPasswordChange: (String) -> Unit,
    onConfirmationChange: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AuthScreenLayout(
        title = stringResource(R.string.feature_auth_new_password_title),
        subtitle = stringResource(R.string.feature_auth_new_password_subtitle),
        modifier = modifier,
        icon = RcIcons.Privacy,
    ) {
        RcPasswordField(
            value = uiState.password,
            onValueChange = onPasswordChange,
            label = stringResource(R.string.feature_auth_new_password),
            supportingText = passwordHint(),
            errorText = uiState.passwordIssue?.message(),
            enabled = !uiState.isSubmitting,
            isNewPassword = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        )
        RcPasswordField(
            value = uiState.confirmation,
            onValueChange = onConfirmationChange,
            label = stringResource(R.string.feature_auth_confirm_password),
            errorText = if (uiState.mismatch) stringResource(R.string.feature_auth_password_mismatch) else null,
            enabled = !uiState.isSubmitting,
            isNewPassword = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onSave() }),
        )
        FormError(uiState.error?.let { stringResource(it.messageRes()) })
        RcButton(
            text = stringResource(R.string.feature_auth_save_password),
            onClick = onSave,
            loading = uiState.isSubmitting,
            modifier = Modifier.fillMaxWidth(),
        )
        RcTextButton(
            text = stringResource(R.string.feature_auth_cancel_reset),
            onClick = onCancel,
            enabled = !uiState.isSubmitting,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
