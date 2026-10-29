package com.reelcounter.core.domain.tracking

/** One entry from Android's usage event log, reduced to the fields the engine needs. */
data class UsageEvent(
    val timestampMs: Long,
    val type: UsageEventType,
    val packageName: String? = null,
    /** The activity class, so several activities of one app can be told apart. */
    val className: String? = null,
)

enum class UsageEventType {
    ActivityResumed,
    ActivityPaused,
    ActivityStopped,
    ScreenInteractive,
    ScreenNonInteractive,
    KeyguardShown,
    KeyguardHidden,
    DeviceShutdown,
    DeviceStartup,
}

/**
 * A stretch of time when one app was in the foreground. [isOpen] means it was still in the
 * foreground when the log was read, so [endMs] is provisional and the next read extends it.
 */
data class UsageSession(
    val packageName: String,
    val startMs: Long,
    val endMs: Long,
    val isOpen: Boolean = false,
) {
    val durationMs: Long get() = endMs - startMs
}

/** Reads the system usage log. Implemented over UsageStatsManager in :core:tracking. */
interface UsageEventSource {
    /** Events in [fromMs, toMs). Empty when usage access is missing or the device is locked. */
    suspend fun query(fromMs: Long, toMs: Long): List<UsageEvent>

    /** False before the first unlock after boot, when the usage log can't be read. */
    fun isUserUnlocked(): Boolean
}
