package com.reelcounter.core.domain.tracking

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReelSwipeDetectorTest {

    private val tiktok = "com.zhiliaoapp.musically"
    private val youtube = "com.google.android.youtube"

    @Test
    fun `staying on one video for a minute counts it once`() {
        val detector = ReelSwipeDetector()
        // The service checks the screen about once a second.
        val counted = (0 until 60).count { second -> detector.onItemOnScreen(tiktok, itemIndex = 0, atMs = second * 1_000L) }
        assertEquals(1, counted)
    }

    @Test
    fun `flicking through twenty videos in a minute counts twenty`() {
        val detector = ReelSwipeDetector()
        val counted = (0 until 20).count { item -> detector.onItemOnScreen(tiktok, itemIndex = item, atMs = item * 3_000L) }
        assertEquals(20, counted)
    }

    @Test
    fun `swiping back to a video counts it again, staying put doesn't`() {
        val detector = ReelSwipeDetector()
        assertTrue(detector.onItemOnScreen(tiktok, 4, 0))
        assertTrue(detector.onItemOnScreen(tiktok, 5, 2_000))
        assertFalse(detector.onItemOnScreen(tiktok, 5, 3_000))
        assertTrue(detector.onItemOnScreen(tiktok, 4, 5_000))
    }

    @Test
    fun `one swipe's burst of scroll events counts once`() {
        val detector = ReelSwipeDetector()
        assertTrue(detector.onFeedScrolled(youtube, 0))
        // A swipe animates for a few hundred ms and reports several scrolls.
        assertFalse(detector.onFeedScrolled(youtube, 80))
        assertFalse(detector.onFeedScrolled(youtube, 160))
        assertFalse(detector.onFeedScrolled(youtube, 300))
        // The next swipe.
        assertTrue(detector.onFeedScrolled(youtube, 2_500))
    }

    @Test
    fun `apps are counted separately`() {
        val detector = ReelSwipeDetector()
        assertTrue(detector.onItemOnScreen(tiktok, 3, 0))
        assertTrue(detector.onItemOnScreen(youtube, 3, 1_000))
        // Back to TikTok's same video: not a new one.
        assertFalse(detector.onItemOnScreen(tiktok, 3, 2_000))
    }

    @Test
    fun `coming back after a long break counts the video on screen again`() {
        val detector = ReelSwipeDetector(sessionGapMs = 60_000)
        assertTrue(detector.onItemOnScreen(tiktok, 7, 0))
        assertFalse(detector.onItemOnScreen(tiktok, 7, 30_000))
        assertTrue(detector.onItemOnScreen(tiktok, 7, 200_000))
    }
}
