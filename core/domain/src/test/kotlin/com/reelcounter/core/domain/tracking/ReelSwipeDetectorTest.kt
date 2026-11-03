package com.reelcounter.core.domain.tracking

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReelSwipeDetectorTest {

    private val tiktok = "com.zhiliaoapp.musically"
    private val youtube = "com.google.android.youtube"

    /** Like the service: one look at the screen per second, counting what [onSecond] and commitDue report. */
    private fun ReelSwipeDetector.watch(seconds: IntRange, onSecond: ReelSwipeDetector.(atMs: Long) -> Boolean): Int =
        seconds.sumOf { second ->
            val atMs = second * 1_000L
            (if (onSecond(atMs)) 1 else 0) + commitDue(atMs).size
        }

    @Test
    fun `staying on one video for a minute counts it once`() {
        val detector = ReelSwipeDetector()
        val counted = detector.watch(0 until 60) { onItemOnScreen(tiktok, itemIndex = 0, atMs = it) }
        assertEquals(1, counted)
    }

    @Test
    fun `a video counts only once it has been on screen for three seconds`() {
        val detector = ReelSwipeDetector()
        assertFalse(detector.onItemOnScreen(tiktok, 0, 0))
        assertFalse(detector.onItemOnScreen(tiktok, 0, 2_999))
        assertEquals(emptyList(), detector.commitDue(2_999))
        assertEquals(listOf(tiktok), detector.commitDue(3_000))
        // Already counted.
        assertEquals(emptyList(), detector.commitDue(10_000))
        assertFalse(detector.onItemOnScreen(tiktok, 0, 11_000))
    }

    @Test
    fun `quick swipes past videos don't count them`() {
        val detector = ReelSwipeDetector()
        // Flicking through ten videos, one a second, then stopping on the eleventh.
        var counted = 0
        for (item in 0 until 10) {
            if (detector.onItemOnScreen(tiktok, item, item * 1_000L)) counted++
            counted += detector.commitDue(item * 1_000L).size
        }
        counted += detector.watch(10 until 20) { onItemOnScreen(tiktok, itemIndex = 10, atMs = it) }
        assertEquals(1, counted)
    }

    @Test
    fun `watching twenty videos for four seconds each counts twenty`() {
        val detector = ReelSwipeDetector()
        val counted = detector.watch(0 until 80) { onItemOnScreen(tiktok, itemIndex = (it / 4_000).toInt(), atMs = it) }
        assertEquals(20, counted)
    }

    @Test
    fun `leaving a video that reached the threshold counts it, even between screen checks`() {
        val detector = ReelSwipeDetector()
        assertFalse(detector.onItemOnScreen(tiktok, 4, 0))
        // The next look comes after the swipe: video 4 was on screen 3.5 s.
        assertTrue(detector.onItemOnScreen(tiktok, 5, 3_500))
    }

    @Test
    fun `swiping back to a video counts it again, staying put doesn't`() {
        val detector = ReelSwipeDetector()
        assertEquals(1, detector.watch(0..4) { onItemOnScreen(tiktok, 4, it) })
        assertEquals(1, detector.watch(5..9) { onItemOnScreen(tiktok, 5, it) })
        assertEquals(1, detector.watch(10..14) { onItemOnScreen(tiktok, 4, it) })
    }

    @Test
    fun `one swipe's burst of scroll events counts once, after the feed is still`() {
        val detector = ReelSwipeDetector()
        assertFalse(detector.onFeedScrolled(youtube, 0))
        // A swipe animates for a few hundred ms and reports several scrolls.
        assertFalse(detector.onFeedScrolled(youtube, 80))
        assertFalse(detector.onFeedScrolled(youtube, 160))
        assertFalse(detector.onFeedScrolled(youtube, 300))
        assertEquals(emptyList(), detector.commitDue(3_000))
        assertEquals(listOf(youtube), detector.commitDue(3_300))
        // The next swipe, after watching: nothing more until that video is watched too.
        assertFalse(detector.onFeedScrolled(youtube, 8_000))
        assertEquals(listOf(youtube), detector.commitDue(11_000))
    }

    @Test
    fun `leaving the feed too soon drops the video`() {
        val detector = ReelSwipeDetector()
        assertFalse(detector.onItemOnScreen(tiktok, 2, 0))
        assertFalse(detector.onFeedHidden(tiktok, 2_000))
        assertEquals(emptyList(), detector.commitDue(60_000))
        // Back on the same video: its clock starts again.
        assertFalse(detector.onItemOnScreen(tiktok, 2, 70_000))
        assertEquals(listOf(tiktok), detector.commitDue(73_000))
    }

    @Test
    fun `leaving the feed after watching long enough counts the video once`() {
        val detector = ReelSwipeDetector()
        assertFalse(detector.onItemOnScreen(tiktok, 2, 0))
        assertTrue(detector.onFeedHidden(tiktok, 3_200))
        // Coming straight back to it isn't a new video.
        assertFalse(detector.onItemOnScreen(tiktok, 2, 5_000))
        assertEquals(emptyList(), detector.commitDue(9_000))
    }

    @Test
    fun `apps are counted separately`() {
        val detector = ReelSwipeDetector()
        assertEquals(1, detector.watch(0..3) { onItemOnScreen(tiktok, 3, it) })
        assertEquals(1, detector.watch(4..7) { onItemOnScreen(youtube, 3, it) })
        // Back to TikTok's same video: not a new one.
        assertEquals(0, detector.watch(8..12) { onItemOnScreen(tiktok, 3, it) })
    }

    @Test
    fun `coming back after a long break counts the video on screen again`() {
        val detector = ReelSwipeDetector(sessionGapMs = 60_000)
        assertEquals(1, detector.watch(0..30) { onItemOnScreen(tiktok, 7, it) })
        assertEquals(1, detector.watch(200..205) { onItemOnScreen(tiktok, 7, it) })
    }

    @Test
    fun `with no threshold a video counts as soon as it fills the screen`() {
        val detector = ReelSwipeDetector(minWatchMs = 0)
        assertTrue(detector.onItemOnScreen(tiktok, 0, 0))
        assertTrue(detector.onItemOnScreen(tiktok, 1, 100))
        assertFalse(detector.onItemOnScreen(tiktok, 1, 200))
    }
}
