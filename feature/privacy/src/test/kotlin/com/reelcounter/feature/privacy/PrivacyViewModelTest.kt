package com.reelcounter.feature.privacy

import com.reelcounter.core.domain.Outcome
import com.reelcounter.core.domain.social.PrivacyState
import com.reelcounter.core.domain.social.SocialError
import com.reelcounter.core.model.PrivacySettings
import com.reelcounter.core.model.UserSummary
import com.reelcounter.core.model.Visibility
import com.reelcounter.core.testing.FakeFriendsRepository
import com.reelcounter.core.testing.FakePrivacyRepository
import com.reelcounter.core.testing.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class PrivacyViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val privacy = FakePrivacyRepository()
    private val friends = FakeFriendsRepository()
    private val spammer = UserSummary(id = "9", username = "spam.bot", displayName = null, avatarUrl = null)

    private fun TestScope.viewModel(): PrivacyViewModel {
        val viewModel = PrivacyViewModel(privacy, friends)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        return viewModel
    }

    private fun PrivacyViewModel.shown(): PrivacySettings = assertIs<PrivacyState.Loaded>(uiState.value.settings).settings

    @Test
    fun `defaults are friends-only activity with online status off`() = runTest {
        val shown = viewModel().shown()
        assertEquals(Visibility.Friends, shown.activityVisibility)
        assertEquals(false, shown.shareOnlineStatus)
    }

    @Test
    fun `a choice shows straight away and is saved`() = runTest {
        val gate = CompletableDeferred<Unit>().also { privacy.gate = it }
        val viewModel = viewModel()

        viewModel.setActivityVisibility(Visibility.Private)
        assertEquals(Visibility.Private, viewModel.shown().activityVisibility)

        gate.complete(Unit)
        assertEquals(Visibility.Private, assertIs<PrivacyState.Loaded>(privacy.state.value).settings.activityVisibility)
        assertEquals(Visibility.Private, viewModel.shown().activityVisibility)
    }

    @Test
    fun `a failed save goes back to the saved value and says so`() = runTest {
        val gate = CompletableDeferred<Unit>().also { privacy.gate = it }
        privacy.updateError = SocialError.Network
        val viewModel = viewModel()

        viewModel.setShareOnlineStatus(true)
        assertEquals(true, viewModel.shown().shareOnlineStatus)

        gate.complete(Unit)
        assertEquals(false, viewModel.shown().shareOnlineStatus)
        assertEquals(SocialError.Network, viewModel.uiState.value.error)

        viewModel.onErrorShown()
        assertEquals(null, viewModel.uiState.value.error)
    }

    @Test
    fun `quick successive changes are all kept`() = runTest {
        val gate = CompletableDeferred<Unit>().also { privacy.gate = it }
        val viewModel = viewModel()

        viewModel.setShareOnlineStatus(true)
        viewModel.setDiscoverable(false)
        viewModel.setAcceptFriendRequests(false)
        gate.complete(Unit)

        val saved = assertIs<PrivacyState.Loaded>(privacy.state.value).settings
        assertEquals(true, saved.shareOnlineStatus)
        assertEquals(false, saved.discoverable)
        assertEquals(false, saved.acceptFriendRequests)
        assertEquals(saved, viewModel.shown())
    }

    @Test
    fun `nothing is saved before the settings have loaded`() = runTest {
        privacy.state.value = PrivacyState.Loading
        val viewModel = viewModel()

        viewModel.setDiscoverable(false)

        assertEquals(0, privacy.updateCount)
        assertEquals(PrivacyState.Loading, viewModel.uiState.value.settings)
    }

    @Test
    fun `blocked accounts load and can be unblocked`() = runTest {
        friends.blocked = Outcome.Success(listOf(spammer))
        val viewModel = viewModel()
        assertEquals(BlockedState.Loaded(listOf(spammer)), viewModel.uiState.value.blocked)

        viewModel.unblock(spammer.id)

        assertEquals(listOf("unblock:9"), friends.actions)
        assertEquals(BlockedState.Loaded(emptyList()), viewModel.uiState.value.blocked)
    }

    @Test
    fun `a failed unblock keeps the account listed`() = runTest {
        friends.blocked = Outcome.Success(listOf(spammer))
        friends.actionError = SocialError.Network
        val viewModel = viewModel()

        viewModel.unblock(spammer.id)

        assertEquals(BlockedState.Loaded(listOf(spammer)), viewModel.uiState.value.blocked)
        assertEquals(SocialError.Network, viewModel.uiState.value.error)
    }

    @Test
    fun `retry reloads both settings and the blocked list`() = runTest {
        friends.blocked = Outcome.Failure(SocialError.Network)
        val viewModel = viewModel()
        assertEquals(BlockedState.Failed(SocialError.Network), viewModel.uiState.value.blocked)

        friends.blocked = Outcome.Success(emptyList())
        viewModel.retry()

        assertEquals(1, privacy.refreshCount)
        assertEquals(BlockedState.Loaded(emptyList()), viewModel.uiState.value.blocked)
    }
}
