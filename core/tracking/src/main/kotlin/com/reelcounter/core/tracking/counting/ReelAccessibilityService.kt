package com.reelcounter.core.tracking.counting

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction
import com.reelcounter.core.common.dispatchers.ApplicationScope
import com.reelcounter.core.domain.tracking.PlatformCatalog
import com.reelcounter.core.domain.tracking.ReelCountingRecorder
import com.reelcounter.core.domain.tracking.ReelSwipeDetector
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Counts short videos one by one in TikTok, Instagram Reels, YouTube Shorts and Facebook Reels.
 *
 * It listens only to those apps (see res/xml/core_tracking_reel_counter_accessibility.xml) and looks only at the
 * layout of their short-video feed: view ids, sizes, list positions and scroll direction. It never
 * reads text, captions, names, messages or the video, and never stores anything but a count.
 *
 * Detection follows what each app exposes (and can change when an app updates):
 * - YouTube Shorts: the `reel_recycler` list; YouTube often sends no scroll event, so the screen is
 *   also checked about once a second while YouTube is in front.
 * - Instagram Reels: `clips_viewer_view_pager` only, so the home feed, Stories and tabs never count.
 * - TikTok: the vertical `viewpager` of the For You / Following feed.
 * - Facebook: no stable ids, so a full-screen vertical feed showing one full-screen item at a time.
 * [ReelSwipeDetector] turns these observations into counts.
 */
@AndroidEntryPoint
class ReelAccessibilityService : AccessibilityService() {

    @Inject lateinit var recorder: ReelCountingRecorder

    @Inject @ApplicationScope
    lateinit var scope: CoroutineScope

    private val detector = ReelSwipeDetector()
    private val handler = Handler(Looper.getMainLooper())
    private var polling = false
    private var lastContentCheckMs = 0L

