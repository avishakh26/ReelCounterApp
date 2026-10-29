package com.reelcounter.core.data

import com.reelcounter.core.data.auth.PasswordResetFlag
import com.reelcounter.core.network.RemoteException
import com.reelcounter.core.network.auth.AuthRemoteDataSource
import com.reelcounter.core.network.auth.RemoteSessionStatus
import com.reelcounter.core.network.profile.NetworkProfile
import com.reelcounter.core.network.profile.NetworkProfileUpdate
import com.reelcounter.core.network.profile.ProfileRemoteDataSource
import kotlinx.coroutines.flow.MutableStateFlow

internal class FakeAuthRemoteDataSource : AuthRemoteDataSource {
    override val sessionStatus = MutableStateFlow<RemoteSessionStatus>(RemoteSessionStatus.SignedOut)

    /** When set, every call throws it. */
    var failure: RemoteException? = null
    var signUpNeedsVerification = true
    val calls = mutableListOf<String>()
    val signUpMetadata = mutableListOf<Map<String, String>>()

    private fun record(call: String) {
        calls += call
        failure?.let { throw it }
    }

    private fun signInAs(email: String) {
        sessionStatus.value = RemoteSessionStatus.SignedIn(userId = "user-1", email = email)
    }

    override suspend fun signUp(email: String, password: String, metadata: Map<String, String>): Boolean {
        record("signUp")
        signUpMetadata += metadata
        if (!signUpNeedsVerification) signInAs(email)
        return signUpNeedsVerification
    }

    override suspend fun verifyEmailCode(email: String, code: String): Boolean {
        record("verifyEmailCode")
        signInAs(email)
        return true
    }

    override suspend fun resendSignUpCode(email: String) = record("resendSignUpCode")

    override suspend fun signIn(email: String, password: String) {
        record("signIn")
        signInAs(email)
    }

    override suspend fun sendPasswordResetCode(email: String) = record("sendPasswordResetCode")

    override suspend fun verifyPasswordResetCode(email: String, code: String): Boolean {
        record("verifyPasswordResetCode")
        signInAs(email)
        return true
    }

    override suspend fun updatePassword(newPassword: String) = record("updatePassword")

    override suspend fun signOut() {
        // Like the real data source: the local session is cleared even when the call fails.
        sessionStatus.value = RemoteSessionStatus.SignedOut
        record("signOut")
    }
}

internal class FakePasswordResetFlag(private val log: MutableList<String>? = null) : PasswordResetFlag {
    override val isPending = MutableStateFlow(false)

    override suspend fun set(pending: Boolean) {
        log?.add("flag=$pending")
        isPending.value = pending
    }
}

internal class FakeProfileRemoteDataSource : ProfileRemoteDataSource {
    val profiles = mutableMapOf<String, NetworkProfile>()
    val storedAvatars = mutableSetOf<String>()
    val deletedAvatars = mutableListOf<String>()
    var usernameStatus = "available"
    var updateFailure: RemoteException? = null
    var fetchFailure: RemoteException? = null
    var checkCalls = 0

    override suspend fun fetchProfile(userId: String): NetworkProfile? {
        fetchFailure?.let { throw it }
        return profiles[userId]
    }

    override suspend fun updateProfile(userId: String, update: NetworkProfileUpdate): NetworkProfile {
        updateFailure?.let { throw it }
        val saved = NetworkProfile(userId, update.username, update.displayName, update.bio, update.avatarPath)
        profiles[userId] = saved
        return saved
    }

    override suspend fun checkUsername(candidate: String): String {
        checkCalls++
        return usernameStatus
    }

    override suspend fun uploadAvatar(path: String, bytes: ByteArray, mimeType: String) {
        storedAvatars += path
    }

    override suspend fun deleteAvatar(path: String) {
        storedAvatars -= path
        deletedAvatars += path
    }

    override fun avatarUrl(path: String) = "https://cdn.test/$path"
}
