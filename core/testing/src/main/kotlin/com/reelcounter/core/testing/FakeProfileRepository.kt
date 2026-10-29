package com.reelcounter.core.testing

import com.reelcounter.core.domain.Outcome
import com.reelcounter.core.domain.profile.AvatarImage
import com.reelcounter.core.domain.profile.AvatarImageProcessor
import com.reelcounter.core.domain.profile.ProfileError
import com.reelcounter.core.domain.profile.ProfileRepository
import com.reelcounter.core.domain.profile.ProfileState
import com.reelcounter.core.domain.profile.ProfileUpdate
import com.reelcounter.core.domain.profile.UsernameAvailability
import com.reelcounter.core.model.UserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FakeProfileRepository(initialState: ProfileState = ProfileState.None) : ProfileRepository {
    val state = MutableStateFlow(initialState)
    override val profileState: StateFlow<ProfileState> = state

    /** Availability by username; anything not listed is available. */
    val availability = mutableMapOf<String, UsernameAvailability>()
    var checkError: ProfileError? = null
    var saveError: ProfileError? = null

    val checkedUsernames = mutableListOf<String>()
    val savedUpdates = mutableListOf<Pair<ProfileUpdate, AvatarImage?>>()
    var refreshCount = 0

    override suspend fun refresh() {
        refreshCount++
    }

    override suspend fun checkUsername(username: String): Outcome<UsernameAvailability, ProfileError> {
        checkedUsernames += username
        checkError?.let { return Outcome.Failure(it) }
        return Outcome.Success(availability[username] ?: UsernameAvailability.Available)
    }

    override suspend fun saveProfile(update: ProfileUpdate, newAvatar: AvatarImage?): Outcome<UserProfile, ProfileError> {
        savedUpdates += update to newAvatar
        saveError?.let { return Outcome.Failure(it) }
        val current = (state.value as? ProfileState.Loaded)?.profile
        val saved = UserProfile(
            id = current?.id ?: "user-1",
            username = update.username,
            displayName = update.displayName,
            bio = update.bio,
            avatarUrl = if (newAvatar != null) "https://example.test/avatar.webp" else current?.avatarUrl,
        )
        state.value = ProfileState.Loaded(saved)
        return Outcome.Success(saved)
    }
}

class FakeAvatarImageProcessor : AvatarImageProcessor {
    var result: Outcome<AvatarImage, ProfileError> = Outcome.Success(AvatarImage(byteArrayOf(1, 2, 3), "image/webp"))

    override suspend fun process(sourceUri: String): Outcome<AvatarImage, ProfileError> = result
}
