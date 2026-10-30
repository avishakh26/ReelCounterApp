package com.reelcounter.core.data.tracking

import com.reelcounter.core.domain.tracking.TrackingStatus
import com.reelcounter.core.domain.tracking.UsageEvent
import com.reelcounter.core.domain.tracking.UsageEventType.ActivityPaused
import com.reelcounter.core.domain.tracking.UsageEventType.ActivityResumed
import com.reelcounter.core.testing.FakeReelCountingStatus
import com.reelcounter.core.testing.FakeTrackingRepository
import kotlinx.coroutines.test.runTest
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals

class DefaultReelCountingRecorderTest {

    private val zone = ZoneId.of("Europe/Berlin")
    private fun at(text: String): Long = LocalDateTime.parse(text).atZone(zone).toInstant().toEpochMilli()

    private val store = FakeTrackingLocalStore()
    private val source = FakeUsageEventSource()
    private val prefs = FakeTrackingPreferences()
    private val time = FakeTimeSource(nowMs = at("2026-10-20T12:00"), zone = zone)
    private val ingestor = UsageIngestor(store, source, FakeUsageAccess(), prefs, time, FakeInstalledApps())

    private fun kotlinx.coroutines.test.TestScope.recorder() = DefaultReelCountingRecorder(
        store, prefs, time, ingestor, FakeTrackingRepository(TrackingStatus.Active), FakeReelCountingStatus(enabled = true), backgroundScope,
    )

    @Test
    fun `a rebind with no tracked app used in between keeps the counting run`() = runTest {
        val recorder = recorder()
        recorder.onCountingStarted(at("2026-10-20T08:00"))
        time.nowMs = at("2026-10-20T11:00")
        recorder.onCountingStopped()

        recorder.onCountingStarted(at("2026-10-20T11:01"))

        assertEquals(at("2026-10-20T08:00"), prefs.state.value.activeCountingSinceMs)
    }

    @Test
    fun `a rebind after a tracked app was used in the gap restarts the run`() = runTest {
        val recorder = recorder()
        recorder.onCountingStarted(at("2026-10-20T08:00"))
        time.nowMs = at("2026-10-20T10:00")
        recorder.onCountingStopped()
        // TikTok was used while counting was off: those reels were never counted.
        source.log += UsageEvent(at("2026-10-20T10:10"), ActivityResumed, "com.zhiliaoapp.musically", "Main")
        source.log += UsageEvent(at("2026-10-20T10:20"), ActivityPaused, "com.zhiliaoapp.musically", "Main")

        time.nowMs = at("2026-10-20T11:00")
        recorder.onCountingStarted(at("2026-10-20T11:00"))

        assertEquals(at("2026-10-20T11:00"), prefs.state.value.activeCountingSinceMs)
    }

    @Test
    fun `while stopped, counting isn't active`() = runTest {
        val recorder = recorder()
        recorder.onCountingStarted(at("2026-10-20T08:00"))
        recorder.onCountingStopped()

        assertEquals(null, prefs.state.value.activeCountingSinceMs)
    }

    @Test
    fun `counted reels are stored per day and app`() = runTest {
        val recorder = recorder()
        repeat(3) { recorder.onReelViewed("com.google.android.youtube", at("2026-10-20T09:00")) }
        recorder.onReelViewed("com.example.other", at("2026-10-20T09:00"))

        assertEquals(mapOf((LocalDate.parse("2026-10-20") to "youtube") to 3), store.reels.toMap())
    }
}
