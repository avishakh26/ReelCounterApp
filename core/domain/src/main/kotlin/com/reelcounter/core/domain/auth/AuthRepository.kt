package com.reelcounter.core.domain.auth

import com.reelcounter.core.domain.Outcome
import kotlinx.coroutines.flow.StateFlow

/**
 * Email and password authentication. Email ownership is proven with 6-digit codes, both when
 * signing up and when resetting a password.
 */
interface AuthRepository {
    val authState: StateFlow<AuthState>

    /** Creates the account. Usually the email still has to be verified with [verifySignUpCode]. */
    suspend fun signUp(email: String, password: String): Outcome<SignUpResult, AuthError>

    suspend fun verifySignUpCode(email: String, code: String): Outcome<Unit, AuthError>

    suspend fun resendSignUpCode(email: String): Outcome<Unit, AuthError>

    suspend fun signIn(email: String, password: String): Outcome<Unit, AuthError>

    /** Emails a reset code. Succeeds whether or not an account exists, so emails can't be probed. */
    suspend fun sendPasswordResetCode(email: String): Outcome<Unit, AuthError>

    /** On success the user is signed in and [authState] becomes [AuthState.PasswordResetPending]. */
    suspend fun verifyPasswordResetCode(email: String, code: String): Outcome<Unit, AuthError>

    /** Sets the new password and finishes a reset; [authState] then becomes [AuthState.SignedIn]. */
    suspend fun completePasswordReset(newPassword: String): Outcome<Unit, AuthError>

    suspend fun signOut()

    /**
     * Permanently deletes the account and everything stored with it (profile, photos, friends, daily
     * figures) on the server, then signs out. Required by Google Play and the App Store.
     */
    suspend fun deleteAccount(): Outcome<Unit, AuthError>
}

sealed interface AuthState {
    /** Restoring a saved session; the splash screen stays up. */
    data object Initializing : AuthState

    /** The build has no Supabase URL or key (see secrets.defaults.properties). */
    data object NotConfigured : AuthState

    data object SignedOut : AuthState

    /** Signed in with a reset code; must choose a new password before using the app. */
    data class PasswordResetPending(val userId: String, val email: String?) : AuthState

    data class SignedIn(val userId: String, val email: String?) : AuthState
}

enum class SignUpResult {
    /** A code was emailed; call [AuthRepository.verifySignUpCode]. */
    VerificationRequired,

    /** The project doesn't require email confirmation; the user is already signed in. */
    SignedIn,
}

sealed interface AuthError {
    data object InvalidCredentials : AuthError
    data object EmailNotConfirmed : AuthError
    data object EmailAlreadyRegistered : AuthError
    data class WeakPassword(val reasons: List<String>) : AuthError
    data object SamePassword : AuthError
    data object InvalidOrExpiredCode : AuthError
    data object RateLimited : AuthError
    data object SignUpDisabled : AuthError
    data object Network : AuthError
    data object NotConfigured : AuthError
    data class Unknown(val code: String?) : AuthError
}
