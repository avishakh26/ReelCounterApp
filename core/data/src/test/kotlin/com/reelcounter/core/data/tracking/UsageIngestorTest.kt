package com.reelcounter.core.data.tracking

import com.reelcounter.core.domain.tracking.PlatformCatalog
import com.reelcounter.core.domain.tracking.RefreshResult
import com.reelcounter.core.domain.tracking.UsageEvent
import com.reelcounter.core.domain.tracking.UsageEventType.ActivityPaused
import com.reelcounter.core.domain.tracking.UsageEventType.ActivityResumed
import com.reelcounter.core.model.MetricQuality
import kotlinx.coroutines.test.runTest
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UsageIngestorTest {

    private val zone = ZoneId.of("Europe/Berlin")
    private val tiktok = "com.zhiliaoapp.musically"
    private val youtube = "com.google.android.youtube"
    private val minute = 60_000L

    private fun at(text: String): Long = LocalDateTime.parse(text).atZone(zone).toInstant().toEpochMilli()
    private val today: LocalDate = LocalDate.parse("2026-10-20")

    private val store = FakeTrackingLocalStore()
    private val source = FakeUsageEventSource()
    private val access = FakeUsageAccess()
    private val prefs = FakeTrackingPreferences()
    private val time = FakeTimeSource(nowMs = at("2026-10-20T12:00"), zone = zone)
    private val ingestor = UsageIngestor(store, source, access, prefs, time)

    private fun use(pkg: String, from: String, minutes: Long) {
        val start = at(from)
        source.log += UsageEvent(start, ActivityResumed, pkg, "Main")
        source.log += UsageEvent(start + minutes * minute, ActivityPaused, pkg, "Main")
    }

    @Test
    fun `first read backfills yesterday and records today, estimating TikTok and YouTube`() = runTest {
        use(tiktok, "2026-10-19T21:00", 30)
        use(tiktok, "2026-10-20T09:00", 10)
        use(youtube, "2026-10-20T10:00", 20)

        assertEquals(RefreshResult.Updated, ingestor.ingest())

        assertEquals(30 * minute, store.row(today.minusDays(1), "tiktok")?.foregroundMs)
        val tiktokToday = assertNotNull(store.row(today, "tiktok"))
        assertEquals(10 * minute, tiktokToday.foregroundMs)
        assertEquals(MetricQuality.ESTIMATED, tiktokToday.quality)
        // 600 s × 0.95 / 21 s ≈ 27
        assertEquals(27, tiktokToday.estimate?.count)

        val youtubeToday = assertNotNull(store.row(today, "youtube"))
        assertEquals(MetricQuality.ESTIMATED, youtubeToday.quality) // decision D1, revised
        // 1200 s × 0.30 / 30 s = 12
        assertEquals(12, youtubeToday.estimate?.count)

        // Covered days get rows even for unused apps, so "0 min" differs from "no data".
        assertEquals(0L, store.row(today, "instagram")?.foregroundMs)
    }

    @Test
    fun `reading again changes nothing, so overlapping reads can't double count`() = runTest {
        use(tiktok, "2026-10-20T11:50", 5)
        ingestor.ingest()
        val first = store.row(today, "tiktok")

        time.nowMs += 5 * minute
        ingestor.ingest()
        ingestor.ingest()

        assertEquals(first?.foregroundMs, store.row(today, "tiktok")?.foregroundMs)
        assertEquals(1, store.sessions.size)
    }

    @Test
    fun `an app still open is extended by the next read`() = runTest {
        source.log += UsageEvent(at("2026-10-20T11:30"), ActivityResumed, tiktok, "Main")
        ingestor.ingest()
        assertEquals(30 * minute, store.row(today, "tiktok")?.foregroundMs)
        assertTrue(store.sessions.values.single().isOpen)

        time.nowMs = at("2026-10-20T13:00")
        source.log += UsageEvent(at("2026-10-20T12:40"), ActivityPaused, tiktok, "Main")
        ingestor.ingest()

        assertEquals(70 * minute, store.row(today, "tiktok")?.foregroundMs)
        assertEquals(false, store.sessions.values.single().isOpen)
    }

    @Test
    fun `a session across midnight counts on both days`() = runTest {
        use(tiktok, "2026-10-19T23:50", 20)
        ingestor.ingest()

        assertEquals(10 * minute, store.row(today.minusDays(1), "tiktok")?.foregroundMs)
        assertEquals(10 * minute, store.row(today, "tiktok")?.foregroundMs)
    }

    @Test
    fun `nothing is read without consent or usage access`() = runTest {
        use(tiktok, "2026-10-20T09:00", 10)
        access.granted.value = false
        assertEquals(RefreshResult.NotPermitted, ingestor.ingest())

        access.granted.value = true
        prefs.state.value = prefs.state.value.copy(disclosureAcceptedVersion = 0)
        assertEquals(RefreshResult.NotPermitted, ingestor.ingest())

        assertTrue(source.queries.isEmpty())
        assertTrue(store.rows.value.isEmpty())
    }

    @Test
    fun `time spent while paused is never recorded`() = runTest {
        use(tiktok, "2026-10-20T09:00", 10)
        ingestor.ingest()

        prefs.setPaused(true, time.nowMs)
        use(tiktok, "2026-10-20T12:30", 15) // while paused
        time.nowMs = at("2026-10-20T13:00")
        assertEquals(RefreshResult.Paused, ingestor.ingest())

        prefs.setPaused(false, time.nowMs) // resume at 13:00
        use(tiktok, "2026-10-20T13:10", 5)
        time.nowMs = at("2026-10-20T13:30")
        ingestor.ingest()

        assertEquals(15 * minute, store.row(today, "tiktok")?.foregroundMs) // 10 + 5, not the paused 15
    }

    @Test
    fun `days older than two days are frozen and never recomputed`() = runTest {
        use(tiktok, "2026-10-20T09:00", 10)
        ingestor.ingest()

        // Three days later, a re-read can't change the 20th any more.
        time.nowMs = at("2026-10-23T12:00")
        ingestor.ingest()
        val frozen = assertNotNull(store.row(today, "tiktok"))
        assertTrue(frozen.isFinal)

        store.sessions.clear() // as if raw sessions were gone
        ingestor.ingest()
        assertEquals(10 * minute, store.row(today, "tiktok")?.foregroundMs)
    }

    @Test
    fun `when the log expired between reads, the missing days are a gap, not zero`() = runTest {
        ingestor.ingest() // first read on the 20th

        // The app isn't opened for five days; the system only kept the last day of events.
        time.nowMs = at("2026-10-25T12:00")
        source.retainedFromMs = at("2026-10-24T12:00")
        use(tiktok, "2026-10-24T13:00", 10)
        ingestor.ingest()

        assertNull(store.row(LocalDate.parse("2026-10-22"), "tiktok"), "a day nobody could read must stay empty")
        assertEquals(10 * minute, store.row(LocalDate.parse("2026-10-24"), "tiktok")?.foregroundMs)
    }

    @Test
    fun `untracked platforms are neither read into sessions nor given rows`() = runTest {
        store.savePlatformSettings(PlatformSettings("youtube", isTracked = false, null, null, null), 0)
        use(youtube, "2026-10-20T10:00", 20)

        ingestor.ingest()

        assertTrue(store.sessions.values.none { it.packageName == youtube })
        assertNull(store.row(today, "youtube"))
    }

    @Test
    fun `revisions keep growing even if the clock is turned back`() = runTest {
        use(tiktok, "2026-10-20T11:00", 5)
        ingestor.ingest()
        val first = assertNotNull(store.row(today, "tiktok")).revision

        // The user sets the clock back half an hour and uses the app again; Android logs the new
        // events with the earlier clock.
        time.nowMs = at("2026-10-20T11:30")
        use(tiktok, "2026-10-20T11:22", 5)
        ingestor.ingest()

        val row = assertNotNull(store.row(today, "tiktok"))
        assertEquals(10 * minute, row.foregroundMs, "usage after the clock change is still read")
        assertTrue(row.revision > first, "the server must see this as newer")
    }

    @Test
    fun `changing estimation settings recomputes recent days only`() = runTest {
        use(youtube, "2026-10-20T10:00", 20)
        ingestor.ingest()

        store.savePlatformSettings(PlatformSettings("youtube", true, estimationEnabled = true, shortFormShare = 1.0, secondsPerReel = 30.0), 0)
        ingestor.recomputeEstimates()

        val row = assertNotNull(store.row(today, "youtube"))
        assertEquals(MetricQuality.ESTIMATED, row.quality)
        assertEquals(40, row.estimate?.count) // 1 200 s × 1.0 / 30 s
        assertTrue(row.userAdjusted)
        assertEquals(PlatformCatalog.YouTube.profile.version, row.estimationVersion)
    }


    @Test
    fun `with counting on before the day's use, the day shows the counted reels`() = runTest {
        prefs.state.value = prefs.state.value.copy(countingSinceMs = at("2026-10-20T08:00"))
        use(tiktok, "2026-10-20T09:00", 10)
        repeat(37) { store.addReelView(today, "tiktok") }

        ingestor.ingest()

        val row = assertNotNull(store.row(today, "tiktok"))
        assertEquals(MetricQuality.COUNTED, row.quality)
        assertEquals(37, row.estimate?.count)
        assertEquals(10 * minute, row.foregroundMs)
        // A tracked app with no use while counting is an exact zero.
        assertEquals(MetricQuality.COUNTED, store.row(today, "instagram")?.quality)
        assertEquals(0, store.row(today, "instagram")?.estimate?.count)
    }

    @Test
    fun `counting turned on after the day's use began falls back to the estimate`() = runTest {
        use(tiktok, "2026-10-20T09:00", 10)
        prefs.state.value = prefs.state.value.copy(countingSinceMs = at("2026-10-20T09:05"))
        repeat(5) { store.addReelView(today, "tiktok") }

        ingestor.ingest()

        val row = assertNotNull(store.row(today, "tiktok"))
        // Part of the use wasn't counted, so 5 would be too low: show the estimate instead.
        assertEquals(MetricQuality.ESTIMATED, row.quality)
        assertEquals(27, row.estimate?.count)
    }

    @Test
    fun `changing estimation settings keeps counted days counted`() = runTest {
        prefs.state.value = prefs.state.value.copy(countingSinceMs = at("2026-10-20T08:00"))
        use(tiktok, "2026-10-20T09:00", 10)
        repeat(12) { store.addReelView(today, "tiktok") }
        ingestor.ingest()

        store.savePlatformSettings(PlatformSettings("tiktok", true, estimationEnabled = true, shortFormShare = 1.0, secondsPerReel = 5.0), 0)
        ingestor.recomputeEstimates()

        val row = assertNotNull(store.row(today, "tiktok"))
        assertEquals(MetricQuality.COUNTED, row.quality)
        assertEquals(12, row.estimate?.count)
    }
}
