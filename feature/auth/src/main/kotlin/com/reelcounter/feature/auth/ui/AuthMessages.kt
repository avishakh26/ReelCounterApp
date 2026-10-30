package com.reelcounter.feature.auth.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.liveRegion
import com.reelcounter.core.domain.auth.AuthError
import com.reelcounter.core.domain.auth.EmailIssue
import com.reelcounter.core.domain.auth.PasswordIssue
import com.reelcounter.core.domain.auth.PasswordRules
import com.reelcounter.feature.auth.R

@StringRes
internal fun AuthError.messageRes(): Int = when (this) {
    AuthError.InvalidCredentials -> R.string.feature_auth_error_invalid_credentials
    AuthError.EmailNotConfirmed -> R.string.feature_auth_error_email_not_confirmed
    AuthError.EmailAlreadyRegistered -> R.string.feature_auth_error_email_registered
    is AuthError.WeakPassword ->
        if ("pwned" in reasons) R.string.feature_auth_error_password_breached else R.string.feature_auth_error_password_weak
    AuthError.SamePassword -> R.string.feature_auth_error_same_password
    AuthError.InvalidOrExpiredCode -> R.string.feature_auth_error_invalid_code
    AuthError.RateLimited -> R.string.feature_auth_error_rate_limited
    AuthError.SignUpDisabled -> R.string.feature_auth_error_signup_disabled
    AuthError.Network -> R.string.feature_auth_error_network
    AuthError.NotConfigured -> R.string.feature_auth_error_not_configured
    AuthError.ServerOutdated -> R.string.feature_auth_error_server_outdated
    is AuthError.Unknown -> R.string.feature_auth_error_unknown
}

@StringRes
internal fun EmailIssue.messageRes(): Int = when (this) {
    EmailIssue.Blank -> R.string.feature_auth_email_required
    EmailIssue.Invalid -> R.string.feature_auth_email_invalid
}

@Composable
internal fun PasswordIssue.message(): String = when (this) {
    PasswordIssue.TooShort -> pluralStringResource(
        R.plurals.feature_auth_password_too_short,
        PasswordRules.MIN_LENGTH,
        PasswordRules.MIN_LENGTH,
    )
    PasswordIssue.TooLong -> stringResource(R.string.feature_auth_password_too_long)
}

@Composable
internal fun passwordHint(): String =
    pluralStringResource(R.plurals.feature_auth_password_hint, PasswordRules.MIN_LENGTH, PasswordRules.MIN_LENGTH)

internal fun SemanticsPropertyReceiver.liveRegionPolite() {
    liveRegion = LiveRegionMode.Polite
}
