package com.reelcounter.core.domain.tracking

import com.reelcounter.core.domain.tracking.UsageEventType.ActivityPaused
import com.reelcounter.core.domain.tracking.UsageEventType.ActivityResumed
import com.reelcounter.core.domain.tracking.UsageEventType.ActivityStopped
import com.reelcounter.core.domain.tracking.UsageEventType.DeviceShutdown
import com.reelcounter.core.domain.tracking.UsageEventType.DeviceStartup
import com.reelcounter.core.domain.tracking.UsageEventType.KeyguardShown
import com.reelcounter.core.domain.tracking.UsageEventType.ScreenNonInteractive
import kotlin.test.Test
import kotlin.test.assertEquals

class SessionBuilderTest {

    private val ig = "com.instagram.android"
    private val tt = "com.zhiliaoapp.musically"
    private val tracked = setOf(ig, tt)

    private fun ev(t: Long, type: UsageEventType, pkg: String? = null, cls: String? = "Main") = UsageEvent(t, type, pkg, cls)

    private fun build(vararg events: UsageEvent, end: Long = 1_000_000) = SessionBuilder.build(events.toList(), tracked, end)

    @Test
    fun `resume then pause is one session`() {
        assertEquals(listOf(UsageSession(ig, 1_000, 61_000)), build(ev(1_000, ActivityResumed, ig), ev(61_000, ActivityPaused, ig)))
    }

    @Test
    fun `untracked apps are ignored`() {
        assertEquals(emptyList(), build(ev(1_000, ActivityResumed, "com.android.chrome"), ev(9_000, ActivityPaused, "com.android.chrome")))
    }

    @Test
    fun `moving between two screens of one app stays one session`() {
        val sessions = build(
            ev(1_000, ActivityResumed, ig, "Feed"),
            ev(5_000, ActivityResumed, ig, "Reels"), // resumed before Feed is paused (multi-resume)
            ev(5_010, ActivityPaused, ig, "Feed"),
            ev(9_000, ActivityPaused, ig, "Reels"),
        )
        assertEquals(listOf(UsageSession(ig, 1_000, 9_000)), sessions)
    }

    @Test
    fun `a quick pause-resume of the same app is merged`() {
        val sessions = build(
            ev(1_000, ActivityResumed, ig),
            ev(5_000, ActivityPaused, ig),
            ev(5_000 + SessionBuilder.MERGE_GAP_MS, ActivityResumed, ig),
            ev(9_000, ActivityStopped, ig),
        )
        assertEquals(listOf(UsageSession(ig, 1_000, 9_000)), sessions)
    }

    @Test
    fun `a longer gap starts a new session`() {
        val sessions = build(
            ev(1_000, ActivityResumed, ig),
            ev(5_000, ActivityPaused, ig),
            ev(8_000, ActivityResumed, ig),
            ev(9_000, ActivityPaused, ig),
        )
        assertEquals(listOf(UsageSession(ig, 1_000, 5_000), UsageSession(ig, 8_000, 9_000)), sessions)
    }

    @Test
    fun `screen off and lock screen end sessions and are never merged across`() {
        val sessions = build(
            ev(1_000, ActivityResumed, ig),
            ev(4_000, ScreenNonInteractive),
            ev(4_500, ActivityResumed, ig), // back within the merge gap, but after screen-off
            ev(7_000, KeyguardShown),
        )
        assertEquals(listOf(UsageSession(ig, 1_000, 4_000), UsageSession(ig, 4_500, 7_000)), sessions)
    }

    @Test
    fun `shutdown ends sessions, and a startup without shutdown ends them at the last event`() {
        val clean = build(ev(1_000, ActivityResumed, ig), ev(3_000, DeviceShutdown))
        assertEquals(listOf(UsageSession(ig, 1_000, 3_000)), clean)

        val crash = build(
            ev(1_000, ActivityResumed, ig),
            ev(2_000, ActivityResumed, tt, "Feed"),
            ev(50_000, DeviceStartup), // battery died; nothing logged after 2 000
        )
        assertEquals(listOf(UsageSession(ig, 1_000, 2_000)), crash.filter { it.packageName == ig })
    }

    @Test
    fun `apps still in the foreground get an open session up to the window end`() {
        val sessions = build(ev(1_000, ActivityResumed, tt), end = 30_000)
        assertEquals(listOf(UsageSession(tt, 1_000, 30_000, isOpen = true)), sessions)
    }

    @Test
    fun `a pause without a matching resume is ignored, as at the start of a re-read window`() {
        assertEquals(emptyList(), build(ev(1_000, ActivityPaused, ig)))
    }

    @Test
    fun `events are processed in time order whatever order they arrive in`() {
        val sessions = build(ev(61_000, ActivityPaused, ig), ev(1_000, ActivityResumed, ig))
        assertEquals(listOf(UsageSession(ig, 1_000, 61_000)), sessions)
    }

    @Test
    fun `two apps in split screen are tracked independently`() {
        val sessions = build(
            ev(1_000, ActivityResumed, ig),
            ev(2_000, ActivityResumed, tt),
            ev(5_000, ActivityPaused, ig),
            ev(6_000, ActivityPaused, tt),
        )
        assertEquals(listOf(UsageSession(ig, 1_000, 5_000), UsageSession(tt, 2_000, 6_000)), sessions)
    }
}
