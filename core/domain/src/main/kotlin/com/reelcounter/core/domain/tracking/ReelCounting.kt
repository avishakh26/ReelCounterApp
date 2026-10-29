package com.reelcounter.core.domain.tracking

import kotlinx.coroutines.flow.Flow

/**
 * Receives what ReelCounter's accessibility service observes. The service never passes on what
 * was watched: only that a new video filled the screen, in which app, and when.
 */
interface ReelCountingRecorder {
    /** The counting service connected and is running from [atMs]. */
    suspend fun onCountingStarted(atMs: Long)

    /** The counting service was turned off or disconnected. */
    suspend fun onCountingStopped()

    /** A new video filled the screen in [packageName]'s short-video feed at [atMs]. */
    suspend fun onReelViewed(packageName: String, atMs: Long)
}

/** Whether the user has turned on ReelCounter's reel-counting accessibility service. */
interface ReelCountingStatus {
    val isEnabled: Flow<Boolean>

    fun isEnabledNow(): Boolean
}
