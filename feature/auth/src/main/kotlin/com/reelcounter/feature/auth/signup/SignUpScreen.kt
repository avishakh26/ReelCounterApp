package com.reelcounter.feature.auth.signup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.reelcounter.core.designsystem.component.RcButton
import com.reelcounter.core.designsystem.component.RcPasswordField
import com.reelcounter.core.designsystem.component.RcTextButton
import com.reelcounter.core.designsystem.component.RcTextField
import com.reelcounter.core.designsystem.theme.Spacing
import com.reelcounter.feature.auth.R
import com.reelcounter.feature.auth.ui.AuthScreenLayout
import com.reelcounter.feature.auth.ui.FormError
import com.reelcounter.feature.auth.ui.message
import com.reelcounter.feature.auth.ui.messageRes
import com.reelcounter.feature.auth.ui.passwordHint

@Composable
internal fun SignUpScreen(
    onBack: () -> Unit,
    onVerifyEmail: (email: String) -> Unit,
    viewModel: SignUpViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.verifyEmail) {
        uiState.verifyEmail?.let {
            onVerifyEmail(it)
            viewModel.onVerifyEmailHandled()
        }
    }

    SignUpScreen(
        uiState = uiState,
        onEmailChange = viewModel::onEmailChange,
        onPasswordChange = viewModel::onPasswordChange,
        onAgeConfirmedChange = viewModel::onAgeConfirmedChange,
        onSignUp = viewModel::signUp,
        onBack = onBack,
    )
}

@Composable
internal fun SignUpScreen(
    uiState: SignUpUiState,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onAgeConfirmedChange: (Boolean) -> Unit,
    onSignUp: () -> Unit,
    onBack: () -> Unit,
) {
    AuthScreenLayout(
        title = stringResource(R.string.feature_auth_sign_up_title),
        subtitle = stringResource(R.string.feature_auth_sign_up_subtitle),
        onBack = onBack,
    ) {
        RcTextField(
            value = uiState.email,
            onValueChange = onEmailChange,
            label = stringResource(R.string.feature_auth_email),
            errorText = uiState.emailIssue?.let { stringResource(it.messageRes()) },
            enabled = !uiState.isSubmitting,
            autofill = ContentType.EmailAddress,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
        )
        RcPasswordField(
            value = uiState.password,
            onValueChange = onPasswordChange,
            label = stringResource(R.string.feature_auth_password),
            supportingText = passwordHint(),
            errorText = uiState.passwordIssue?.message(),
            enabled = !uiState.isSubmitting,
            isNewPassword = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        )
        AgeConfirmation(
            checked = uiState.ageConfirmed,
            showError = uiState.ageConfirmationMissing,
            enabled = !uiState.isSubmitting,
            onCheckedChange = onAgeConfirmedChange,
        )
        FormError(uiState.error?.let { stringResource(it.messageRes()) })
        RcButton(
            text = stringResource(R.string.feature_auth_sign_up_action),
            onClick = onSignUp,
            loading = uiState.isSubmitting,
            modifier = Modifier.fillMaxWidth(),
        )
        RcTextButton(
            text = stringResource(R.string.feature_auth_have_account),
            onClick = onBack,
            enabled = !uiState.isSubmitting,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun AgeConfirmation(
    checked: Boolean,
    showError: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Column {
        // The whole row is one toggle target, so the label is tappable and read with the checkbox.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(value = checked, enabled = enabled, role = Role.Checkbox, onValueChange = onCheckedChange),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = checked, onCheckedChange = null, enabled = enabled)
            Text(text = stringResource(R.string.feature_auth_age_confirmation), style = MaterialTheme.typography.bodyMedium)
        }
        if (showError) {
            Text(
                text = stringResource(R.string.feature_auth_age_confirmation_required),
                modifier = Modifier.padding(start = Spacing.xxxl),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}
