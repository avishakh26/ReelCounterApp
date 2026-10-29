package com.reelcounter.feature.auth.reset

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.reelcounter.core.designsystem.component.RcButton
import com.reelcounter.core.designsystem.component.RcTextField
import com.reelcounter.core.designsystem.icon.RcIcons
import com.reelcounter.feature.auth.R
import com.reelcounter.feature.auth.ui.AuthScreenLayout
import com.reelcounter.feature.auth.ui.FormError
import com.reelcounter.feature.auth.ui.messageRes

@Composable
internal fun ForgotPasswordScreen(
    onBack: () -> Unit,
    onCodeSent: (email: String) -> Unit,
    viewModel: ForgotPasswordViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.codeSentTo) {
        uiState.codeSentTo?.let {
            onCodeSent(it)
            viewModel.onCodeSentHandled()
        }
    }

    ForgotPasswordScreen(
        uiState = uiState,
        onEmailChange = viewModel::onEmailChange,
        onSendCode = viewModel::sendCode,
        onBack = onBack,
    )
}

@Composable
internal fun ForgotPasswordScreen(
    uiState: ForgotPasswordUiState,
    onEmailChange: (String) -> Unit,
    onSendCode: () -> Unit,
    onBack: () -> Unit,
) {
    AuthScreenLayout(
        title = stringResource(R.string.feature_auth_forgot_title),
        subtitle = stringResource(R.string.feature_auth_forgot_subtitle),
        onBack = onBack,
        icon = RcIcons.Privacy,
    ) {
        RcTextField(
            value = uiState.email,
            onValueChange = onEmailChange,
            label = stringResource(R.string.feature_auth_email),
            errorText = uiState.emailIssue?.let { stringResource(it.messageRes()) },
            enabled = !uiState.isSubmitting,
            autofill = ContentType.EmailAddress,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onSendCode() }),
        )
        FormError(uiState.error?.let { stringResource(it.messageRes()) })
        RcButton(
            text = stringResource(R.string.feature_auth_send_code),
            onClick = onSendCode,
            loading = uiState.isSubmitting,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
