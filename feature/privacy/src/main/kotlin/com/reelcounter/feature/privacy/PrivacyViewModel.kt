package com.reelcounter.feature.privacy

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reelcounter.core.domain.Outcome
import com.reelcounter.core.domain.social.FriendsRepository
import com.reelcounter.core.domain.social.PrivacyRepository
import com.reelcounter.core.domain.social.PrivacyState
import com.reelcounter.core.domain.social.SocialError
import com.reelcounter.core.model.PrivacySettings
import com.reelcounter.core.model.UserSummary
import com.reelcounter.core.model.Visibility
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface BlockedState {
    data object Loading : BlockedState
    data class Loaded(val users: List<UserSummary>) : BlockedState
    data class Failed(val error: SocialError) : BlockedState
}

data class PrivacyUiState(
    val settings: PrivacyState = PrivacyState.Loading,
    val blocked: BlockedState = BlockedState.Loading,
    val unblockingIds: Set<String> = emptySet(),
    /** A save or unblock that failed, shown once as a snackbar. */
    val error: SocialError? = null,
)

@HiltViewModel
class PrivacyViewModel @Inject constructor(
    private val privacyRepository: PrivacyRepository,
    private val friendsRepository: FriendsRepository,
) : ViewModel() {

    /** What the user just chose, shown until the server confirms or refuses it. */
    private val pending = MutableStateFlow<PrivacySettings?>(null)
    private var inFlight = 0
    private val blocked = MutableStateFlow<BlockedState>(BlockedState.Loading)
    private val unblocking = MutableStateFlow<Set<String>>(emptySet())
    private val error = MutableStateFlow<SocialError?>(null)

    val uiState: StateFlow<PrivacyUiState> = combine(
        privacyRepository.settings,
        pending,
        blocked,
        unblocking,
        error,
    ) { settings, pending, blocked, unblocking, error ->
        val shown = if (settings is PrivacyState.Loaded && pending != null) PrivacyState.Loaded(pending) else settings
        PrivacyUiState(shown, blocked, unblocking, error)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PrivacyUiState())

    init {
        loadBlocked()
    }

    fun setActivityVisibility(visibility: Visibility) = save { it.copy(activityVisibility = visibility) }

    fun setShareOnlineStatus(share: Boolean) = save { it.copy(shareOnlineStatus = share) }

    fun setDiscoverable(discoverable: Boolean) = save { it.copy(discoverable = discoverable) }

    fun setAcceptFriendRequests(accept: Boolean) = save { it.copy(acceptFriendRequests = accept) }

    fun unblock(userId: String) {
        if (userId in unblocking.value) return
        unblocking.update { it + userId }
        viewModelScope.launch {
            when (val result = friendsRepository.unblock(userId)) {
                is Outcome.Success -> blocked.update { state ->
                    if (state is BlockedState.Loaded) BlockedState.Loaded(state.users.filterNot { it.id == userId }) else state
                }
                is Outcome.Failure -> error.value = result.error
            }
            unblocking.update { it - userId }
        }
    }

    fun retry() {
        viewModelScope.launch { privacyRepository.refresh() }
        loadBlocked()
    }

    fun onErrorShown() = error.update { null }

    private fun save(transform: (PrivacySettings) -> PrivacySettings) {
        val current = pending.value ?: (privacyRepository.settings.value as? PrivacyState.Loaded)?.settings ?: return
        pending.value = transform(current)
        inFlight++
        viewModelScope.launch {
            // The repository applies the same change to the latest saved settings, so quick
            // successive toggles each keep their own change.
            val result = privacyRepository.update(transform)
            if (result is Outcome.Failure) error.value = result.error
            if (--inFlight == 0) pending.value = null
        }
    }

    private fun loadBlocked() {
        viewModelScope.launch {
            blocked.value = when (val result = friendsRepository.blockedUsers()) {
                is Outcome.Success -> BlockedState.Loaded(result.value)
                is Outcome.Failure -> BlockedState.Failed(result.error)
            }
        }
    }
}
