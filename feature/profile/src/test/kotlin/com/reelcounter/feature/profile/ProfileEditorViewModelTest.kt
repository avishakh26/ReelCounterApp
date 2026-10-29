package com.reelcounter.feature.profile

import com.reelcounter.core.domain.profile.ProfileError
import com.reelcounter.core.domain.profile.ProfileState
import com.reelcounter.core.domain.profile.ProfileUpdate
import com.reelcounter.core.domain.profile.UsernameAvailability
import com.reelcounter.core.domain.profile.UsernameIssue
import com.reelcounter.core.model.UserProfile
import com.reelcounter.core.testing.FakeAuthRepository
import com.reelcounter.core.testing.FakeAvatarImageProcessor
import com.reelcounter.core.testing.FakeProfileRepository
import com.reelcounter.core.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileEditorViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val newUser = UserProfile(id = "user-1", username = null, displayName = null, bio = null, avatarUrl = null)
    private val profiles = FakeProfileRepository(ProfileState.Loaded(newUser))
    private val avatars = FakeAvatarImageProcessor()

    private fun viewModel() = ProfileEditorViewModel(profiles, avatars, FakeAuthRepository())

    private fun TestScope.passDebounce() = advanceTimeBy(ProfileEditorViewModel.USERNAME_CHECK_DEBOUNCE_MS + 1)

    @Test
    fun `a new user starts with an empty form that can't be saved`() = runTest(mainDispatcherRule.testDispatcher) {
        val state = viewModel().uiState.value
        assertFalse(state.isLoading)
        assertEquals(UsernameStatus.Empty, state.usernameStatus)
        assertFalse(state.canSave)
    }

    @Test
    fun `a valid username is checked after a pause in typing`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = viewModel()

        viewModel.onUsernameChange("Sam.K")
        viewModel.onUsernameChange("Sam.Ki")
        assertEquals(UsernameStatus.Checking, viewModel.uiState.value.usernameStatus)
        assertEquals("sam.ki", viewModel.uiState.value.username)

        passDebounce()
        assertEquals(listOf("sam.ki"), profiles.checkedUsernames)
        assertEquals(UsernameStatus.Available, viewModel.uiState.value.usernameStatus)
        assertTrue(viewModel.uiState.value.canSave)
    }

    @Test
    fun `taken names block saving`() = runTest(mainDispatcherRule.testDispatcher) {
        profiles.availability["sam"] = UsernameAvailability.Taken
        val viewModel = viewModel()

        viewModel.onUsernameChange("sam")
        passDebounce()

        assertEquals(UsernameStatus.Taken, viewModel.uiState.value.usernameStatus)
        assertFalse(viewModel.uiState.value.canSave)
    }

    @Test
    fun `malformed names are flagged locally, without a server check`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = viewModel()

        viewModel.onUsernameChange("sam..k")
        passDebounce()

        assertEquals(UsernameStatus.Invalid(UsernameIssue.ConsecutivePeriods), viewModel.uiState.value.usernameStatus)
        assertEquals(emptyList(), profiles.checkedUsernames)
    }

    @Test
    fun `saving normalizes optional fields and includes a picked avatar`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = viewModel()
        viewModel.onUsernameChange("sam")
        passDebounce()
        viewModel.onDisplayNameChange("  Sam   Kim ")
        viewModel.onBioChange("   ")
        viewModel.onAvatarPicked("content://picked")

        viewModel.save()

        val (update, avatar) = profiles.savedUpdates.single()
        assertEquals(ProfileUpdate(username = "sam", displayName = "Sam Kim", bio = null), update)
        assertNotNull(avatar)
        assertTrue(viewModel.uiState.value.saved)
    }

    @Test
    fun `a name taken between check and save is reported on the field`() = runTest(mainDispatcherRule.testDispatcher) {
        profiles.saveError = ProfileError.UsernameTaken
        val viewModel = viewModel()
        viewModel.onUsernameChange("sam")
        passDebounce()

        viewModel.save()

        assertEquals(UsernameStatus.Taken, viewModel.uiState.value.usernameStatus)
        assertFalse(viewModel.uiState.value.saved)
    }

    @Test
    fun `an existing username counts as unchanged and is saveable`() = runTest(mainDispatcherRule.testDispatcher) {
        profiles.state.value = ProfileState.Loaded(newUser.copy(username = "sam"))
        val viewModel = viewModel()

        assertEquals(UsernameStatus.Unchanged, viewModel.uiState.value.usernameStatus)
        assertTrue(viewModel.uiState.value.canSave)
    }
}
