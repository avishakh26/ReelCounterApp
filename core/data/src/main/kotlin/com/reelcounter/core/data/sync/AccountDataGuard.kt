package com.reelcounter.core.data.sync

import com.reelcounter.core.common.AppInitializer
import com.reelcounter.core.common.dispatchers.ApplicationScope
import com.reelcounter.core.data.tracking.TrackingLocalStore
import com.reelcounter.core.datastore.SyncPreferencesStore
import com.reelcounter.core.datastore.TrackingPreferencesStore
import com.reelcounter.core.domain.auth.AuthRepository
import com.reelcounter.core.domain.auth.AuthState
import com.reelcounter.core.domain.sync.SyncScheduler
import com.reelcounter.core.domain.tracking.TimeSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps one account's usage from ever reaching another account on a shared phone.
 *
 * - The first account to sign in adopts what's already recorded on this device.
 * - If a different account signs in later, the previous account's local tracking data and
 *   tracking consent are erased. The new account gets a fresh device id and must agree to the
 *   disclosure itself, and only usage from that moment on is recorded for it.
 *
 * Every sign-in also requests a sync, so anything recorded while signed out goes up.
 */
@Singleton
internal class AccountDataGuard @Inject constructor(
    private val authRepository: AuthRepository,
    private val syncPreferences: SyncPreferencesStore,
    private val trackingPreferences: TrackingPreferencesStore,
    private val store: TrackingLocalStore,
    private val syncScheduler: SyncScheduler,
    private val time: TimeSource,
    @param:ApplicationScope private val scope: CoroutineScope,
) : AppInitializer {

    override fun initialize() {
        scope.launch {
            authRepository.authState
                .map { (it as? AuthState.SignedIn)?.userId }
                .distinctUntilChanged()
                .collect { userId -> if (userId != null) onSignedIn(userId) }
        }
    }

    internal suspend fun onSignedIn(userId: String) {
        val owner = syncPreferences.state.first().ownerUserId
        when {
            owner == null -> syncPreferences.adoptOwner(userId)
            owner != userId -> {
                store.clearForNewOwner()
                trackingPreferences.reset()
                // Marks "now" as the earliest moment the usage log may be read for this account.
                trackingPreferences.setPaused(false, time.nowMs())
                syncPreferences.switchOwner(userId)
            }
        }
        syncScheduler.requestSync()
    }
}
