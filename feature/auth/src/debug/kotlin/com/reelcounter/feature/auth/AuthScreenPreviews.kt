package com.reelcounter.feature.auth

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewFontScale
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.reelcounter.core.domain.auth.AuthError
import com.reelcounter.core.domain.auth.EmailIssue
import com.reelcounter.core.domain.auth.PasswordIssue
import com.reelcounter.core.preview.PreviewTheme
import com.reelcounter.feature.auth.code.CodeVerificationScreen
import com.reelcounter.feature.auth.code.CodeVerificationUiState
import com.reelcounter.feature.auth.reset.ForgotPasswordScreen
import com.reelcounter.feature.auth.reset.ForgotPasswordUiState
import com.reelcounter.feature.auth.reset.SetNewPasswordScreen
import com.reelcounter.feature.auth.reset.SetNewPasswordUiState
import com.reelcounter.feature.auth.signin.SignInScreen
import com.reelcounter.feature.auth.signin.SignInUiState
import com.reelcounter.feature.auth.signup.SignUpScreen
import com.reelcounter.feature.auth.signup.SignUpUiState

@PreviewLightDark
@Composable
private fun SignInPreview() = PreviewTheme {
    SignInScreen(
        uiState = SignInUiState(email = "sam@example.com", error = AuthError.InvalidCredentials),
        onEmailChange = {}, onPasswordChange = {}, onSignIn = {}, onCreateAccount = {}, onForgotPassword = {},
    )
}

@PreviewLightDark
@PreviewFontScale
@Composable
private fun SignUpPreview() = PreviewTheme {
    SignUpScreen(
        uiState = SignUpUiState(
            email = "sam@",
            emailIssue = EmailIssue.Invalid,
            passwordIssue = PasswordIssue.TooShort,
            ageConfirmationMissing = true,
        ),
        onEmailChange = {}, onPasswordChange = {}, onAgeConfirmedChange = {}, onSignUp = {}, onBack = {},
    )
}

@PreviewLightDark
@Composable
private fun VerifyCodePreview() = PreviewTheme {
    CodeVerificationScreen(
        title = "Check your email",
        uiState = CodeVerificationUiState(email = "sam@example.com", code = "1234", resendCooldownSeconds = 42),
        onCodeChange = {}, onVerify = {}, onResend = {}, onBack = {},
    )
}

@PreviewLightDark
@Composable
private fun ForgotPasswordPreview() = PreviewTheme {
    ForgotPasswordScreen(
        uiState = ForgotPasswordUiState(email = "sam@example.com", isSubmitting = true),
        onEmailChange = {}, onSendCode = {}, onBack = {},
    )
}

@PreviewLightDark
@Composable
private fun SetNewPasswordPreview() = PreviewTheme {
    SetNewPasswordScreen(
        uiState = SetNewPasswordUiState(password = "longenough", confirmation = "different", mismatch = true),
        onPasswordChange = {}, onConfirmationChange = {}, onSave = {}, onCancel = {},
    )
}
