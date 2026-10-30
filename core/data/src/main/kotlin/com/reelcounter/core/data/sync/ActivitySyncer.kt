package com.reelcounter.core.data.sync

import com.reelcounter.core.data.tracking.DailyRow
import com.reelcounter.core.data.tracking.RowVersion
import com.reelcounter.core.data.tracking.TrackingLocalStore
import com.reelcounter.core.datastore.SyncPreferencesStore
import com.reelcounter.core.datastore.TrackingPreferencesStore
import com.reelcounter.core.domain.auth.AuthRepository
import com.reelcounter.core.domain.auth.AuthState
import com.reelcounter.core.domain.sync.ActivitySync
import com.reelcounter.core.domain.sync.SyncResult
import com.reelcounter.core.domain.tracking.DayBucketer
import com.reelcounter.core.domain.tracking.TimeSource
import com.reelcounter.core.domain.tracking.UPLOAD_CONSENT_VERSION
import com.reelcounter.core.network.RemoteException
import com.reelcounter.core.network.SupabaseConfig
import com.reelcounter.core.network.activity.ActivityRemoteDataSource
import com.reelcounter.core.network.activity.NetworkDailySummary
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Uploads this device's changed daily rows (docs/phase-1/01-system-architecture.md §2b).
 *
 * - Payloads are absolute daily totals, never deltas, upserted on a unique key: retrying is
 *   always safe.
 * - The server keeps the higher `revision`, so an older upload arriving late can't overwrite
 *   newer figures.
 * - A row is marked synced only if it wasn't changed again while it was uploading; otherwise it
 *   stays dirty and goes up in the next batch.
 * - A batch the server rejects outright is retried row by row. A row that is itself invalid is
 *   parked with its error instead of being retried forever; the rest go through.
 */
@Singleton
internal class ActivitySyncer @Inject constructor(
    private val config: SupabaseConfig,
    private val authRepository: AuthRepository,
    private val trackingPreferences: TrackingPreferencesStore,
    private val syncPreferences: SyncPreferencesStore,
    private val store: TrackingLocalStore,
    private val remote: ActivityRemoteDataSource,
    private val time: TimeSource,
) : ActivitySync {

    private val mutex = Mutex()

    override suspend fun syncNow(): SyncResult = mutex.withLock {
        if (!config.isConfigured) return SyncResult.NotConfigured
        val userId = (authRepository.authState.value as? AuthState.SignedIn)?.userId ?: return SyncResult.NotSignedIn
        if (trackingPreferences.preferences.first().disclosureAcceptedVersion < UPLOAD_CONSENT_VERSION) {
            return SyncResult.NotConsented
        }
        val sync = syncPreferences.state.first()
        // Local data belongs to another account until AccountDataGuard has handed it over.
        if (sync.ownerUserId != userId) return SyncResult.RetryLater
        val deviceId = sync.deviceId ?: return SyncResult.RetryLater

        try {
            if (!sync.deviceRegistered) {
                remote.registerDevice(deviceId, userId)
                syncPreferences.setDeviceRegistered(true)
            }
            // Bounded, in case rows keep changing while they upload; leftovers go in the next sync.
            val coverageStart = sync.coverageStartDate?.let(LocalDate::parse)
            for (attempt in 1..MAX_BATCHES) {
                val batch = store.dirtyRows(BATCH_SIZE)
                if (batch.isEmpty()) break
                val toUpload = withoutDaysOnServer(batch, userId, deviceId, coverageStart)
                if (toUpload.isNotEmpty()) upload(toUpload, userId, deviceId)
                if (batch.size < BATCH_SIZE) break
            }
            syncPreferences.setLastSync(time.nowMs())
            SyncResult.Synced
        } catch (e: RemoteException) {
            if (e.kind == RemoteException.Kind.Database && e.code == PERMISSION_DENIED) {
                // The device id isn't ours on the server (e.g. reinstall race): start over with a new one.
                syncPreferences.switchOwner(userId, UUID.randomUUID().toString(), sync.coverageStartDate ?: today())
            }
            SyncResult.RetryLater
        }
    }

    /**
     * Days before this install began recording were read back from the phone's usage history. If
     * the server already has them from another device id (an earlier install of the app here), they
     * are marked synced instead of uploaded, so friends don't see those days counted twice.
     */
    private suspend fun withoutDaysOnServer(
        batch: List<DailyRow>,
        userId: String,
        deviceId: String,
        coverageStart: LocalDate?,
    ): List<DailyRow> {
        val readBack = batch.filter { coverageStart != null && it.date < coverageStart }
        if (readBack.isEmpty()) return batch
        val onServer = remote.daysFromOtherDevices(userId, deviceId, readBack.map { it.date.toString() }.toSet())
        val skip = readBack.filter { (it.date.toString() to it.platformId) in onServer }
        if (skip.isNotEmpty()) store.markSynced(skip.map { it.version() })
        return batch - skip.toSet()
    }

    private fun today(): String = DayBucketer.dateOf(time.nowMs(), time.zone()).toString()

    private suspend fun upload(batch: List<DailyRow>, userId: String, deviceId: String) {
        try {
            remote.upsertDailySummaries(batch.map { it.toNetwork(userId, deviceId) })
            store.markSynced(batch.map { it.version() })
        } catch (e: RemoteException) {
            if (!e.isInvalidData()) throw e
            // Find the bad row(s); upload the others.
            for (row in batch) {
                try {
                    remote.upsertDailySummaries(listOf(row.toNetwork(userId, deviceId)))
                    store.markSynced(listOf(row.version()))
                } catch (rowError: RemoteException) {
                    if (!rowError.isInvalidData()) throw rowError
                    store.markRejected(row.version(), rowError.code ?: "invalid")
                }
            }
        }
    }

    private companion object {
        const val BATCH_SIZE = 200
        const val MAX_BATCHES = 20
        const val PERMISSION_DENIED = "42501"
    }
}

/** Constraint and data errors (SQLSTATE classes 22 and 23) mean this row will never be accepted as is. */
private fun RemoteException.isInvalidData(): Boolean =
    kind == RemoteException.Kind.Database && (code?.startsWith("22") == true || code?.startsWith("23") == true)

private fun DailyRow.version() = RowVersion(date, platformId, revision)

private fun DailyRow.toNetwork(userId: String, deviceId: String) = NetworkDailySummary(
    userId = userId,
    deviceId = deviceId,
    platformId = platformId,
    activityDate = date.toString(),
    tzId = tzId,
    foregroundMs = foregroundMs,
    sessionCount = sessionCount,
    estimatedReels = estimate?.count,
    estimatedReelsLow = estimate?.low,
    estimatedReelsHigh = estimate?.high,
    quality = quality.name,
    source = "usage_stats",
    estimationVersion = estimationVersion,
    userAdjusted = userAdjusted,
    isFinal = isFinal,
    revision = revision,
)
