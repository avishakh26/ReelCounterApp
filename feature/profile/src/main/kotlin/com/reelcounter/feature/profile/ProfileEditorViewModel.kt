package com.reelcounter.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reelcounter.core.domain.Outcome
import com.reelcounter.core.domain.auth.AuthRepository
import com.reelcounter.core.domain.profile.AvatarImage
import com.reelcounter.core.domain.profile.AvatarImageProcessor
import com.reelcounter.core.domain.profile.ProfileError
import com.reelcounter.core.domain.profile.ProfileRepository
import com.reelcounter.core.domain.profile.ProfileState
import com.reelcounter.core.domain.profile.ProfileTextRules
import com.reelcounter.core.domain.profile.ProfileUpdate
import com.reelcounter.core.domain.profile.UsernameAvailability
import com.reelcounter.core.domain.profile.UsernameIssue
import com.reelcounter.core.domain.profile.UsernameRules
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface UsernameStatus {
    data object Empty : UsernameStatus
    data class Invalid(val issue: UsernameIssue) : UsernameStatus
    data object Checking : UsernameStatus
    data object Available : UsernameStatus

    /** The user's current username; nothing to check. */
    data object Unchanged : UsernameStatus
    data object Taken : UsernameStatus
    data object Reserved : UsernameStatus

    /** The availability check itself failed (e.g. offline); saving will still be checked by the server. */
    data object CheckFailed : UsernameStatus
}

data class ProfileEditorUiState(
    val isLoading: Boolean = true,
    val username: String = "",
    val usernameStatus: UsernameStatus = UsernameStatus.Empty,
    val displayName: String = "",
    val bio: String = "",
    /** A URL, or the bytes of a newly picked image; null shows the monogram. */
    val avatar: Any? = null,
    val isProcessingAvatar: Boolean = false,
    val isSaving: Boolean = false,
    val error: ProfileError? = null,
    /** Set once after a successful save; the edit screen closes and clears it. */
    val saved: Boolean = false,
    /** Something differs from the saved profile, so leaving should ask first. */
    val hasUnsavedChanges: Boolean = false,
) {
    val canSave: Boolean
        get() = !isLoading && !isSaving && !isProcessingAvatar && usernameStatus in savableStatuses

    private companion object {
        val savableStatuses = setOf(UsernameStatus.Available, UsernameStatus.Unchanged, UsernameStatus.CheckFailed)
    }
}

/**
 * Edits the signed-in user's profile. Used for first-run setup (no username yet) and for later
 * edits. The username is checked against the server as the user types, debounced.
 */
