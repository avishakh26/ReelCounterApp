package com.reelcounter.feature.tracking.setup

import com.reelcounter.core.domain.tracking.CURRENT_DISCLOSURE_VERSION
import com.reelcounter.core.domain.tracking.TrackingStatus
import com.reelcounter.core.testing.FakePlatformSettingsRepository
import com.reelcounter.core.testing.FakeReelCountingStatus
import com.reelcounter.core.testing.FakeTrackingRepository
import com.reelcounter.core.testing.MainDispatcherRule
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TrackingSetupViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val tracking = FakeTrackingRepository()
    private val platforms = FakePlatformSettingsRepository(installed = setOf("tiktok", "instagram"))

    private val counting = FakeReelCountingStatus(enabled = true)

    private fun viewModel() = TrackingSetupViewModel(tracking, platforms, counting)

    @Test
    fun `setup starts with the disclosure and pre-selects installed apps only`() {
        val state = viewModel().uiState.value
        assertEquals(SetupStep.Disclosure, state.step)
        assertEquals(setOf("tiktok", "instagram"), state.choices.filter { it.selected }.map { it.platform.id }.toSet())
    }

    @Test
    fun `nothing is accepted until the user taps agree`() {
        val viewModel = viewModel()
        assertNull(tracking.acceptedDisclosureVersion)

        viewModel.acceptDisclosure()

        assertEquals(CURRENT_DISCLOSURE_VERSION, tracking.acceptedDisclosureVersion)
        assertEquals(SetupStep.ChooseApps, viewModel.uiState.value.step)
    }

    @Test
    fun `at least one app must be chosen`() {
        val viewModel = viewModel()
        viewModel.acceptDisclosure()
        viewModel.toggle("tiktok")
        viewModel.toggle("instagram")

        assertFalse(viewModel.uiState.value.canContinueFromApps)
    }

    @Test
    fun `confirmed choices are saved, then usage access is requested`() {
        val viewModel = viewModel()
        viewModel.acceptDisclosure()
        viewModel.toggle("instagram")
        viewModel.confirmApps()

        assertTrue(platforms.current("tiktok").isTracked)
        assertFalse(platforms.current("instagram").isTracked)
        assertFalse(platforms.current("youtube").isTracked)
        assertEquals(SetupStep.GrantAccess, viewModel.uiState.value.step)
    }

    @Test
    fun `granting usage access in settings finishes setup with a first read`() {
        val viewModel = viewModel()
        viewModel.acceptDisclosure()
        viewModel.confirmApps()

        tracking.statusFlow.value = TrackingStatus.Active

        assertEquals(SetupStep.Done, viewModel.uiState.value.step)
        assertEquals(1, tracking.refreshCount)
    }

    @Test
    fun `re-accepting an updated disclosure with access already granted finishes straight away`() {
        tracking.statusAfterDisclosure = TrackingStatus.Active
        val viewModel = viewModel()

        viewModel.acceptDisclosure()

        assertEquals(SetupStep.Done, viewModel.uiState.value.step)
        assertEquals(1, tracking.refreshCount)
    }

    @Test
    fun `returning after revoking access resumes at the permission step`() {
        tracking.statusFlow.value = TrackingStatus.NeedsUsageAccess
        assertEquals(SetupStep.GrantAccess, viewModel().uiState.value.step)
    }


    @Test
    fun `without reel counting, setup offers to turn it on after usage access`() {
        counting.enabled.value = false
        val viewModel = viewModel()
        viewModel.acceptDisclosure()
        viewModel.confirmApps()

        tracking.statusFlow.value = TrackingStatus.Active

        assertEquals(SetupStep.CountReels, viewModel.uiState.value.step)
        // Turning it on in accessibility settings finishes setup by itself.
        counting.enabled.value = true
        assertEquals(SetupStep.Done, viewModel.uiState.value.step)
    }

    @Test
    fun `reel counting can be skipped, keeping estimates`() {
        counting.enabled.value = false
        val viewModel = viewModel()
        viewModel.acceptDisclosure()
        viewModel.confirmApps()
        tracking.statusFlow.value = TrackingStatus.Active

        viewModel.skipCounting()

        assertEquals(SetupStep.Done, viewModel.uiState.value.step)
    }
}
