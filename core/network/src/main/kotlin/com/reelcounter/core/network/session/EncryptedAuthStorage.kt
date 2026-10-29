package com.reelcounter.core.network.session

import android.content.Context
import android.util.Base64
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.jan.supabase.auth.CodeVerifierCache
import io.github.jan.supabase.auth.SessionManager
import io.github.jan.supabase.auth.exception.NoSessionFoundException
import io.github.jan.supabase.auth.user.UserSession
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private val Context.authDataStore by preferencesDataStore(name = "auth_session")

/**
 * Persists the Supabase session (access and refresh tokens) and the PKCE code verifier,
 * encrypted with a Keystore key. supabase-kt's default would store tokens in plain
 * SharedPreferences. Unreadable data (e.g. the key was reset) is discarded, which simply means
 * signing in again.
 */
@Singleton
internal class EncryptedAuthStorage @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : SessionManager, CodeVerifierCache {

    private val cipher = KeystoreCipher(keyAlias = "reelcounter.auth.session")
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun saveSession(session: UserSession) =
        write(SESSION, json.encodeToString(UserSession.serializer(), session))

    override suspend fun loadSession(): UserSession =
        read(SESSION)
            ?.let { runCatching { json.decodeFromString(UserSession.serializer(), it) }.getOrNull() }
            ?: throw NoSessionFoundException()

    override suspend fun deleteSession() = remove(SESSION)

    override suspend fun saveCodeVerifier(codeVerifier: String) = write(CODE_VERIFIER, codeVerifier)

    override suspend fun loadCodeVerifier(): String? = read(CODE_VERIFIER)

    override suspend fun deleteCodeVerifier() = remove(CODE_VERIFIER)

    private suspend fun write(key: Preferences.Key<String>, value: String) {
        val encrypted = Base64.encodeToString(cipher.encrypt(value.encodeToByteArray()), Base64.NO_WRAP)
        context.authDataStore.edit { it[key] = encrypted }
    }

    private suspend fun read(key: Preferences.Key<String>): String? {
        val stored = context.authDataStore.data.first()[key] ?: return null
        return runCatching { cipher.decrypt(Base64.decode(stored, Base64.NO_WRAP)).decodeToString() }
            .onFailure { remove(key) }
            .getOrNull()
    }

    private suspend fun remove(key: Preferences.Key<String>) {
        context.authDataStore.edit { it.remove(key) }
    }

    private companion object {
        val SESSION = stringPreferencesKey("session")
        val CODE_VERIFIER = stringPreferencesKey("code_verifier")
    }
}
