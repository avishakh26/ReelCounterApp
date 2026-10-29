package com.reelcounter.core.tracking

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.UserManager
import androidx.core.content.getSystemService
import com.reelcounter.core.common.dispatchers.Dispatcher
import com.reelcounter.core.common.dispatchers.RcDispatchers
import com.reelcounter.core.domain.tracking.PlatformCatalog
import com.reelcounter.core.domain.tracking.UsageEvent
import com.reelcounter.core.domain.tracking.UsageEventSource
import com.reelcounter.core.domain.tracking.UsageEventType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Reads UsageStatsManager's event log. Only events for catalog apps and device-wide events
 * (screen, lock screen, shutdown) are kept, so other apps' usage never leaves this function.
 */
internal class UsageStatsEventSource @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:Dispatcher(RcDispatchers.IO) private val ioDispatcher: CoroutineDispatcher,
) : UsageEventSource {

    private val catalogPackages = PlatformCatalog.all.flatMap { it.packages }.toSet()

    override fun isUserUnlocked(): Boolean = context.getSystemService<UserManager>()?.isUserUnlocked ?: true

    override suspend fun query(fromMs: Long, toMs: Long): List<UsageEvent> = withContext(ioDispatcher) {
        val manager = context.getSystemService<UsageStatsManager>() ?: return@withContext emptyList()
        // Returns null (or throws) without usage access or while the user is locked.
        val events = runCatching { manager.queryEvents(fromMs, toMs) }.getOrNull() ?: return@withContext emptyList()

        val result = ArrayList<UsageEvent>()
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val type = event.eventType.toUsageEventType() ?: continue
            val isAppEvent = type == UsageEventType.ActivityResumed ||
                type == UsageEventType.ActivityPaused ||
                type == UsageEventType.ActivityStopped
            if (isAppEvent && event.packageName !in catalogPackages) continue
            result += UsageEvent(
                timestampMs = event.timeStamp,
                type = type,
                packageName = event.packageName.takeIf { isAppEvent },
                className = event.className.takeIf { isAppEvent },
            )
        }
        result
    }
}

// Raw values from android.app.usage.UsageEvents.Event, used as literals because several
// constants were only named in later API levels (the values themselves are stable since API 21–29).
private fun Int.toUsageEventType(): UsageEventType? = when (this) {
    1 -> UsageEventType.ActivityResumed // MOVE_TO_FOREGROUND / ACTIVITY_RESUMED
    2 -> UsageEventType.ActivityPaused // MOVE_TO_BACKGROUND / ACTIVITY_PAUSED
    23 -> UsageEventType.ActivityStopped // API 29+
    15 -> UsageEventType.ScreenInteractive // API 28+
    16 -> UsageEventType.ScreenNonInteractive // API 28+
    17 -> UsageEventType.KeyguardShown // API 28+
    18 -> UsageEventType.KeyguardHidden // API 28+
    26 -> UsageEventType.DeviceShutdown // API 29+
    27 -> UsageEventType.DeviceStartup // API 29+
    else -> null
}
