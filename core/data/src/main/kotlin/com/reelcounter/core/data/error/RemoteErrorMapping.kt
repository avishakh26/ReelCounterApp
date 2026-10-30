package com.reelcounter.core.data.error

import com.reelcounter.core.domain.auth.AuthError
import com.reelcounter.core.domain.profile.ProfileError
import com.reelcounter.core.network.RemoteException
import com.reelcounter.core.network.RemoteException.Kind

internal fun RemoteException.toAuthError(): AuthError = when (kind) {
    Kind.Network, Kind.Timeout -> AuthError.Network
    Kind.Auth -> when (code) {
        "invalid_credentials" -> AuthError.InvalidCredentials
        "email_not_confirmed" -> AuthError.EmailNotConfirmed
        "user_already_exists", "email_exists" -> AuthError.EmailAlreadyRegistered
        "weak_password" -> AuthError.WeakPassword(details)
        "same_password" -> AuthError.SamePassword
        // Supabase reports wrong and expired codes the same way.
        "otp_expired" -> AuthError.InvalidOrExpiredCode
        "over_request_rate_limit", "over_email_send_rate_limit" -> AuthError.RateLimited
        "signup_disabled", "email_provider_disabled" -> AuthError.SignUpDisabled
        else -> AuthError.Unknown(code)
    }
    Kind.Database -> if (isServerOutdated()) AuthError.ServerOutdated else AuthError.Unknown(code)
    Kind.Server, Kind.Unknown -> AuthError.Unknown(code)
}

/**
 * The server doesn't have something the app calls: a function (PostgREST PGRST202, Postgres
 * 42883) or a column (42703). Happens when a newer migration hasn't been run on the project yet.
 */
internal fun RemoteException.isServerOutdated(): Boolean =
    kind == Kind.Database && code in setOf("PGRST202", "42883", "42703")

internal fun RemoteException.toProfileError(): ProfileError = when (kind) {
    Kind.Network, Kind.Timeout -> ProfileError.Network
    Kind.Database -> when (code) {
        "23505" -> ProfileError.UsernameTaken // unique_violation on profiles.username
        "RC001" -> ProfileError.UsernameReserved // raised by private.reject_reserved_username()
        else -> ProfileError.Unknown(code)
    }
    Kind.Auth, Kind.Server, Kind.Unknown -> ProfileError.Unknown(code)
}
