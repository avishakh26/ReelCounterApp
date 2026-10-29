package com.reelcounter.core.data.sync

import com.reelcounter.core.data.tracking.DailyRow
import com.reelcounter.core.data.tracking.FakeSyncPreferences
import com.reelcounter.core.data.tracking.FakeTimeSource
import com.reelcounter.core.data.tracking.FakeTrackingLocalStore
import com.reelcounter.core.data.tracking.FakeTrackingPreferences
import com.reelcounter.core.datastore.SyncState
import com.reelcounter.core.datastore.TrackingPreferences
import com.reelcounter.core.domain.auth.AuthState
import com.reelcounter.core.domain.sync.SyncResult
import com.reelcounter.core.domain.tracking.ReelEstimate
import com.reelcounter.core.model.MetricQuality
import com.reelcounter.core.network.RemoteException
import com.reelcounter.core.network.SupabaseConfig
import com.reelcounter.core.network.activity.ActivityRemoteDataSource
import com.reelcounter.core.network.activity.NetworkDailySummary
import com.reelcounter.core.testing.FakeAuthRepository
import kotlinx.coroutines.test.runTest
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ActivitySyncerTest {

    private val day = LocalDate.parse("2026-10-20")
    private val store = FakeTrackingLocalStore()
    private val remote = FakeActivityRemote()
    private val auth = FakeAuthRepository(AuthState.SignedIn("user-1", "sam@example.com"))
    private val consent = FakeTrackingPreferences(TrackingPreferences(disclosureAcceptedVersion = 2))
    private val sync = FakeSyncPreferences(SyncState(ownerUserId = "user-1", deviceId = "device-1"))

    private val syncer = ActivitySyncer(
        config = SupabaseConfig("https://project.supabase.co", "sb_publishable_x"),
        authRepository = auth,
        trackingPreferences = consent,
        syncPreferences = sync,
        store = store,
        remote = remote,
        time = FakeTimeSource(0, ZoneId.of("UTC")),
    )

    private suspend fun dirtyRow(platform: String, ms: Long = 60_000, revision: Long = 10, quality: MetricQuality = MetricQuality.DURATION_ONLY) =
        store.saveDailyRows(
            listOf(
                DailyRow(
                    date = day, platformId = platform, foregroundMs = ms, sessionCount = 1, quality = quality,
                    estimate = if (quality == MetricQuality.ESTIMATED) ReelEstimate(3, 1, 6) else null,
                    estimationVersion = null, userAdjusted = false, tzId = "UTC", isFinal = false,
                    revision = revision, dirty = true,
                ),
            ),
        )

    @Test
    fun `nothing is uploaded when signed out or without upload consent`() = runTest {
        dirtyRow("tiktok")

        auth.state.value = AuthState.SignedOut
        assertEquals(SyncResult.NotSignedIn, syncer.syncNow())

        auth.state.value = AuthState.SignedIn("user-1", null)
        consent.state.value = TrackingPreferences(disclosureAcceptedVersion = 1) // v1 said "stays on device"
        assertEquals(SyncResult.NotConsented, syncer.syncNow())

        assertTrue(remote.uploads.isEmpty())
    }

    @Test
    fun `registers the device once, uploads absolute rows and marks them synced`() = runTest {
        dirtyRow("tiktok", quality = MetricQuality.ESTIMATED)
        dirtyRow("youtube")

        assertEquals(SyncResult.Synced, syncer.syncNow())
        assertEquals(SyncResult.Synced, syncer.syncNow())

        assertEquals(listOf("device-1"), remote.registeredDevices)
        val uploaded = remote.uploads.single()
        assertEquals(setOf("tiktok", "youtube"), uploaded.map { it.platformId }.toSet())
        assertTrue(uploaded.all { it.userId == "user-1" && it.deviceId == "device-1" && it.source == "usage_stats" })
        // Duration-only rows send explicit nulls, so an old estimate on the server is cleared.
        assertNull(uploaded.first { it.platformId == "youtube" }.estimatedReels)
        assertTrue(store.dirtyRows(10).isEmpty())
    }

    @Test
    fun `a row changed during upload stays dirty for the next sync`() = runTest {
        dirtyRow("tiktok", revision = 10)
        remote.beforeUpload = { dirtyRow("tiktok", ms = 120_000, revision = 11) } // ingest ran meanwhile

        syncer.syncNow()

        assertEquals(11, store.dirtyRows(10).single().revision)
    }

    @Test
    fun `offline leaves everything dirty and asks to retry`() = runTest {
        dirtyRow("tiktok")
        remote.failure = RemoteException(RemoteException.Kind.Network)

        assertEquals(SyncResult.RetryLater, syncer.syncNow())
        assertEquals(1, store.dirtyRows(10).size)
        assertFalse(sync.current.value.deviceRegistered)
    }

    @Test
    fun `one invalid row is parked and the rest still go up`() = runTest {
        dirtyRow("instagram")
        dirtyRow("tiktok", ms = -1) // the server's CHECK constraint rejects it
        dirtyRow("youtube")

        assertEquals(SyncResult.Synced, syncer.syncNow())

        assertEquals(setOf("instagram", "youtube"), remote.accepted.map { it.platformId }.toSet())
        assertEquals("23514", store.rejected.values.single())
        assertTrue(store.dirtyRows(10).isEmpty(), "parked rows aren't retried")
    }

    @Test
    fun `data still owned by another account is never uploaded`() = runTest {
        dirtyRow("tiktok")
        sync.current.value = SyncState(ownerUserId = "someone-else", deviceId = "device-1")

        assertEquals(SyncResult.RetryLater, syncer.syncNow())
        assertTrue(remote.uploads.isEmpty())
    }
}

private class FakeActivityRemote : ActivityRemoteDataSource {
    val registeredDevices = mutableListOf<String>()
    val uploads = mutableListOf<List<NetworkDailySummary>>()
    val accepted = mutableListOf<NetworkDailySummary>()
    var failure: RemoteException? = null
    var beforeUpload: (suspend () -> Unit)? = null

    override suspend fun registerDevice(deviceId: String, userId: String) {
        failure?.let { throw it }
        registeredDevices += deviceId
    }

    override suspend fun upsertDailySummaries(rows: List<NetworkDailySummary>) {
        beforeUpload?.invoke()
        beforeUpload = null
        failure?.let { throw it }
        uploads += rows
        // Mimics the foreground_ms CHECK constraint: one bad row fails the whole statement.
        if (rows.any { it.foregroundMs < 0 }) throw RemoteException(RemoteException.Kind.Database, code = "23514")
        accepted += rows
    }
}