    private val poll = object : Runnable {
        override fun run() {
            if (!checkScreen()) {
                polling = false
                return
            }
            handler.postDelayed(this, POLL_INTERVAL_MS)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        val now = System.currentTimeMillis()
        scope.launch { recorder.onCountingStarted(now) }
    }

    override fun onUnbind(intent: Intent?): Boolean {
        handler.removeCallbacks(poll)
        polling = false
        scope.launch { recorder.onCountingStopped() }
        return super.onUnbind(intent)
    }

    override fun onInterrupt() = Unit

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val packageName = event.packageName?.toString() ?: return
        if (packageName !in WATCHED_PACKAGES) return
        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> onScrolled(event, packageName)
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> startPolling()
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                // Content changes arrive in floods while a video plays; look at most twice a second.
                val now = System.currentTimeMillis()
                if (now - lastContentCheckMs >= CONTENT_CHECK_INTERVAL_MS) {
                    lastContentCheckMs = now
                    startPolling()
                }
            }
            else -> Unit
        }
    }

    private fun startPolling() {
        if (polling) return
        polling = true
        handler.post(poll)
    }

    /** Looks for the feed on screen and counts a new item. False once a watched app isn't in front. */
    private fun checkScreen(): Boolean {
        val root = rootInActiveWindow ?: return false
        val packageName = root.packageName?.toString() ?: return false
        if (packageName !in WATCHED_PACKAGES) return false
        val feed = findFeed(root, packageName) ?: return true
        val item = settledItem(feed) ?: return true
        if (detector.onItemOnScreen(packageName, item, System.currentTimeMillis())) record(packageName)
        return true
    }

    private fun onScrolled(event: AccessibilityEvent, packageName: String) {
        val source = event.source ?: return
        if (!isFeed(source, packageName)) return
        val now = System.currentTimeMillis()
        val item = settledItem(source)
            ?: event.fromIndex.takeIf { it >= 0 && it == event.toIndex }
        val counted = when {
            item != null -> detector.onItemOnScreen(packageName, item, now)
            movedVertically(event) -> detector.onFeedScrolled(packageName, now)
            else -> false
        }
        if (counted) record(packageName)
        startPolling()
    }

    private fun record(packageName: String) {
        val now = System.currentTimeMillis()
        scope.launch { recorder.onReelViewed(packageName, now) }
    }

    private fun findFeed(root: AccessibilityNodeInfo, packageName: String): AccessibilityNodeInfo? {
        FEED_IDS[packageName].orEmpty().forEach { id ->
            root.findAccessibilityNodeInfosByViewId("$packageName:id/$id")
                .firstOrNull { it.isVisibleToUser }
                ?.let { return it }
        }
        return if (packageName in GENERIC_FEED_PACKAGES) findFullScreenVerticalFeed(root) else null
    }

    private fun isFeed(node: AccessibilityNodeInfo, packageName: String): Boolean {
        val id = node.viewIdResourceName?.substringAfter(":id/")
        if (id != null && id in FEED_IDS[packageName].orEmpty()) return true
        return packageName in GENERIC_FEED_PACKAGES && isFullScreenVerticalFeed(node)
    }

    /** Breadth-first, bounded: a full-screen, vertically scrolling list showing one full-screen item. */
    private fun findFullScreenVerticalFeed(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>().apply { add(root) }
        var visited = 0
        while (queue.isNotEmpty() && visited < MAX_NODES_SEARCHED) {
            val node = queue.removeFirst()
            visited++
            if (isFullScreenVerticalFeed(node)) return node
            for (i in 0 until node.childCount) node.getChild(i)?.let(queue::addLast)
        }
        return null
    }

    private fun isFullScreenVerticalFeed(node: AccessibilityNodeInfo): Boolean {
        if (!node.isScrollable || !node.isVisibleToUser) return false
        if (visibleHeight(node) < screenHeight() * FULL_SCREEN_FRACTION) return false
        // Horizontal pagers (Stories, tabs) offer left/right paging; skip them.
        val actions = node.actionList
        val horizontal = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            actions.any { it.id == AccessibilityAction.ACTION_PAGE_LEFT.id || it.id == AccessibilityAction.ACTION_PAGE_RIGHT.id } ||
            actions.any { it.id == AccessibilityAction.ACTION_SCROLL_LEFT.id || it.id == AccessibilityAction.ACTION_SCROLL_RIGHT.id }
        return !horizontal && settledItem(node) != null
    }

    /**
     * The position of the feed item that fills the screen, once a swipe has settled; null while
     * between two videos or when the app doesn't report positions.
     */
    private fun settledItem(feed: AccessibilityNodeInfo): Int? {
        var list = feed
        // ViewPager2 wraps its pages in an inner list; look at that one.
        if (list.childCount == 1) {
            val inner = list.getChild(0)
            if (inner != null && inner.isScrollable) list = inner
        }
        val feedHeight = visibleHeight(list).takeIf { it > 0 } ?: return null
        for (i in 0 until list.childCount) {
            val child = list.getChild(i) ?: continue
            if (visibleHeight(child) >= feedHeight * SETTLED_FRACTION) {
                return child.collectionItemInfo?.rowIndex?.takeIf { it >= 0 }
            }
        }
        return null
    }

    private fun movedVertically(event: AccessibilityEvent): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) event.scrollDeltaY != 0 else event.scrollY >= 0

    private fun visibleHeight(node: AccessibilityNodeInfo): Int {
        val rect = Rect()
        node.getBoundsInScreen(rect)
        return rect.height().coerceAtLeast(0)
    }

    private fun screenHeight(): Int = resources.displayMetrics.heightPixels

    private companion object {
        val WATCHED_PACKAGES: Set<String> = PlatformCatalog.all.flatMap { it.packages }.toSet()

        /** Each app's short-video feed, by view id (without the "package:id/" prefix). */
        val FEED_IDS: Map<String, List<String>> = buildMap {
            put("com.google.android.youtube", listOf("reel_recycler"))
            put("com.instagram.android", listOf("clips_viewer_view_pager"))
            put("com.instagram.lite", listOf("clips_viewer_view_pager"))
            PlatformCatalog.TikTok.packages.forEach { put(it, listOf("viewpager")) }
        }

        /** Apps without a stable feed id, recognized by the feed's shape instead. */
        val GENERIC_FEED_PACKAGES: Set<String> = PlatformCatalog.Facebook.packages

        const val POLL_INTERVAL_MS = 1_000L
        const val CONTENT_CHECK_INTERVAL_MS = 500L
        const val MAX_NODES_SEARCHED = 400
        const val FULL_SCREEN_FRACTION = 0.7
        const val SETTLED_FRACTION = 0.85
    }
}
