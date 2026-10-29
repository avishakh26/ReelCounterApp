package com.reelcounter.core.data.auth

import com.reelcounter.core.common.dispatchers.ApplicationScope
import com.reelcounter.core.data.error.toAuthError
import com.reelcounter.core.domain.Outcome
import com.reelcounter.core.domain.auth.AuthError
import com.reelcounter.core.domain.auth.AuthRepository
import com.reelcounter.core.domain.auth.AuthState
import com.reelcounter.core.domain.auth.SignUpResult
import com.reelcounter.core.network.RemoteException
import com.reelcounter.core.network.SupabaseConfig
import com.reelcounter.core.network.auth.AuthRemoteDataSource
import com.reelcounter.core.network.auth.RemoteSessionStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class DefaultAuthRepository @Inject constructor(
    private val remote: AuthRemoteDataSource,
    private val config: SupabaseConfig,
    private val passwordResetFlag: PasswordResetFlag,
    @ApplicationScope scope: CoroutineScope,
) : AuthRepository {

    override val authState: StateFlow<AuthState> =
        if (!config.isConfigured) {
            MutableStateFlow(AuthState.NotConfigured).asStateFlow()
        } else {
            combine(remote.sessionStatus, passwordResetFlag.isPending) { status, resetPending ->
                when (status) {
                    RemoteSessionStatus.Initializing -> AuthState.Initializing
                    RemoteSessionStatus.SignedOut -> AuthState.SignedOut
                    is RemoteSessionStatus.SignedIn ->
                        if (resetPending) {
                            AuthState.PasswordResetPending(status.userId, status.email)
                        } else {
                            AuthState.SignedIn(status.userId, status.email)
                        }
                }
            }.stateIn(scope, SharingStarted.Eagerly, AuthState.Initializing)
        }

    override suspend fun signUp(email: String, password: String): Outcome<SignUpResult, AuthError> = call {
        // Records that sign-up went through the 16+ age gate (decision D4) in the user's metadata.
        val needsVerification = remote.signUp(email, password, metadata = mapOf("age_gate" to "16+"))
        if (needsVerification) SignUpResult.VerificationRequired else SignUpResult.SignedIn
    }

    override suspend fun verifySignUpCode(email: String, code: String): Outcome<Unit, AuthError> = call {
        if (!remote.verifyEmailCode(email, code)) {
            throw RemoteException(RemoteException.Kind.Auth, code = "verified_without_session")
        }
    }

    override suspend fun resendSignUpCode(email: String): Outcome<Unit, AuthError> = call {
        remote.resendSignUpCode(email)
    }

    override suspend fun signIn(email: String, password: String): Outcome<Unit, AuthError> = call {
        remote.signIn(email, password)
    }

    override suspend fun sendPasswordResetCode(email: String): Outcome<Unit, AuthError> = call {
        remote.sendPasswordResetCode(email)
    }

    override suspend fun verifyPasswordResetCode(email: String, code: String): Outcome<Unit, AuthError> =
        // Must finish even if the screen goes away mid-call; the flag and the session have to agree.
        withContext(NonCancellable) {
            // Set first, so the session arriving with the verification never shows the main app.
            passwordResetFlag.set(true)
            val outcome = call {
                if (!remote.verifyPasswordResetCode(email, code)) {
                    throw RemoteException(RemoteException.Kind.Auth, code = "verified_without_session")
                }
            }
            if (outcome is Outcome.Failure) passwordResetFlag.set(false)
            outcome
        }

    override suspend fun completePasswordReset(newPassword: String): Outcome<Unit, AuthError> =
        withContext(NonCancellable) {
            call {
                remote.updatePassword(newPassword)
                passwordResetFlag.set(false)
            }
        }

    override suspend fun signOut() = withContext(NonCancellable) {
        try {
            remote.signOut()
        } catch (_: RemoteException) {
            // The data source clears the local session even when the server can't be reached.
        } finally {
            passwordResetFlag.set(false)
        }
    }

    private suspend fun <T> call(block: suspend () -> T): Outcome<T, AuthError> {
        if (!config.isConfigured) return Outcome.Failure(AuthError.NotConfigured)
        return try {
            Outcome.Success(block())
        } catch (e: RemoteException) {
            Outcome.Failure(e.toAuthError())
        }
    }
}
