package com.reelcounter.core.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.reelcounter.core.domain.tracking.TrackingRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException

/**
 * Reads the usage log in the background. It only affects freshness, not correctness: the log is
 * kept by the system, so a run delayed by Doze or an OEM battery manager just catches up later
 * (docs/phase-1/04-technical-limitations.md L5).
 */
@HiltWorker
internal class UsageIngestWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val trackingRepository: TrackingRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = try {
        // No permission, paused or locked are normal outcomes, not failures.
        trackingRepository.refresh()
        Result.success()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
    }

    private companion object {
        const val MAX_ATTEMPTS = 3
    }
}
