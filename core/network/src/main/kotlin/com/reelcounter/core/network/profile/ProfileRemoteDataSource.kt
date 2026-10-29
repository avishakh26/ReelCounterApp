package com.reelcounter.core.network.profile

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** The `profiles` table and the `avatars` bucket. All methods throw RemoteException on failure. */
interface ProfileRemoteDataSource {
    suspend fun fetchProfile(userId: String): NetworkProfile?

    suspend fun updateProfile(userId: String, update: NetworkProfileUpdate): NetworkProfile

    /** The raw status from `public.check_username`: available, taken, reserved or invalid. */
    suspend fun checkUsername(candidate: String): String

    suspend fun uploadAvatar(path: String, bytes: ByteArray, mimeType: String)

    suspend fun deleteAvatar(path: String)

    fun avatarUrl(path: String): String
}

@Serializable
data class NetworkProfile(
    val id: String,
    val username: String? = null,
    @SerialName("display_name") val displayName: String? = null,
    val bio: String? = null,
    @SerialName("avatar_path") val avatarPath: String? = null,
)

/** Every column is written, including nulls, so clearing a field really clears it. */
data class NetworkProfileUpdate(
    val username: String,
    val displayName: String?,
    val bio: String?,
    val avatarPath: String?,
)
