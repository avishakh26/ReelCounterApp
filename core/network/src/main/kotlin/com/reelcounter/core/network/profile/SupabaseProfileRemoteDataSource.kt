package com.reelcounter.core.network.profile

import com.reelcounter.core.network.RemoteException
import com.reelcounter.core.network.SupabaseConfig
import com.reelcounter.core.network.remoteCall
import dagger.Lazy
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.storage.storage
import io.ktor.http.ContentType
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject

internal class SupabaseProfileRemoteDataSource @Inject constructor(
    private val client: Lazy<SupabaseClient>,
    private val config: SupabaseConfig,
) : ProfileRemoteDataSource {

    override suspend fun fetchProfile(userId: String): NetworkProfile? = remoteCall {
        client.get().postgrest.from(TABLE)
            .select(COLUMNS) { filter { eq("id", userId) } }
            .decodeSingleOrNull<NetworkProfile>()
    }

    override suspend fun updateProfile(userId: String, update: NetworkProfileUpdate): NetworkProfile = remoteCall {
        client.get().postgrest.from(TABLE)
            .update({
                set("username", update.username)
                set("display_name", update.displayName)
                set("bio", update.bio)
                set("avatar_path", update.avatarPath)
            }) {
                select(COLUMNS)
                filter { eq("id", userId) }
            }
            .decodeSingleOrNull<NetworkProfile>()
            // RLS hides other users' rows, so "no row" means it isn't ours (or no longer exists).
            ?: throw RemoteException(RemoteException.Kind.Database, code = "not_found")
    }

    override suspend fun checkUsername(candidate: String): String = remoteCall {
        client.get().postgrest
            .rpc("check_username", buildJsonObject { put("candidate", candidate) })
            .decodeAs<String>()
    }

    override suspend fun uploadAvatar(path: String, bytes: ByteArray, mimeType: String) = remoteCall {
        client.get().storage.from(AVATAR_BUCKET).upload(path, bytes) {
            upsert = false
            contentType = ContentType.parse(mimeType)
        }
        Unit
    }

    override suspend fun deleteAvatar(path: String) = remoteCall {
        client.get().storage.from(AVATAR_BUCKET).delete(path)
    }

    override fun avatarUrl(path: String): String = config.publicObjectUrl(AVATAR_BUCKET, path)

    private companion object {
        const val TABLE = "profiles"
        const val AVATAR_BUCKET = "avatars"
        val COLUMNS = Columns.list("id", "username", "display_name", "bio", "avatar_path")
    }
}
