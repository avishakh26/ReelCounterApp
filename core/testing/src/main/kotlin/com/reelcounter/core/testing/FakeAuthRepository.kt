package com.reelcounter.core.testing

import com.reelcounter.core.domain.Outcome
import com.reelcounter.core.domain.auth.AuthError
import com.reelcounter.core.domain.auth.AuthRepository
import com.reelcounter.core.domain.auth.AuthState
import com.reelcounter.core.domain.auth.SignUpResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Records calls and returns whatever the test configured. */
class FakeAuthRepository(initialState: AuthState = AuthState.SignedOut) : AuthRepository {
    val state = MutableStateFlow(initialState)
    override val authState: StateFlow<AuthState> = state

    /** Result of every operation that returns Unit. */
    var result: Outcome<Unit, AuthError> = Outcome.Success(Unit)
    var signUpResult: Outcome<SignUpResult, AuthError> = Outcome.Success(SignUpResult.VerificationRequired)

    val calls = mutableListOf<String>()

    override suspend fun signUp(email: String, password: String): Outcome<SignUpResult, AuthError> {
        calls += "signUp:$email"
        return signUpResult
    }

    override suspend fun verifySignUpCode(email: String, code: String) = record("verifySignUpCode:$email:$code")

    override suspend fun resendSignUpCode(email: String) = record("resendSignUpCode:$email")

    override suspend fun signIn(email: String, password: String) = record("signIn:$email")

    override suspend fun sendPasswordResetCode(email: String) = record("sendPasswordResetCode:$email")

    override suspend fun verifyPasswordResetCode(email: String, code: String) = record("verifyPasswordResetCode:$email:$code")

    override suspend fun completePasswordReset(newPassword: String) = record("completePasswordReset")

    override suspend fun signOut() {
        calls += "signOut"
        state.value = AuthState.SignedOut
    }

    override suspend fun deleteAccount(): Outcome<Unit, AuthError> {
        val outcome = record("deleteAccount")
        if (outcome is Outcome.Success) state.value = AuthState.SignedOut
        return outcome
    }

    private fun record(call: String): Outcome<Unit, AuthError> {
        calls += call
        return result
    }
}
