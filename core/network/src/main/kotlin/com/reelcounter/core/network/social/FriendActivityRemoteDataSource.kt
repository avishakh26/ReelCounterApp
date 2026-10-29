package com.reelcounter.core.network.social

import com.reelcounter.core.network.remoteCall
import dagger.Lazy
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.LocalDate
import javax.inject.Inject

/**
 * Friends' daily figures and friend suggestions. Methods throw RemoteException.
 *
 * Figures come from the `daily_activity_totals` view, which sums each person's devices and applies
 * the caller's RLS: rows a friend doesn't share simply aren't returned.
 */
interface FriendActivityRemoteDataSource {
    /** Per-platform daily totals of [userIds] on or after [from]. */
    suspend fun dailyTotals(userIds: Collection<String>, from: LocalDate): List<NetworkDailyTotal>

    /** People the caller might know, best matches first. */
    suspend fun suggestions(maxResults: Int): List<NetworkSuggestion>

    /** Ids of friends whose presence channel the caller may join (they opted in, no stealth, no block). */
    suspend fun friendsSharingPresence(): List<String>
}

@Serializable
data class NetworkDailyTotal(
    @SerialName("user_id") val userId: String,
    @SerialName("platform_id") val platformId: String,
    @SerialName("activity_date") val activityDate: String,
    @SerialName("foreground_ms") val foregroundMs: Long? = null,
    @SerialName("estimated_reels") val estimatedReels: Int? = null,
    @SerialName("estimated_reels_low") val estimatedReelsLow: Int? = null,
    @SerialName("estimated_reels_high") val estimatedReelsHigh: Int? = null,
    @SerialName("verified_reels") val verifiedReels: Int? = null,
    /** VERIFIED, ESTIMATED, DURATION_ONLY or UNAVAILABLE: the best category across devices. */
    @SerialName("best_quality") val bestQuality: String,
    @SerialName("user_adjusted") val userAdjusted: Boolean? = null,
    /** Reels counted one by one on the friend's phone (category COUNTED). */
    @SerialName("counted_reels") val countedReels: Int? = null,
)

@Serializable
data class NetworkSuggestion(
    val id: String,
    val username: String,
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("avatar_path") val avatarPath: String? = null,
    @SerialName("mutual_friends") val mutualFriends: Int = 0,
)

internal class SupabaseFriendActivityRemoteDataSource @Inject constructor(
    private val client: Lazy<SupabaseClient>,
) : FriendActivityRemoteDataSource {

    private val postgrest get() = client.get().postgrest

    override suspend fun dailyTotals(userIds: Collection<String>, from: LocalDate): List<NetworkDailyTotal> {
        if (userIds.isEmpty()) return emptyList()
        return remoteCall {
            postgrest.from("daily_activity_totals")
                .select(TOTAL_COLUMNS) {
                    filter {
                        isIn("user_id", userIds.toList())
                        gte("activity_date", from.toString())
                    }
                }
                .decodeList()
        }
    }

    override suspend fun suggestions(maxResults: Int): List<NetworkSuggestion> = remoteCall {
        postgrest.rpc("suggest_friends", buildJsonObject { put("max_results", maxResults) }).decodeList()
    }

    override suspend fun friendsSharingPresence(): List<String> = remoteCall {
        postgrest.rpc("friends_sharing_presence").decodeList<String>()
    }

    private companion object {
        val TOTAL_COLUMNS = Columns.list(
            "user_id", "platform_id", "activity_date", "foreground_ms", "estimated_reels",
            "estimated_reels_low", "estimated_reels_high", "verified_reels", "best_quality", "user_adjusted", "counted_reels",
        )
    }
}
