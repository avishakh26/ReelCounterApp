package com.reelcounter.core.data.social

import com.reelcounter.core.common.AppForegroundMonitor
import com.reelcounter.core.common.AppInitializer
import com.reelcounter.core.common.dispatchers.ApplicationScope
import com.reelcounter.core.domain.auth.AuthRepository
import com.reelcounter.core.domain.auth.AuthState
import com.reelcounter.core.domain.social.ConnectionsState
import com.reelcounter.core.domain.social.FriendsRepository
import com.reelcounter.core.domain.social.PresenceRepository
import com.reelcounter.core.domain.social.PrivacyRepository
import com.reelcounter.core.domain.social.PrivacyState
import com.reelcounter.core.network.SupabaseConfig
import com.reelcounter.core.network.realtime.FriendsChannelEvent
import com.reelcounter.core.network.realtime.RealtimeRemoteDataSource
import com.reelcounter.core.network.social.FriendActivityRemoteDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

/**
 * Owns all Realtime connections. They exist only while someone is signed in AND the app is in
 * the foreground, which keeps battery use and the presence footprint minimal.
 *
 * - Friend-request changes refresh the friends list live, and friends' activity changes are
 *   passed on to [DefaultFriendActivityRepository.changes].
 * - The user's own presence is shared only if they opted in (Privacy → online status).
 * - Friends' presence is watched for up to [MAX_WATCHED_FRIENDS] friends. RLS only lets through
 *   friends who opted in themselves.
 */
@Singleton
internal class RealtimeCoordinator @Inject constructor(
    private val authRepository: AuthRepository,
    private val friendsRepository: FriendsRepository,
    private val friendActivity: DefaultFriendActivityRepository,
    private val privacyRepository: PrivacyRepository,
    private val realtime: RealtimeRemoteDataSource,
    private val activityRemote: FriendActivityRemoteDataSource,
    private val foreground: AppForegroundMonitor,
    private val config: SupabaseConfig,
    @param:ApplicationScope private val scope: CoroutineScope,
) : AppInitializer, PresenceRepository {

    private val online = MutableStateFlow<Set<String>>(emptySet())
    override val onlineFriendIds: StateFlow<Set<String>> = online.asStateFlow()

    override fun initialize() {
        if (!config.isConfigured) return
        val activeUser = combine(
            authRepository.authState.map { (it as? AuthState.SignedIn)?.userId },
            foreground.isForeground(),
        ) { userId, isForeground -> userId.takeIf { isForeground } }.distinctUntilChanged()

        scope.launch {
            activeUser.collectLatest { userId ->
                online.value = emptySet()
                if (userId == null) return@collectLatest
                coroutineScope {
                    // Anything may have changed while in the background.
                    launch { friendsRepository.refresh() }
                    launch {
                        realtime.friendsChannel(userId).withBackoff().collect { event ->
                            when (event) {
                                FriendsChannelEvent.FriendshipsChanged -> friendsRepository.refresh()
                                is FriendsChannelEvent.ActivityChanged -> friendActivity.onActivityChanged(event.userId)
                            }
                        }
                    }
                    launch {
                        privacyRepository.settings
                            .map { (it as? PrivacyState.Loaded)?.settings?.shareOnlineStatus == true }
                            .distinctUntilChanged()
                            .collectLatest { share -> if (share) realtime.shareOwnPresence(userId).withBackoff().collect { } }
                    }
                    launch {
                        friendsRepository.connections
                            .map { state -> (state as? ConnectionsState.Loaded)?.friends?.take(MAX_WATCHED_FRIENDS)?.map { it.id }?.toSet().orEmpty() }
                            .distinctUntilChanged()
                            .collectLatest { ids ->
                                // Join only channels we're allowed into: Realtime refuses the rest, and the
                                // client's endless rejoin attempts would keep dropping the shared websocket.
                                val visible = runCatching { activityRemote.friendsSharingPresence().toSet() }.getOrDefault(emptySet())
                                realtime.presenceOf(ids intersect visible).withBackoff().collect { online.value = it }
                            }
                    }
                }
            }
        }
    }

    /** Reconnects after failures (e.g. network loss) with exponential backoff, capped at 60 s. */
    private fun <T> Flow<T>.withBackoff(): Flow<T> = retryWhen { _, attempt ->
        delay(min(60_000L, 1_000L shl attempt.coerceAtMost(6).toInt()))
        true
    }

    private companion object {
        const val MAX_WATCHED_FRIENDS = 50
    }
}
