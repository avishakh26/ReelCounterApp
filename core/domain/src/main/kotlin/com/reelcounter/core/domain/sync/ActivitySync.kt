package com.reelcounter.core.domain.sync

/** Uploads this device's changed daily rows to the user's account. */
interface ActivitySync {
    suspend fun syncNow(): SyncResult
}

enum class SyncResult {
    Synced,
    NotSignedIn,

    /** The user hasn't agreed to a disclosure that covers uploading. */
    NotConsented,
    NotConfigured,

    /** A temporary problem (offline, server error); try again with backoff. */
    RetryLater,
}

/** Asks for a sync to run when the device is online. Implemented with WorkManager. */
fun interface SyncScheduler {
    fun requestSync()
}
