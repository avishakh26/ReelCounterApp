package com.reelcounter.core.network.realtime

import dagger.Lazy
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.broadcast.BroadcastPayload
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.presenceDataFlow
import io.github.jan.supabase.realtime.realtime
import io.github.jan.supabase.realtime.track
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

/**
 * Supabase Realtime. Every flow subscribes when collected and unsubscribes when the collector is
 * cancelled, so callers decide how long connections live (only while the app is in the foreground).
 * All channels are private: joining them is authorized by RLS on realtime.messages.
 */
interface RealtimeRemoteDataSource {
    /**
     * Events on [userId]'s own private channel "friendships:<id>". Database triggers send them:
     * [FriendsChannelEvent.FriendshipsChanged] when a friendship involving the user changes, and
     * [FriendsChannelEvent.ActivityChanged] when a friend's daily figures change. Neither carries
     * data; the app refetches through the usual RLS. One collector per user: joining the same
     * topic twice would make the server drop the first join.
     */
    fun friendsChannel(userId: String): Flow<FriendsChannelEvent>

    /** Shares [userId]'s presence on their own channel for as long as it's collected. */
    fun shareOwnPresence(userId: String): Flow<Unit>

    /** The subset of [userIds] currently present. Only channels the caller may join report anything. */
    fun presenceOf(userIds: Set<String>): Flow<Set<String>>
}

internal class SupabaseRealtimeRemoteDataSource @Inject constructor(
    private val client: Lazy<SupabaseClient>,
) : RealtimeRemoteDataSource {

    override fun friendsChannel(userId: String): Flow<FriendsChannelEvent> = subscribed("friendships:$userId") { channel ->
        merge(
            channel.broadcastFlow(event = FRIENDSHIP_CHANGED_EVENT).map { FriendsChannelEvent.FriendshipsChanged },
            channel.broadcastFlow(event = ACTIVITY_CHANGED_EVENT).mapNotNull { broadcast ->
                val payload = (broadcast.payload as? BroadcastPayload.Json)?.value as? JsonObject
                payload?.get("user_id")?.jsonPrimitive?.contentOrNull?.let(FriendsChannelEvent::ActivityChanged)
            },
        )
    }

    override fun shareOwnPresence(userId: String): Flow<Unit> = channelFlow {
        val channel = client.get().channel(presenceTopic(userId)) { isPrivate = true }
        try {
            channel.subscribe(blockUntilSubscribed = true)
            channel.track(PresenceState(online = true))
            send(Unit)
            awaitCancellation()
        } finally {
            withContext(NonCancellable) {
                runCatching { channel.untrack() }
                client.get().realtime.removeChannel(channel)
            }
        }
    }

    override fun presenceOf(userIds: Set<String>): Flow<Set<String>> = channelFlow {
        send(emptySet())
        if (userIds.isEmpty()) awaitCancellation()
        // One collector per friend; they run concurrently, so the shared set must be thread-safe.
        val online = ConcurrentHashMap.newKeySet<String>()
        userIds.forEach { id ->
            launch {
                subscribed(presenceTopic(id)) { it.presenceDataFlow<JsonObject>() }.collect { presences ->
                    val changed = if (presences.isEmpty()) online.remove(id) else online.add(id)
                    if (changed) send(online.toSet())
                }
            }
        }
    }

    /**
     * Creates a private channel, starts listening BEFORE subscribing (Realtime needs bindings and
     * presence callbacks registered first), and removes the channel when cancelled.
     */
    private fun <T> subscribed(topic: String, stream: (RealtimeChannel) -> Flow<T>): Flow<T> = channelFlow {
        val channel = client.get().channel(topic) { isPrivate = true }
        val events = stream(channel)
        launch(start = CoroutineStart.UNDISPATCHED) { events.collect { send(it) } }
        try {
            channel.subscribe(blockUntilSubscribed = true)
            awaitCancellation()
        } finally {
            withContext(NonCancellable) { client.get().realtime.removeChannel(channel) }
        }
    }

    private fun presenceTopic(userId: String) = "presence:$userId"

    private companion object {
        /** Sent by private.notify_friendship_change() in the Phase 6 migration. */
        const val FRIENDSHIP_CHANGED_EVENT = "changed"

        /** Sent by private.notify_activity_change() in the friend-activity migration. */
        const val ACTIVITY_CHANGED_EVENT = "activity"
    }
}

@Serializable
private data class PresenceState(val online: Boolean)

sealed interface FriendsChannelEvent {
    /** A friendship involving the user was created, accepted or deleted. */
    data object FriendshipsChanged : FriendsChannelEvent

    /** The figures of friend [userId] changed. */
    data class ActivityChanged(val userId: String) : FriendsChannelEvent
}
