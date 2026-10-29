package com.reelcounter.feature.auth.signin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.reelcounter.core.designsystem.component.RcButton
import com.reelcounter.core.designsystem.component.RcOutlinedButton
import com.reelcounter.core.designsystem.component.RcPasswordField
import com.reelcounter.core.designsystem.component.RcTextButton
import com.reelcounter.core.designsystem.component.RcTextField
import com.reelcounter.feature.auth.R
import com.reelcounter.feature.auth.ui.AuthDivider
import com.reelcounter.feature.auth.ui.AuthScreenLayout
import com.reelcounter.feature.auth.ui.FormError
import com.reelcounter.feature.auth.ui.messageRes

@Composable
internal fun SignInScreen(
    onCreateAccount: () -> Unit,
    onForgotPassword: (email: String) -> Unit,
    onVerifyEmail: (email: String) -> Unit,
    viewModel: SignInViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.verifyEmail) {
        uiState.verifyEmail?.let {
            onVerifyEmail(it)
            viewModel.onVerifyEmailHandled()
        }
    }

    SignInScreen(
        uiState = uiState,
        onEmailChange = viewModel::onEmailChange,
        onPasswordChange = viewModel::onPasswordChange,
        onSignIn = viewModel::signIn,
        onCreateAccount = onCreateAccount,
        onForgotPassword = { onForgotPassword(uiState.email) },
    )
}

@Composable
internal fun SignInScreen(
    uiState: SignInUiState,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSignIn: () -> Unit,
    onCreateAccount: () -> Unit,
    onForgotPassword: () -> Unit,
) {
    AuthScreenLayout(
        title = stringResource(R.string.feature_auth_sign_in_title),
        subtitle = stringResource(R.string.feature_auth_sign_in_subtitle),
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
            errorText = if (uiState.passwordMissing) stringResource(R.string.feature_auth_password_required) else null,
            enabled = !uiState.isSubmitting,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onSignIn() }),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RcTextButton(
                text = stringResource(R.string.feature_auth_forgot_password),
                onClick = onForgotPassword,
                enabled = !uiState.isSubmitting,
            )
        }
        FormError(uiState.error?.let { stringResource(it.messageRes()) })
        RcButton(
            text = stringResource(R.string.feature_auth_sign_in_action),
            onClick = onSignIn,
            loading = uiState.isSubmitting,
            modifier = Modifier.fillMaxWidth(),
        )
        AuthDivider(text = stringResource(R.string.feature_auth_new_here))
        RcOutlinedButton(
            text = stringResource(R.string.feature_auth_create_account),
            onClick = onCreateAccount,
            enabled = !uiState.isSubmitting,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
