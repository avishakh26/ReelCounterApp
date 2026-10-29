package com.reelcounter.core.domain.tracking

/**
 * Turns raw usage events into foreground sessions, one state machine per app:
 *
 * - An app is in the foreground while at least one of its activities is resumed, so moving
 *   between two screens of the same app (A paused, B resumed) doesn't end the session.
 * - A pause followed by a resume of the same app within [MERGE_GAP_MS] is one session.
 * - Screen off, the lock screen and shutdown end every session.
 * - A startup with sessions still open means the device died without a shutdown event; those
 *   sessions end at the last event seen before the startup.
 * - Apps still in the foreground at [windowEndMs] get an open session ending there.
 *
 * The result is deterministic for a given event list, which is what makes re-reading
 * overlapping windows safe.
 */
object SessionBuilder {
    /** Activity transitions inside one app produce pause/resume pairs a few ms apart. */
    const val MERGE_GAP_MS = 2_000L

    fun build(events: List<UsageEvent>, trackedPackages: Set<String>, windowEndMs: Long): List<UsageSession> {
        val resumedActivities = mutableMapOf<String, MutableSet<String>>()
        val openSince = mutableMapOf<String, Long>()
        val closed = mutableListOf<UsageSession>()
        // Last session per app that ended by a pause (not screen-off), eligible for merging.
        val mergeable = mutableMapOf<String, UsageSession>()
        var lastTimestamp: Long? = null

        fun close(pkg: String, at: Long, allowMerge: Boolean) {
            val start = openSince.remove(pkg) ?: return
            resumedActivities.remove(pkg)
            if (at <= start) return
            val session = UsageSession(pkg, start, at)
            closed += session
            if (allowMerge) mergeable[pkg] = session else mergeable.remove(pkg)
        }

        fun closeAll(at: Long) {
            openSince.keys.toList().forEach { close(it, at, allowMerge = false) }
            resumedActivities.clear()
            mergeable.clear()
        }

        // Stable sort: events with equal timestamps keep the system's order.
        for (event in events.sortedBy { it.timestampMs }) {
            val pkg = event.packageName
            when (event.type) {
                UsageEventType.ActivityResumed -> if (pkg != null && pkg in trackedPackages) {
                    resumedActivities.getOrPut(pkg) { mutableSetOf() } += event.className.orEmpty()
                    if (pkg !in openSince) {
                        val previous = mergeable.remove(pkg)
                        if (previous != null && event.timestampMs - previous.endMs <= MERGE_GAP_MS) {
                            closed.remove(previous)
                            openSince[pkg] = previous.startMs
                        } else {
                            openSince[pkg] = event.timestampMs
                        }
                    }
                }
                UsageEventType.ActivityPaused, UsageEventType.ActivityStopped -> if (pkg != null && pkg in openSince) {
                    val activities = resumedActivities[pkg]
                    activities?.remove(event.className.orEmpty())
                    if (activities.isNullOrEmpty()) close(pkg, event.timestampMs, allowMerge = true)
                }
                UsageEventType.ScreenNonInteractive,
                UsageEventType.KeyguardShown,
                UsageEventType.DeviceShutdown,
                -> closeAll(event.timestampMs)
                UsageEventType.DeviceStartup -> closeAll(lastTimestamp ?: event.timestampMs)
                UsageEventType.ScreenInteractive, UsageEventType.KeyguardHidden -> Unit
            }
            lastTimestamp = event.timestampMs
        }

        val open = openSince.mapNotNull { (pkg, start) ->
            if (windowEndMs > start) UsageSession(pkg, start, windowEndMs, isOpen = true) else null
        }
        return (closed + open).sortedWith(compareBy({ it.startMs }, { it.packageName }))
    }
}
