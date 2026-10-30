package com.reelcounter.core.data.sync

import com.reelcounter.core.data.tracking.DailyRow
import com.reelcounter.core.data.tracking.FakeDeviceIds
import com.reelcounter.core.data.tracking.FakeSyncPreferences
import com.reelcounter.core.data.tracking.FakeTimeSource
import com.reelcounter.core.data.tracking.FakeTrackingLocalStore
import com.reelcounter.core.data.tracking.FakeTrackingPreferences
import com.reelcounter.core.datastore.SyncState
import com.reelcounter.core.datastore.TrackingPreferences
import com.reelcounter.core.domain.auth.AuthState
import com.reelcounter.core.model.MetricQuality
import com.reelcounter.core.testing.FakeAuthRepository
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class AccountDataGuardTest {

    private val store = FakeTrackingLocalStore()
    private val sync = FakeSyncPreferences()
    private val tracking = FakeTrackingPreferences(TrackingPreferences(disclosureAcceptedVersion = 2))
    private var syncRequests = 0

    private fun TestScope.guard() = AccountDataGuard(
        authRepository = FakeAuthRepository(AuthState.SignedOut),
        syncPreferences = sync,
        trackingPreferences = tracking,
        store = store,
        syncScheduler = { syncRequests++ },
        time = FakeTimeSource(nowMs = 5_000, zone = ZoneId.of("UTC")),
        deviceIds = FakeDeviceIds(),
        scope = backgroundScope,
    )

    private suspend fun recordSomething() = store.saveDailyRows(
        listOf(
            DailyRow(
                LocalDate.parse("2026-10-20"), "tiktok", 60_000, 1, MetricQuality.DURATION_ONLY, null, null,
                false, "UTC", false, 1, true,
            ),
        ),
    )

    @Test
    fun `the first account adopts what is already recorded`() = runTest {
        recordSomething()
        guard().onSignedIn("alice")

        assertEquals("alice", sync.current.value.ownerUserId)
        assertEquals(1, store.rows.value.size)
        assertEquals(1, syncRequests)
    }

    @Test
    fun `the same account signing in again keeps its data`() = runTest {
        val guard = guard()
        guard.onSignedIn("alice")
        recordSomething()
        val device = sync.current.value.deviceId

        guard.onSignedIn("alice")

        assertEquals(1, store.rows.value.size)
        assertEquals(device, sync.current.value.deviceId)
    }

    @Test
    fun `after a reinstall the account gets the same device id again`() = runTest {
        guard().onSignedIn("alice")
        val first = sync.current.value.deviceId
        sync.current.value = SyncState() // a reinstall wipes the app's own storage

        guard().onSignedIn("alice")

        assertEquals(first, sync.current.value.deviceId, "so its uploads replace the earlier ones")
        assertEquals("1970-01-01", sync.current.value.coverageStartDate)
    }

    @Test
    fun `another account erases the previous account's data and consent`() = runTest {
        val guard = guard()
        guard.onSignedIn("alice")
        recordSomething()
        val aliceDevice = sync.current.value.deviceId

        guard.onSignedIn("bob")

        assertTrue(store.rows.value.isEmpty(), "Alice's usage must never reach Bob's account")
        assertEquals("bob", sync.current.value.ownerUserId)
        assertNotEquals(aliceDevice, sync.current.value.deviceId)
        assertEquals(0, tracking.state.value.disclosureAcceptedVersion, "Bob must agree to the disclosure himself")
        assertEquals(5_000L, tracking.state.value.resumedAtMs, "nothing from before Bob signed in is read")
    }
}
