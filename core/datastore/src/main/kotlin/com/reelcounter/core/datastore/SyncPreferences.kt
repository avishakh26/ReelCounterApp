package com.reelcounter.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class SyncState(
    /** The account this device's local tracking data belongs to; null until someone signs in. */
    val ownerUserId: String? = null,
    /** Random per-install id registered in public.devices; never a hardware identifier. */
    val deviceId: String? = null,
    val deviceRegistered: Boolean = false,
    val lastSyncMs: Long? = null,
)

interface SyncPreferencesStore {
    val state: Flow<SyncState>

    /** The first account to sign in adopts the data already recorded on this device. */
    suspend fun adoptOwner(userId: String)

    /** A different account signed in: new owner and a new device id (the old one belongs to the old owner). */
    suspend fun switchOwner(userId: String)

    suspend fun setDeviceRegistered(registered: Boolean)
    suspend fun setLastSync(atMs: Long)
}

private val Context.syncDataStore by preferencesDataStore(name = "sync_state")

@Singleton
internal class DataStoreSyncPreferencesStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : SyncPreferencesStore {

    override val state: Flow<SyncState> = context.syncDataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map {
            SyncState(
                ownerUserId = it[OWNER],
                deviceId = it[DEVICE_ID],
                deviceRegistered = it[DEVICE_REGISTERED] ?: false,
                lastSyncMs = it[LAST_SYNC],
            )
        }

    override suspend fun adoptOwner(userId: String) {
        context.syncDataStore.edit {
            it[OWNER] = userId
            if (it[DEVICE_ID] == null) it[DEVICE_ID] = UUID.randomUUID().toString()
        }
    }

    override suspend fun switchOwner(userId: String) {
        context.syncDataStore.edit {
            it[OWNER] = userId
            it[DEVICE_ID] = UUID.randomUUID().toString()
            it[DEVICE_REGISTERED] = false
            it.remove(LAST_SYNC)
        }
    }

    override suspend fun setDeviceRegistered(registered: Boolean) {
        context.syncDataStore.edit { it[DEVICE_REGISTERED] = registered }
    }

    override suspend fun setLastSync(atMs: Long) {
        context.syncDataStore.edit { it[LAST_SYNC] = atMs }
    }

    private companion object {
        val OWNER = stringPreferencesKey("owner_user_id")
        val DEVICE_ID = stringPreferencesKey("device_id")
        val DEVICE_REGISTERED = booleanPreferencesKey("device_registered")
        val LAST_SYNC = longPreferencesKey("last_sync_ms")
    }
}
