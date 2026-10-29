package com.reelcounter.core.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.reelcounter.core.domain.sync.ActivitySync
import com.reelcounter.core.domain.sync.SyncResult
import com.reelcounter.core.domain.sync.SyncScheduler
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/** Uploads changed daily rows; runs only with a network and backs off exponentially on failure. */
@HiltWorker
internal class ActivitySyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val activitySync: ActivitySync,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = try {
        when (activitySync.syncNow()) {
            SyncResult.RetryLater -> Result.retry()
            // Not signed in, no consent or no backend: nothing to do until that changes, and
            // whatever changes it (sign-in, consent) requests a new sync.
            SyncResult.Synced, SyncResult.NotSignedIn, SyncResult.NotConsented, SyncResult.NotConfigured -> Result.success()
        }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        Result.retry()
    }
}

internal class WorkManagerSyncScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : SyncScheduler {

    override fun requestSync() {
        val request = OneTimeWorkRequestBuilder<ActivitySyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        // APPEND_OR_REPLACE: a request during a running sync queues one more run, so rows written
        // mid-upload aren't left waiting; a failed chain is replaced rather than blocking forever.
        WorkManager.getInstance(context).enqueueUniqueWork(SYNC_WORK, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    private companion object {
        const val SYNC_WORK = "activity-sync"
    }
}
