package com.reelcounter.core.domain.tracking

/**
 * Decides when a short video counts as watched, from what the accessibility service sees of an
 * app's short-video feed. It never sees what the video is: only which item of the feed fills the
 * screen (its position in the list) or, when an app doesn't expose positions, that the feed moved.
 *
 * - A video counts once it has stayed on screen for [minWatchMs]. Swiping past it sooner (a quick
 *   skip, or flicking through several videos in one go) doesn't count it.
 * - It counts once, however long it plays or loops. Swiping back to it later counts it again.
 * - Without positions, a scroll means a new video; the scroll events of one swipe keep moving its
 *   start time, so the video counts once the feed has been still for [minWatchMs].
 * - After [sessionGapMs] away from an app's feed, coming back to the same video counts it again.
 *
 * The caller reports what it sees and calls [commitDue] regularly (the service checks about once a
 * second while a watched app is in front), and [onFeedHidden] when the feed leaves the screen, so a
 * video that was on screen too briefly is dropped rather than counted later.
 *
 * State is per app, so switching between apps never double-counts the video you return to.
 */
class ReelSwipeDetector(
    private val minWatchMs: Long = DEFAULT_MIN_WATCH_MS,
    private val sessionGapMs: Long = 10 * 60_000L,
) {
    init {
        require(minWatchMs >= 0) { "minWatchMs must not be negative: $minWatchMs" }
    }

    /**
     * The video on screen in one app's feed: its position ([item], null when unknown), when it
     * came on screen, and whether it has been counted yet.
     */
    private class FeedState(var item: Int?, var shownAtMs: Long, var lastSeenMs: Long, var counted: Boolean)

    private val feeds = mutableMapOf<String, FeedState>()

    /**
     * [itemIndex] (its position in the feed) fills the screen in [packageName].
     * True = one video to count now (the one on screen, or the one just left, reached [minWatchMs]).
     */
    @Synchronized
    fun onItemOnScreen(packageName: String, itemIndex: Int, atMs: Long): Boolean {
        val state = feeds[packageName]
        if (state == null || atMs - state.lastSeenMs > sessionGapMs) {
            feeds[packageName] = FeedState(item = itemIndex, shownAtMs = atMs, lastSeenMs = atMs, counted = false)
            return minWatchMs == 0L && markCounted(packageName)
        }
        if (state.item == itemIndex) {
            state.lastSeenMs = atMs
            return commitIfDue(state, atMs)
        }
        // A different video: the one we left counts if it stayed long enough.
        val leftCounts = commitIfDue(state, atMs)
        state.item = itemIndex
        state.shownAtMs = atMs
        state.lastSeenMs = atMs
        state.counted = false
        return leftCounts || commitIfDue(state, atMs)
    }

    /**
     * The feed of [packageName] scrolled to another video, position unknown.
     * True = one video to count now (the one scrolled away from had reached [minWatchMs]).
     */
    @Synchronized
    fun onFeedScrolled(packageName: String, atMs: Long): Boolean {
        val state = feeds[packageName]
        if (state == null || atMs - state.lastSeenMs > sessionGapMs) {
            feeds[packageName] = FeedState(item = null, shownAtMs = atMs, lastSeenMs = atMs, counted = false)
            return minWatchMs == 0L && markCounted(packageName)
        }
        val leftCounts = commitIfDue(state, atMs)
        // Positions are unknown from here on; a known one later shouldn't match a stale value.
        state.item = null
        // Every scroll event of a swipe restarts the clock, so the swipe itself isn't watch time.
        state.shownAtMs = atMs
        state.lastSeenMs = atMs
        state.counted = false
        return leftCounts || commitIfDue(state, atMs)
    }

    /**
     * The short-video feed of [packageName] is no longer on screen (another screen of the app, or
     * the app left the front). The video on screen counts if it stayed long enough; otherwise it's
     * dropped. True = one video to count now.
     */
    @Synchronized
    fun onFeedHidden(packageName: String, atMs: Long): Boolean {
        val state = feeds[packageName] ?: return false
        val counts = commitIfDue(state, atMs)
        // Seen-and-counted stays remembered (coming straight back to it doesn't count it again);
        // a video that was too brief is forgotten, so returning to it starts its clock afresh.
        if (!state.counted) feeds.remove(packageName)
        return counts
    }

    /** Apps whose video on screen has just reached [minWatchMs]: count one for each. */
    @Synchronized
    fun commitDue(atMs: Long): List<String> =
        feeds.filter { (_, state) -> commitIfDue(state, atMs) }.keys.toList()

    private fun commitIfDue(state: FeedState, atMs: Long): Boolean {
        if (state.counted || atMs - state.shownAtMs < minWatchMs) return false
        state.counted = true
        return true
    }

    private fun markCounted(packageName: String): Boolean {
        feeds[packageName]?.counted = true
        return true
    }

    companion object {
        /** How long a video must stay on screen to count as watched. */
        const val DEFAULT_MIN_WATCH_MS = 3_000L
    }
}
