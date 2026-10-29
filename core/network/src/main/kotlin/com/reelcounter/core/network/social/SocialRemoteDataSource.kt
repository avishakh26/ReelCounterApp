package com.reelcounter.core.network.social

import com.reelcounter.core.network.SupabaseConfig
import com.reelcounter.core.network.remoteCall
import dagger.Lazy
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject

/** The Phase 6 RPCs and the caller's own privacy row. Methods throw RemoteException. */
interface SocialRemoteDataSource {
    suspend fun search(prefix: String): List<NetworkUserCard>
    suspend fun connections(): List<NetworkConnection>
    suspend fun blockedUsers(): List<NetworkUserCard>

    /** Each returns the RPC's status word, e.g. "requested" or "not_found". */
    suspend fun sendFriendRequest(target: String): String
    suspend fun respondFriendRequest(requester: String, accept: Boolean): String
    suspend fun cancelFriendRequest(target: String): String
    suspend fun removeFriend(target: String): String
    suspend fun block(target: String): String
    suspend fun unblock(target: String): String

    suspend fun privacySettings(userId: String): NetworkPrivacySettings?
    suspend fun updatePrivacySettings(userId: String, settings: NetworkPrivacySettings): NetworkPrivacySettings

    fun avatarUrl(path: String): String
}

@Serializable
data class NetworkUserCard(
    @SerialName("id") val idFromSearch: String? = null,
    @SerialName("user_id") val idFromList: String? = null,
    val username: String,
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("avatar_path") val avatarPath: String? = null,
    /** Only in search results: none, friend, incoming or outgoing. */
    val relationship: String? = null,
) {
    val id: String get() = checkNotNull(idFromSearch ?: idFromList)
}

@Serializable
data class NetworkConnection(
    @SerialName("user_id") val userId: String,
    val username: String,
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("avatar_path") val avatarPath: String? = null,
    /** friend, incoming or outgoing. */
    val status: String,
)

@Serializable
data class NetworkPrivacySettings(
    @SerialName("activity_visibility") val activityVisibility: String,
    @SerialName("share_presence") val sharePresence: Boolean,
    val discoverable: Boolean,
    @SerialName("accept_friend_requests") val acceptFriendRequests: Boolean,
)

internal class SupabaseSocialRemoteDataSource @Inject constructor(
    private val client: Lazy<SupabaseClient>,
    private val config: SupabaseConfig,
) : SocialRemoteDataSource {

    private val postgrest get() = client.get().postgrest

    override suspend fun search(prefix: String): List<NetworkUserCard> = remoteCall {
        postgrest.rpc("search_profiles", buildJsonObject { put("prefix", prefix) }).decodeList()
    }

    override suspend fun connections(): List<NetworkConnection> = remoteCall {
        postgrest.rpc("my_connections").decodeList()
    }

    override suspend fun blockedUsers(): List<NetworkUserCard> = remoteCall {
        postgrest.rpc("my_blocked_users").decodeList()
    }

    override suspend fun sendFriendRequest(target: String) = status("send_friend_request", buildJsonObject { put("target", target) })

    override suspend fun respondFriendRequest(requester: String, accept: Boolean) =
        status("respond_friend_request", buildJsonObject {
            put("requester", requester)
            put("accept", accept)
        })

    override suspend fun cancelFriendRequest(target: String) = status("cancel_friend_request", buildJsonObject { put("target", target) })

    override suspend fun removeFriend(target: String) = status("remove_friend", buildJsonObject { put("target", target) })

    override suspend fun block(target: String) = status("block_user", buildJsonObject { put("target", target) })

    override suspend fun unblock(target: String) = status("unblock_user", buildJsonObject { put("target", target) })

    override suspend fun privacySettings(userId: String): NetworkPrivacySettings? = remoteCall {
        postgrest.from("user_privacy_settings")
            .select(PRIVACY_COLUMNS) { filter { eq("user_id", userId) } }
            .decodeSingleOrNull()
    }

    override suspend fun updatePrivacySettings(userId: String, settings: NetworkPrivacySettings): NetworkPrivacySettings =
        remoteCall {
            postgrest.from("user_privacy_settings")
                .update(settings) {
                    select(PRIVACY_COLUMNS)
                    filter { eq("user_id", userId) }
                }
                .decodeSingle()
        }

    override fun avatarUrl(path: String): String = config.publicObjectUrl("avatars", path)

    private suspend fun status(function: String, parameters: JsonObject): String = remoteCall {
        postgrest.rpc(function, parameters).decodeAs<String>()
    }

    private companion object {
        val PRIVACY_COLUMNS = Columns.list("activity_visibility", "share_presence", "discoverable", "accept_friend_requests")
    }
}
