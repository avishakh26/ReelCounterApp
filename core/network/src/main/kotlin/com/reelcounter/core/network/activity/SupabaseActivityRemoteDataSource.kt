package com.reelcounter.core.network.activity

import com.reelcounter.core.network.remoteCall
import dagger.Lazy
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import javax.inject.Inject

internal class SupabaseActivityRemoteDataSource @Inject constructor(
    private val client: Lazy<SupabaseClient>,
) : ActivityRemoteDataSource {

    override suspend fun registerDevice(deviceId: String, userId: String) = remoteCall {
        client.get().postgrest.from("devices").upsert(listOf(NetworkDevice(deviceId, userId))) {
            onConflict = "id"
            ignoreDuplicates = true
        }
        Unit
    }

    override suspend fun daysFromOtherDevices(userId: String, deviceId: String, dates: Collection<String>): Set<Pair<String, String>> {
        if (dates.isEmpty()) return emptySet()
        return remoteCall {
            client.get().postgrest.from("daily_activity_summaries")
                .select(Columns.list("activity_date", "platform_id")) {
                    filter {
                        eq("user_id", userId)
                        neq("device_id", deviceId)
                        isIn("activity_date", dates.toList())
                    }
                }
                .decodeList<NetworkDayKey>()
                .map { it.activityDate to it.platformId }
                .toSet()
        }
    }

    override suspend fun upsertDailySummaries(rows: List<NetworkDailySummary>) = remoteCall {
        if (rows.isEmpty()) return@remoteCall
        client.get().postgrest.from("daily_activity_summaries").upsert(rows) {
            onConflict = "user_id,device_id,platform_id,activity_date"
        }
        Unit
    }
}

@Serializable
private data class NetworkDevice(
    val id: String,
    @SerialName("user_id") val userId: String,
)

@Serializable
private data class NetworkDayKey(
    @SerialName("activity_date") val activityDate: String,
    @SerialName("platform_id") val platformId: String,
)
