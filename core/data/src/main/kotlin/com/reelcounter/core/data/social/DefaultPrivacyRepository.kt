package com.reelcounter.core.data.social

import com.reelcounter.core.common.dispatchers.ApplicationScope
import com.reelcounter.core.domain.Outcome
import com.reelcounter.core.domain.auth.AuthRepository
import com.reelcounter.core.domain.auth.AuthState
import com.reelcounter.core.domain.social.PrivacyRepository
import com.reelcounter.core.domain.social.PrivacyState
import com.reelcounter.core.domain.social.SocialError
import com.reelcounter.core.model.PrivacySettings
import com.reelcounter.core.model.Visibility
import com.reelcounter.core.network.RemoteException
import com.reelcounter.core.network.SupabaseConfig
import com.reelcounter.core.network.social.NetworkPrivacySettings
import com.reelcounter.core.network.social.SocialRemoteDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class DefaultPrivacyRepository @Inject constructor(
    private val remote: SocialRemoteDataSource,
    private val config: SupabaseConfig,
    private val authRepository: AuthRepository,
    @ApplicationScope scope: CoroutineScope,
) : PrivacyRepository {

    private val state = MutableStateFlow<PrivacyState>(PrivacyState.Loading)
    override val settings: StateFlow<PrivacyState> = state.asStateFlow()
    private val writes = Mutex()

    init {
        scope.launch {
            authRepository.authState
                .map { (it as? AuthState.SignedIn)?.userId }
                .distinctUntilChanged()
                .collectLatest { userId -> if (userId == null) state.value = PrivacyState.Loading else refresh() }
        }
    }

    override suspend fun refresh() {
        val userId = signedInUserId() ?: return
        state.value = try {
            remote.privacySettings(userId)?.let { PrivacyState.Loaded(it.toModel()) }
                ?: PrivacyState.Failed(SocialError.Unknown("missing_settings"))
        } catch (e: RemoteException) {
            if (state.value is PrivacyState.Loaded) return else PrivacyState.Failed(e.toSocialError())
        }
    }

    // Serialized, so two quick toggles can't overwrite each other with stale values.
    override suspend fun update(transform: (PrivacySettings) -> PrivacySettings): Outcome<PrivacySettings, SocialError> =
        writes.withLock {
            val userId = signedInUserId() ?: return Outcome.Failure(SocialError.NotSignedIn)
            val current = (state.value as? PrivacyState.Loaded)?.settings ?: return Outcome.Failure(SocialError.Unknown("not_loaded"))
            try {
                val saved = remote.updatePrivacySettings(userId, transform(current).toNetwork()).toModel()
                state.value = PrivacyState.Loaded(saved)
                Outcome.Success(saved)
            } catch (e: RemoteException) {
                Outcome.Failure(e.toSocialError())
            }
        }

    private fun signedInUserId(): String? =
        if (config.isConfigured) (authRepository.authState.value as? AuthState.SignedIn)?.userId else null
}

private fun NetworkPrivacySettings.toModel() = PrivacySettings(
    activityVisibility = when (activityVisibility) {
        "public" -> Visibility.Public
        "private" -> Visibility.Private
        else -> Visibility.Friends
    },
    shareOnlineStatus = sharePresence,
    discoverable = discoverable,
    acceptFriendRequests = acceptFriendRequests,
)

private fun PrivacySettings.toNetwork() = NetworkPrivacySettings(
    activityVisibility = when (activityVisibility) {
        Visibility.Public -> "public"
        Visibility.Friends -> "friends"
        Visibility.Private -> "private"
    },
    sharePresence = shareOnlineStatus,
    discoverable = discoverable,
    acceptFriendRequests = acceptFriendRequests,
)
