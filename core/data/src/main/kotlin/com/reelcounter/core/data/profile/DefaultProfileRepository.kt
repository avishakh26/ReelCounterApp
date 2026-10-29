package com.reelcounter.core.data.profile

import com.reelcounter.core.common.dispatchers.ApplicationScope
import com.reelcounter.core.data.error.toProfileError
import com.reelcounter.core.domain.Outcome
import com.reelcounter.core.domain.auth.AuthRepository
import com.reelcounter.core.domain.auth.AuthState
import com.reelcounter.core.domain.profile.AvatarImage
import com.reelcounter.core.domain.profile.ProfileError
import com.reelcounter.core.domain.profile.ProfileField
import com.reelcounter.core.domain.profile.ProfileRepository
import com.reelcounter.core.domain.profile.ProfileState
import com.reelcounter.core.domain.profile.ProfileTextRules
import com.reelcounter.core.domain.profile.ProfileUpdate
import com.reelcounter.core.domain.profile.UsernameAvailability
import com.reelcounter.core.domain.profile.UsernameRules
import com.reelcounter.core.model.UserProfile
import com.reelcounter.core.network.RemoteException
import com.reelcounter.core.network.profile.NetworkProfile
import com.reelcounter.core.network.profile.NetworkProfileUpdate
import com.reelcounter.core.network.profile.ProfileRemoteDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class DefaultProfileRepository @Inject constructor(
    private val remote: ProfileRemoteDataSource,
    authRepository: AuthRepository,
    @ApplicationScope scope: CoroutineScope,
) : ProfileRepository {

    private val state = MutableStateFlow<ProfileState>(ProfileState.None)
    override val profileState: StateFlow<ProfileState> = state.asStateFlow()

    @Volatile private var userId: String? = null

    /** The last profile row from the server; holds the avatar's storage path, which the model doesn't. */
    @Volatile private var remoteProfile: NetworkProfile? = null

    init {
        scope.launch {
            authRepository.authState
                .map { it.userIdOrNull() }
                .distinctUntilChanged()
                .collectLatest { id ->
                    userId = id
                    remoteProfile = null
                    if (id == null) state.value = ProfileState.None else load(id)
                }
        }
    }

    override suspend fun refresh() {
        userId?.let { load(it) }
    }

    override suspend fun checkUsername(username: String): Outcome<UsernameAvailability, ProfileError> {
        if (UsernameRules.validate(username) != null) return Outcome.Success(UsernameAvailability.Invalid)
        return try {
            val availability = when (remote.checkUsername(username)) {
                "available" -> UsernameAvailability.Available
                "taken" -> UsernameAvailability.Taken
                "reserved" -> UsernameAvailability.Reserved
                else -> UsernameAvailability.Invalid
            }
            Outcome.Success(availability)
        } catch (e: RemoteException) {
            Outcome.Failure(e.toProfileError())
        }
    }

    override suspend fun saveProfile(update: ProfileUpdate, newAvatar: AvatarImage?): Outcome<UserProfile, ProfileError> {
        val id = userId ?: return Outcome.Failure(ProfileError.NotSignedIn)
        validate(update)?.let { return Outcome.Failure(it) }

        // Upload, row update and cleanup must finish together, even if the screen closes meanwhile.
        return withContext(NonCancellable) {
            val oldAvatarPath = remoteProfile?.avatarPath
            val newAvatarPath = newAvatar?.let { "$id/${UUID.randomUUID()}.${it.fileExtension}" }
            try {
                if (newAvatar != null && newAvatarPath != null) {
                    remote.uploadAvatar(newAvatarPath, newAvatar.bytes, newAvatar.mimeType)
                }
                val saved = remote.updateProfile(
                    userId = id,
                    update = NetworkProfileUpdate(
                        username = update.username,
                        displayName = update.displayName,
                        bio = update.bio,
                        avatarPath = newAvatarPath ?: oldAvatarPath,
                    ),
                )
                remoteProfile = saved
                val profile = saved.toModel()
                state.value = ProfileState.Loaded(profile)
                if (newAvatarPath != null && oldAvatarPath != null) {
                    // Best effort: an old file left behind is harmless and can be swept later.
                    runCatching { remote.deleteAvatar(oldAvatarPath) }
                }
                Outcome.Success(profile)
            } catch (e: RemoteException) {
                // Don't leave an uploaded file that no profile points to.
                if (newAvatarPath != null) runCatching { remote.deleteAvatar(newAvatarPath) }
                Outcome.Failure(e.toProfileError())
            }
        }
    }

    private suspend fun load(id: String) {
        state.value = ProfileState.Loading
        state.value = try {
            val profile = remote.fetchProfile(id)
            if (profile == null) {
                // The row is created by a trigger at sign-up, so a missing row is a server problem.
                ProfileState.Failed(ProfileError.Unknown("profile_missing"))
            } else {
                remoteProfile = profile
                ProfileState.Loaded(profile.toModel())
            }
        } catch (e: RemoteException) {
            ProfileState.Failed(e.toProfileError())
        }
    }

    private fun validate(update: ProfileUpdate): ProfileError? = when {
        UsernameRules.validate(update.username) != null -> ProfileError.InvalidInput(ProfileField.Username)
        !ProfileTextRules.isValidDisplayName(update.displayName) -> ProfileError.InvalidInput(ProfileField.DisplayName)
        !ProfileTextRules.isValidBio(update.bio) -> ProfileError.InvalidInput(ProfileField.Bio)
        else -> null
    }

    private fun NetworkProfile.toModel() = UserProfile(
        id = id,
        username = username,
        displayName = displayName,
        bio = bio,
        avatarUrl = avatarPath?.let(remote::avatarUrl),
    )
}

private fun AuthState.userIdOrNull(): String? = when (this) {
    is AuthState.SignedIn -> userId
    is AuthState.PasswordResetPending -> userId
    AuthState.Initializing, AuthState.NotConfigured, AuthState.SignedOut -> null
}
