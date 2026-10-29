package com.reelcounter.core.network.auth

import com.reelcounter.core.network.remoteCall
import dagger.Lazy
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.OtpVerifyResult
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.util.Base64
import javax.inject.Inject

// The client is Lazy so an unconfigured build never creates one.
internal class SupabaseAuthRemoteDataSource @Inject constructor(
    private val client: Lazy<SupabaseClient>,
) : AuthRemoteDataSource {

    private val auth: Auth get() = client.get().auth

    override val sessionStatus: Flow<RemoteSessionStatus> = flow { emitAll(auth.sessionStatus) }
        .map { status ->
            when (status) {
                SessionStatus.Initializing -> RemoteSessionStatus.Initializing
                is SessionStatus.Authenticated -> status.session.toSignedIn()
                is SessionStatus.NotAuthenticated -> RemoteSessionStatus.SignedOut
                // A refresh failed (usually offline). The saved session is still valid to show the
                // user's data, so they stay signed in; supabase-kt retries the refresh itself.
                is SessionStatus.RefreshFailure ->
                    auth.sessionManager.loadSessionOrNull()?.toSignedIn() ?: RemoteSessionStatus.SignedOut
            }
        }
        .distinctUntilChanged()

    override suspend fun signUp(email: String, password: String, metadata: Map<String, String>): Boolean = remoteCall {
        val user = auth.signUpWith(Email) {
            this.email = email
            this.password = password
            data = JsonObject(metadata.mapValues { JsonPrimitive(it.value) })
        }
        // supabase-kt returns the user when confirmation is pending, and null when the project
        // auto-confirms (the session is then already imported).
        user != null
    }

    override suspend fun verifyEmailCode(email: String, code: String): Boolean = remoteCall {
        auth.verifyEmailOtp(type = OtpType.Email.EMAIL, email = email, token = code) is OtpVerifyResult.Authenticated
    }

    override suspend fun resendSignUpCode(email: String) = remoteCall {
        auth.resendEmail(type = OtpType.Email.SIGNUP, email = email)
    }

    override suspend fun signIn(email: String, password: String) = remoteCall {
        auth.signInWith(Email) {
            this.email = email
            this.password = password
        }
    }

    override suspend fun sendPasswordResetCode(email: String) = remoteCall {
        auth.resetPasswordForEmail(email = email)
    }

    override suspend fun verifyPasswordResetCode(email: String, code: String): Boolean = remoteCall {
        auth.verifyEmailOtp(type = OtpType.Email.RECOVERY, email = email, token = code) is OtpVerifyResult.Authenticated
    }

    override suspend fun updatePassword(newPassword: String) = remoteCall {
        auth.updateUser { password = newPassword }
        Unit
    }

    override suspend fun signOut() = remoteCall {
        try {
            auth.signOut()
        } finally {
            // Signing out must work offline too: drop the local session even if the server call failed.
            if (auth.currentSessionOrNull() != null) auth.clearSession()
        }
    }
}

private fun UserSession.toSignedIn(): RemoteSessionStatus {
    val userId = user?.id ?: subjectOf(accessToken) ?: return RemoteSessionStatus.SignedOut
    return RemoteSessionStatus.SignedIn(userId = userId, email = user?.email)
}

/** The `sub` claim of a JWT, for the rare session restored without its user object. */
private fun subjectOf(jwt: String): String? {
    val payload = jwt.split('.').getOrNull(1) ?: return null
    val json = runCatching { String(Base64.getUrlDecoder().decode(payload)) }.getOrNull() ?: return null
    return Regex(""""sub"\s*:\s*"([^"]+)"""").find(json)?.groupValues?.get(1)
}
