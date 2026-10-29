package com.reelcounter.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

data class TrackingPreferences(
    /** The disclosure version the user agreed to; 0 = never. */
    val disclosureAcceptedVersion: Int = 0,
    val disclosureAcceptedAtMs: Long? = null,
    val paused: Boolean = false,
    /**
     * When tracking last resumed after a pause. Usage before this moment is never read, so time
     * spent while paused is never recorded, even though Android still logs it.
     */
    val resumedAtMs: Long? = null,
    /**
     * Since when the reel-counting accessibility service has been running without a break;
     * null while it's off. A day's count is exact only if counting was on before that day's use.
     */
    val countingSinceMs: Long? = null,
    /** When the counting service last stopped, while it's off; null while it's running. */
    val countingStoppedAtMs: Long? = null,
) {
    /** The start of the current unbroken counting run, or null while counting is off. */
    val activeCountingSinceMs: Long? get() = countingSinceMs.takeIf { countingStoppedAtMs == null }
}

interface TrackingPreferencesStore {
    val preferences: Flow<TrackingPreferences>
    suspend fun acceptDisclosure(version: Int, atMs: Long)
    suspend fun setPaused(paused: Boolean, atMs: Long)

    /** Stores the counting service's run: since when it has counted, and when it stopped (if it did). */
    suspend fun setCountingState(sinceMs: Long?, stoppedAtMs: Long?)

    /** Forgets consent and pause, e.g. when another account takes over this device. */
    suspend fun reset()
}

private val Context.trackingDataStore by preferencesDataStore(name = "tracking_preferences")

@Singleton
internal class DataStoreTrackingPreferencesStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : TrackingPreferencesStore {

    override val preferences: Flow<TrackingPreferences> = context.trackingDataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map {
            TrackingPreferences(
                disclosureAcceptedVersion = it[DISCLOSURE_VERSION] ?: 0,
                disclosureAcceptedAtMs = it[DISCLOSURE_AT],
                paused = it[PAUSED] ?: false,
                resumedAtMs = it[RESUMED_AT],
                countingSinceMs = it[COUNTING_SINCE],
                countingStoppedAtMs = it[COUNTING_STOPPED_AT],
            )
        }

    override suspend fun acceptDisclosure(version: Int, atMs: Long) {
        context.trackingDataStore.edit {
            it[DISCLOSURE_VERSION] = version
            it[DISCLOSURE_AT] = atMs
        }
    }

    override suspend fun setPaused(paused: Boolean, atMs: Long) {
        context.trackingDataStore.edit {
            it[PAUSED] = paused
            if (!paused) it[RESUMED_AT] = atMs
        }
    }

    override suspend fun setCountingState(sinceMs: Long?, stoppedAtMs: Long?) {
        context.trackingDataStore.edit {
            if (sinceMs == null) it.remove(COUNTING_SINCE) else it[COUNTING_SINCE] = sinceMs
            if (stoppedAtMs == null) it.remove(COUNTING_STOPPED_AT) else it[COUNTING_STOPPED_AT] = stoppedAtMs
        }
    }

    override suspend fun reset() {
        // The counting service keeps running across accounts, so its run is kept.
        context.trackingDataStore.edit {
            val countingSince = it[COUNTING_SINCE]
            val countingStoppedAt = it[COUNTING_STOPPED_AT]
            it.clear()
            if (countingSince != null) it[COUNTING_SINCE] = countingSince
            if (countingStoppedAt != null) it[COUNTING_STOPPED_AT] = countingStoppedAt
        }
    }

    private companion object {
        val DISCLOSURE_VERSION = intPreferencesKey("disclosure_version")
        val DISCLOSURE_AT = longPreferencesKey("disclosure_accepted_at")
        val PAUSED = booleanPreferencesKey("paused")
        val RESUMED_AT = longPreferencesKey("resumed_at")
        val COUNTING_SINCE = longPreferencesKey("counting_since")
        val COUNTING_STOPPED_AT = longPreferencesKey("counting_stopped_at")
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class DataStoreModule {
    @Binds
    abstract fun bindTrackingPreferencesStore(impl: DataStoreTrackingPreferencesStore): TrackingPreferencesStore

    @Binds
    abstract fun bindSyncPreferencesStore(impl: DataStoreSyncPreferencesStore): SyncPreferencesStore
}
