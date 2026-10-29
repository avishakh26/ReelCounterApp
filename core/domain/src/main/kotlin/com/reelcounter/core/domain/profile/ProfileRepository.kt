package com.reelcounter.core.domain.profile

import com.reelcounter.core.domain.Outcome
import com.reelcounter.core.model.UserProfile
import kotlinx.coroutines.flow.StateFlow

/** The signed-in user's own profile. Loads automatically on sign-in and clears on sign-out. */
interface ProfileRepository {
    val profileState: StateFlow<ProfileState>

    /** Reloads the profile, e.g. after a failed load. */
    suspend fun refresh()

    /** Format check first, then the server's answer: available, taken or reserved. */
    suspend fun checkUsername(username: String): Outcome<UsernameAvailability, ProfileError>

    /**
     * Saves [update]. When [newAvatar] is set it is uploaded first and replaces the old avatar,
     * which is then deleted.
     */
    suspend fun saveProfile(update: ProfileUpdate, newAvatar: AvatarImage? = null): Outcome<UserProfile, ProfileError>
}

sealed interface ProfileState {
    /** Nobody is signed in. */
    data object None : ProfileState
    data object Loading : ProfileState
    data class Loaded(val profile: UserProfile) : ProfileState
    data class Failed(val error: ProfileError) : ProfileState
}

/** Field values to save. Blank optional fields are stored as null. */
data class ProfileUpdate(
    val username: String,
    val displayName: String?,
    val bio: String?,
)

/** An avatar already resized and encoded for upload. */
class AvatarImage(val bytes: ByteArray, val mimeType: String) {
    val fileExtension: String get() = if (mimeType == "image/webp") "webp" else "jpg"
}

enum class UsernameAvailability { Available, Taken, Reserved, Invalid }

sealed interface ProfileError {
    data object UsernameTaken : ProfileError
    data object UsernameReserved : ProfileError
    data class InvalidInput(val field: ProfileField) : ProfileError
    data object ImageUnreadable : ProfileError
    data object NotSignedIn : ProfileError
    data object Network : ProfileError
    data class Unknown(val code: String?) : ProfileError
}

enum class ProfileField { Username, DisplayName, Bio, Avatar }