@OptIn(FlowPreview::class)
@HiltViewModel
class ProfileEditorViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val avatarImageProcessor: AvatarImageProcessor,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileEditorUiState())
    val uiState: StateFlow<ProfileEditorUiState> = _uiState.asStateFlow()

    private val usernameToCheck = MutableStateFlow<String?>(null)
    private var originalUsername: String? = null
    private var originalDisplayName = ""
    private var originalBio = ""
    private var newAvatar: AvatarImage? = null

    init {
        viewModelScope.launch {
            val profile = profileRepository.profileState.filterIsInstance<ProfileState.Loaded>().first().profile
            originalUsername = profile.username
            originalDisplayName = profile.displayName.orEmpty()
            originalBio = profile.bio.orEmpty()
            _uiState.update {
                it.copy(
                    isLoading = false,
                    username = profile.username.orEmpty(),
                    usernameStatus = if (profile.username != null) UsernameStatus.Unchanged else UsernameStatus.Empty,
                    displayName = profile.displayName.orEmpty(),
                    bio = profile.bio.orEmpty(),
                    avatar = profile.avatarUrl,
                )
            }
        }
        viewModelScope.launch {
            usernameToCheck
                .filterNotNull()
                .debounce(USERNAME_CHECK_DEBOUNCE_MS)
                .collectLatest(::checkAvailability)
        }
    }

    fun onUsernameChange(input: String) {
        val username = UsernameRules.normalize(input).take(UsernameRules.MAX_LENGTH)
        val issue = UsernameRules.validate(username)
        val status = when {
            username.isEmpty() -> UsernameStatus.Empty
            issue != null -> UsernameStatus.Invalid(issue)
            username == originalUsername -> UsernameStatus.Unchanged
            else -> UsernameStatus.Checking
        }
        _uiState.update { it.copy(username = username, usernameStatus = status, error = null).withChangeFlag() }
        usernameToCheck.value = username.takeIf { status == UsernameStatus.Checking }
    }

    fun onDisplayNameChange(input: String) =
        _uiState.update { it.copy(displayName = input.take(ProfileTextRules.DISPLAY_NAME_MAX), error = null).withChangeFlag() }

    fun onBioChange(input: String) =
        _uiState.update { it.copy(bio = input.take(ProfileTextRules.BIO_MAX), error = null).withChangeFlag() }

    /** [uri] is the content URI returned by the photo picker. */
    fun onAvatarPicked(uri: String) {
        _uiState.update { it.copy(isProcessingAvatar = true, error = null) }
        viewModelScope.launch {
            when (val result = avatarImageProcessor.process(uri)) {
                is Outcome.Success -> {
                    newAvatar = result.value
                    _uiState.update { it.copy(isProcessingAvatar = false, avatar = result.value.bytes).withChangeFlag() }
                }
                is Outcome.Failure -> _uiState.update { it.copy(isProcessingAvatar = false, error = result.error) }
            }
        }
    }

    fun save() {
        val current = _uiState.value
        if (!current.canSave) return
        val update = ProfileUpdate(
            username = current.username,
            displayName = ProfileTextRules.normalizeDisplayName(current.displayName),
            bio = ProfileTextRules.normalizeBio(current.bio),
        )
        _uiState.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            when (val result = profileRepository.saveProfile(update, newAvatar)) {
                is Outcome.Success -> {
                    newAvatar = null
                    originalUsername = result.value.username
                    _uiState.update {
                        it.copy(isSaving = false, saved = true, usernameStatus = UsernameStatus.Unchanged, avatar = result.value.avatarUrl)
                    }
                }
                is Outcome.Failure -> _uiState.update {
                    it.copy(
                        isSaving = false,
                        error = result.error,
                        usernameStatus = when (result.error) {
                            ProfileError.UsernameTaken -> UsernameStatus.Taken
                            ProfileError.UsernameReserved -> UsernameStatus.Reserved
                            else -> it.usernameStatus
                        },
                    )
                }
            }
        }
    }

    fun onSavedHandled() = _uiState.update { it.copy(saved = false) }

    private fun ProfileEditorUiState.withChangeFlag() = copy(
        hasUnsavedChanges = username != originalUsername.orEmpty() ||
            displayName != originalDisplayName ||
            bio != originalBio ||
            newAvatar != null,
    )

    /** Offered during first-run setup, in case the user signed in with the wrong account. */
    fun signOut() {
        viewModelScope.launch { authRepository.signOut() }
    }

    private suspend fun checkAvailability(username: String) {
        val result = profileRepository.checkUsername(username)
        _uiState.update { state ->
            // Ignore answers for a name the user has already changed.
            if (state.username != username) return@update state
            val status = when (result) {
                is Outcome.Success -> when (result.value) {
                    UsernameAvailability.Available -> UsernameStatus.Available
                    UsernameAvailability.Taken -> UsernameStatus.Taken
                    UsernameAvailability.Reserved -> UsernameStatus.Reserved
                    UsernameAvailability.Invalid ->
                        UsernameRules.validate(username)?.let(UsernameStatus::Invalid) ?: UsernameStatus.CheckFailed
                }
                is Outcome.Failure -> UsernameStatus.CheckFailed
            }
            state.copy(usernameStatus = status)
        }
    }

    internal companion object {
        const val USERNAME_CHECK_DEBOUNCE_MS = 400L
    }
}
