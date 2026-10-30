package com.reelcounter.core.network.activity

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** `public.devices` and `public.daily_activity_summaries`. Methods throw RemoteException. */
interface ActivityRemoteDataSource {
    /** Idempotent: registering an already-registered device does nothing. */
    suspend fun registerDevice(deviceId: String, userId: String)

    /**
     * Upserts absolute daily totals on (user, device, platform, date). The server keeps the row
     * with the higher revision, so retries and out-of-order uploads are safe.
     */
    suspend fun upsertDailySummaries(rows: List<NetworkDailySummary>)

    /** The (date, platform) pairs on [dates] that [userId] has rows for from devices other than [deviceId]. */
    suspend fun daysFromOtherDevices(userId: String, deviceId: String, dates: Collection<String>): Set<Pair<String, String>>
}

/**
 * One row of public.daily_activity_summaries. Deliberately no default values: every column,
 * including nulls, is always sent, so a cleared estimate is cleared on the server too.
 */
@Serializable
data class NetworkDailySummary(
    @SerialName("user_id") val userId: String,
    @SerialName("device_id") val deviceId: String,
    @SerialName("platform_id") val platformId: String,
    @SerialName("activity_date") val activityDate: String,
    @SerialName("tz_id") val tzId: String,
    @SerialName("foreground_ms") val foregroundMs: Long,
    @SerialName("session_count") val sessionCount: Int,
    @SerialName("estimated_reels") val estimatedReels: Int?,
    @SerialName("estimated_reels_low") val estimatedReelsLow: Int?,
    @SerialName("estimated_reels_high") val estimatedReelsHigh: Int?,
    val quality: String,
    val source: String,
    @SerialName("estimation_version") val estimationVersion: Int?,
    @SerialName("user_adjusted") val userAdjusted: Boolean,
    @SerialName("is_final") val isFinal: Boolean,
    val revision: Long,
)
