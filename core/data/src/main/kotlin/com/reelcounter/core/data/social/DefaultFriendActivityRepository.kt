package com.reelcounter.core.data.social

import com.reelcounter.core.domain.Outcome
import com.reelcounter.core.domain.map
import com.reelcounter.core.domain.social.FriendActivityRepository
import com.reelcounter.core.domain.social.FriendSuggestion
import com.reelcounter.core.domain.social.SocialError
import com.reelcounter.core.domain.tracking.PlatformCatalog
import com.reelcounter.core.model.DailyPlatformActivity
import com.reelcounter.core.model.Platform
import com.reelcounter.core.model.PlatformMetric
import com.reelcounter.core.model.UnavailableReason
import com.reelcounter.core.model.UserSummary
import com.reelcounter.core.network.RemoteException
import com.reelcounter.core.network.SupabaseConfig
import com.reelcounter.core.network.social.FriendActivityRemoteDataSource
import com.reelcounter.core.network.social.NetworkDailyTotal
import com.reelcounter.core.network.social.SocialRemoteDataSource
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.milliseconds

@Singleton
internal class DefaultFriendActivityRepository @Inject constructor(
    private val remote: FriendActivityRemoteDataSource,
    private val social: SocialRemoteDataSource,
    private val config: SupabaseConfig,
) : FriendActivityRepository {

    private val changed = MutableSharedFlow<String>(extraBufferCapacity = 16, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val changes: Flow<String> = changed.asSharedFlow()

    /** Called by [RealtimeCoordinator] when the server says a friend's figures changed. */
    fun onActivityChanged(userId: String) {
        changed.tryEmit(userId)
    }

    override suspend fun activity(
        userIds: Collection<String>,
        from: LocalDate,
    ): Outcome<Map<String, List<DailyPlatformActivity>>, SocialError> =
        call { remote.dailyTotals(userIds, from) }.map { rows ->
            rows.groupBy { it.userId }.mapValues { (_, days) -> days.map { it.toActivity() }.sortedBy { it.date } }
        }

    override suspend fun suggestions(): Outcome<List<FriendSuggestion>, SocialError> =
        call { remote.suggestions(MAX_SUGGESTIONS) }.map { list ->
            list.map {
                FriendSuggestion(
                    user = UserSummary(it.id, it.username, it.displayName, it.avatarPath?.let(social::avatarUrl)),
                    mutualFriends = it.mutualFriends,
                )
            }
        }

    private suspend fun <T> call(block: suspend () -> T): Outcome<T, SocialError> {
        if (!config.isConfigured) return Outcome.Failure(SocialError.Unknown("not_configured"))
        return try {
            Outcome.Success(block())
        } catch (e: RemoteException) {
            Outcome.Failure(e.toSocialError())
        }
    }

    private companion object {
        const val MAX_SUGGESTIONS = 20
    }
}

/** One platform-day as the friend's own app recorded it, keeping its category and range. */
internal fun NetworkDailyTotal.toActivity(): DailyPlatformActivity {
    val platform = PlatformCatalog.forId(platformId)?.platform
        ?: Platform(id = platformId, displayName = platformId.replaceFirstChar { it.uppercase() })
    val date = LocalDate.parse(activityDate)
    val appTime = foregroundMs?.coerceAtLeast(0)?.milliseconds
    val estimated = estimatedReels
    val low = estimatedReelsLow
    val high = estimatedReelsHigh
    val verified = verifiedReels
    val counted = countedReels
    val metric = when {
        bestQuality == "VERIFIED" && verified != null -> PlatformMetric.Verified(count = verified, verifiedThrough = date, appTime = appTime)
        bestQuality == "COUNTED" && counted != null -> PlatformMetric.Counted(count = counted, appTime = appTime ?: kotlin.time.Duration.ZERO)
        estimated != null && low != null && high != null -> PlatformMetric.Estimated(
            appTime = appTime ?: kotlin.time.Duration.ZERO,
            count = estimated,
            low = minOf(low, estimated),
            high = maxOf(high, estimated),
            userAdjusted = userAdjusted == true,
        )
        appTime != null -> PlatformMetric.DurationOnly(appTime)
        else -> PlatformMetric.Unavailable(UnavailableReason.COVERAGE_GAP)
    }
    return DailyPlatformActivity(platform = platform, date = date, metric = metric)
}
