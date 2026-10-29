package com.reelcounter.feature.auth.code

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.em
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.reelcounter.core.designsystem.component.RcBanner
import com.reelcounter.core.designsystem.component.RcButton
import com.reelcounter.core.designsystem.component.RcTextButton
import com.reelcounter.core.designsystem.component.RcTextField
import com.reelcounter.core.designsystem.component.RcTone
import com.reelcounter.core.designsystem.icon.RcIcons
import com.reelcounter.core.domain.auth.OtpRules
import com.reelcounter.feature.auth.R
import com.reelcounter.feature.auth.ui.AuthScreenLayout
import com.reelcounter.feature.auth.ui.FormError
import com.reelcounter.feature.auth.ui.messageRes

/** Enter the code that confirms a new account's email. */
@Composable
internal fun VerifyEmailScreen(
    onBack: () -> Unit,
    viewModel: VerifyEmailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    CodeVerificationScreen(
        title = stringResource(R.string.feature_auth_verify_title),
        uiState = uiState,
        onCodeChange = viewModel::onCodeChange,
        onVerify = viewModel::verify,
        onResend = viewModel::resendCode,
        onBack = onBack,
    )
}

/** Enter the code from a password-reset email. On success the user picks a new password. */
@Composable
internal fun ResetCodeScreen(
    onBack: () -> Unit,
    viewModel: ResetCodeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    CodeVerificationScreen(
        title = stringResource(R.string.feature_auth_reset_code_title),
        uiState = uiState,
        onCodeChange = viewModel::onCodeChange,
        onVerify = viewModel::verify,
        onResend = viewModel::resendCode,
        onBack = onBack,
    )
}

@Composable
internal fun CodeVerificationScreen(
    title: String,
    uiState: CodeVerificationUiState,
    onCodeChange: (String) -> Unit,
    onVerify: () -> Unit,
    onResend: () -> Unit,
    onBack: () -> Unit,
) {
    AuthScreenLayout(
        title = title,
        subtitle = stringResource(R.string.feature_auth_code_sent_to, uiState.email),
        onBack = onBack,
        icon = RcIcons.EmailCode,
    ) {
        RcTextField(
            value = uiState.code,
            onValueChange = onCodeChange,
            label = stringResource(R.string.feature_auth_code_label, OtpRules.LENGTH),
            enabled = !uiState.isVerifying,
            // Lets the keyboard offer the code straight from the email notification.
            autofill = ContentType.SmsOtpCode,
            // Large, spaced digits are easier to compare with the email.
            textStyle = MaterialTheme.typography.headlineSmall.copy(letterSpacing = 0.3.em),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onVerify() }),
        )
        FormError(uiState.error?.let { stringResource(it.messageRes()) })
        if (uiState.codeResent) {
            RcBanner(
                message = stringResource(R.string.feature_auth_code_resent),
                icon = RcIcons.Success,
                tone = RcTone.Positive,
            )
        }
        RcButton(
            text = stringResource(R.string.feature_auth_verify_action),
            onClick = onVerify,
            loading = uiState.isVerifying,
            enabled = OtpRules.isComplete(uiState.code),
            modifier = Modifier.fillMaxWidth(),
        )
        RcTextButton(
            text = if (uiState.resendCooldownSeconds > 0) {
                stringResource(R.string.feature_auth_resend_in, uiState.resendCooldownSeconds)
            } else {
                stringResource(R.string.feature_auth_resend)
            },
            onClick = onResend,
            enabled = uiState.resendCooldownSeconds == 0 && !uiState.isResending && !uiState.isVerifying,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = stringResource(R.string.feature_auth_code_spam_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
