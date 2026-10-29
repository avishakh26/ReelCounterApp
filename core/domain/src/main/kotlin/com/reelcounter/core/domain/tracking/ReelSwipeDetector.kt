package com.reelcounter.core.domain.tracking

/**
 * Decides when a new short video is on screen, from what the accessibility service sees of an
 * app's short-video feed. It never sees what the video is: only which item of the feed fills the
 * screen (its position in the list) or, when an app doesn't expose positions, that the feed moved.
 *
 * - Opening the feed counts the video on screen.
 * - A different item filling the screen counts one more; the same item again doesn't, however long
 *   it plays or loops. So time spent on one video never adds reels.
 * - Without positions, a scroll that moved the feed counts once per [minScrollIntervalMs]; one swipe
 *   sends a burst of scroll events, and this collapses the burst into one reel.
 * - After [sessionGapMs] away from an app's feed, coming back counts the video on screen again.
 *
 * State is per app, so switching between apps never double-counts the video you return to.
 */
class ReelSwipeDetector(
    private val minScrollIntervalMs: Long = 700,
    private val sessionGapMs: Long = 10 * 60_000L,
) {
    private class FeedState(var item: Int?, var lastSeenMs: Long, var lastCountMs: Long)

    private val feeds = mutableMapOf<String, FeedState>()

    /** [itemIndex] (its position in the feed) fills the screen in [packageName]. True = a new video. */
    @Synchronized
    fun onItemOnScreen(packageName: String, itemIndex: Int, atMs: Long): Boolean {
        val state = feeds[packageName]
        if (state == null || atMs - state.lastSeenMs > sessionGapMs) {
            feeds[packageName] = FeedState(item = itemIndex, lastSeenMs = atMs, lastCountMs = atMs)
            return true
        }
        state.lastSeenMs = atMs
        if (state.item == itemIndex) return false
        state.item = itemIndex
        state.lastCountMs = atMs
        return true
    }

    /** The feed of [packageName] scrolled to another video, position unknown. True = count one. */
    @Synchronized
    fun onFeedScrolled(packageName: String, atMs: Long): Boolean {
        val state = feeds[packageName]
        if (state == null || atMs - state.lastSeenMs > sessionGapMs) {
            feeds[packageName] = FeedState(item = null, lastSeenMs = atMs, lastCountMs = atMs)
            return true
        }
        state.lastSeenMs = atMs
        // Positions are unknown from here on; a known one later shouldn't match a stale value.
        state.item = null
        if (atMs - state.lastCountMs < minScrollIntervalMs) return false
        state.lastCountMs = atMs
        return true
    }
}
