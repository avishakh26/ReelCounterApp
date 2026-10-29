package com.reelcounter.core.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object TrackingWork {
    private const val PERIODIC_INGEST = "usage-ingest"

    /**
     * Schedules the hourly read. Call on every app start; unique work makes it idempotent.
     * WorkManager persists the schedule and restores it after a reboot by itself.
     * No constraints: a read is local and cheap, and the system already batches it.
     */
    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<UsageIngestWorker>(1, TimeUnit.HOURS, 15, TimeUnit.MINUTES)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC_INGEST, ExistingPeriodicWorkPolicy.UPDATE, request)
    }
}
