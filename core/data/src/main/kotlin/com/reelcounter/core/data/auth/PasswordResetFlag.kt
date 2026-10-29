package com.reelcounter.core.data.auth

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Remembers that the current session came from a password-reset code and no new password has
 * been set yet. It's persisted so restarting the app mid-reset still asks for the new password.
 */
internal interface PasswordResetFlag {
    val isPending: Flow<Boolean>
    suspend fun set(pending: Boolean)
}

private val Context.authStateDataStore by preferencesDataStore(name = "auth_state")

@Singleton
internal class DataStorePasswordResetFlag @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : PasswordResetFlag {

    override val isPending: Flow<Boolean> = context.authStateDataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY] ?: false }

    override suspend fun set(pending: Boolean) {
        context.authStateDataStore.edit { it[KEY] = pending }
    }

    private companion object {
        val KEY = booleanPreferencesKey("password_reset_pending")
    }
}
