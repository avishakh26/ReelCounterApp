package com.reelcounter.core.network.auth

import kotlinx.coroutines.flow.Flow

/** Supabase Auth, reduced to what the app needs. All methods throw RemoteException on failure. */
interface AuthRemoteDataSource {
    val sessionStatus: Flow<RemoteSessionStatus>

    /** Returns true if the email must still be verified with a code. */
    suspend fun signUp(email: String, password: String, metadata: Map<String, String>): Boolean

    /** Returns true if a session was created (the user is now signed in). */
    suspend fun verifyEmailCode(email: String, code: String): Boolean

    suspend fun resendSignUpCode(email: String)

    suspend fun signIn(email: String, password: String)

    suspend fun sendPasswordResetCode(email: String)

    /** Returns true if a session was created (the user is now signed in). */
    suspend fun verifyPasswordResetCode(email: String, code: String): Boolean

    suspend fun updatePassword(newPassword: String)

    suspend fun signOut()
}

sealed interface RemoteSessionStatus {
    data object Initializing : RemoteSessionStatus
    data object SignedOut : RemoteSessionStatus
    data class SignedIn(val userId: String, val email: String?) : RemoteSessionStatus
}
