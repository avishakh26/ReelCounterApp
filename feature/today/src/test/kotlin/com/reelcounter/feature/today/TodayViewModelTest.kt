package com.reelcounter.feature.today

import com.reelcounter.core.domain.tracking.TrackingStatus
import com.reelcounter.core.model.DailyPlatformActivity
import com.reelcounter.core.model.Platform
import com.reelcounter.core.model.PlatformMetric
import com.reelcounter.core.model.UnavailableReason
import com.reelcounter.core.testing.FakeCurrentDateProvider
import com.reelcounter.core.testing.FakeProfileRepository
import com.reelcounter.core.testing.FakeReelCountingStatus
import com.reelcounter.core.testing.FakeTrackingRepository
import com.reelcounter.core.testing.MainDispatcherRule
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.minutes

class TodayViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val monday = LocalDate.parse("2026-10-19")
    private val tuesday = LocalDate.parse("2026-10-20")
    private val tracking = FakeTrackingRepository(TrackingStatus.Active)
    private val dates = FakeCurrentDateProvider(monday)
    private val profiles = FakeProfileRepository()

    private fun activity(day: LocalDate, minutes: Int?) = DailyPlatformActivity(
        platform = Platform("tiktok", "TikTok"),
        date = day,
        metric = minutes?.let { PlatformMetric.DurationOnly(it.minutes) } ?: PlatformMetric.Unavailable(UnavailableReason.COVERAGE_GAP),
    )

    @Test
    fun `tracking that was never set up asks for setup`() = runTest(mainDispatcherRule.testDispatcher) {
        tracking.statusFlow.value = TrackingStatus.NeedsDisclosure
        val viewModel = TodayViewModel(tracking, dates, profiles, FakeReelCountingStatus())
        backgroundScope.launch { viewModel.uiState.collect() }

        assertIs<TodayUiState.NeedsSetup>(viewModel.uiState.value)
    }

    @Test
    fun `figures recorded before access was revoked are still shown`() = runTest(mainDispatcherRule.testDispatcher) {
        tracking.statusFlow.value = TrackingStatus.NeedsUsageAccess
        tracking.lastUpdated.value = 1L
        tracking.days.value = mapOf(monday to listOf(activity(monday, 30)))
        val viewModel = TodayViewModel(tracking, dates, profiles, FakeReelCountingStatus())
        backgroundScope.launch { viewModel.uiState.collect() }

        val content = assertIs<TodayUiState.Content>(viewModel.uiState.value)
        assertEquals(TrackingStatus.NeedsUsageAccess, content.status)
        assertEquals(30.minutes, content.totalAppTime)
    }

    @Test
    fun `at midnight the screen switches to the new day`() = runTest(mainDispatcherRule.testDispatcher) {
        tracking.days.value = mapOf(monday to listOf(activity(monday, 45)), tuesday to listOf(activity(tuesday, null)))
        val viewModel = TodayViewModel(tracking, dates, profiles, FakeReelCountingStatus())
        backgroundScope.launch { viewModel.uiState.collect() }
        assertEquals(45.minutes, assertIs<TodayUiState.Content>(viewModel.uiState.value).totalAppTime)

        dates.date.value = tuesday

        val content = assertIs<TodayUiState.Content>(viewModel.uiState.value)
        assertEquals(tuesday, content.date)
        assertEquals(null, content.totalAppTime) // nothing measured yet: no total, not "0"
    }

    @Test
    fun `refresh reads the usage log`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = TodayViewModel(tracking, dates, profiles, FakeReelCountingStatus())
        viewModel.refresh()
        assertEquals(1, tracking.refreshCount)
    }

    @Test
    fun `the week covers seven days and days without data stay empty, not zero`() = runTest(mainDispatcherRule.testDispatcher) {
        dates.date.value = tuesday
        tracking.days.value = mapOf(monday to listOf(activity(monday, 40)), tuesday to listOf(activity(tuesday, 10)))
        val viewModel = TodayViewModel(tracking, dates, profiles, FakeReelCountingStatus())
        backgroundScope.launch { viewModel.uiState.collect() }

        val content = assertIs<TodayUiState.Content>(viewModel.uiState.value)
        assertEquals(7, content.week.size)
        assertEquals(tuesday, content.week.last().date)
        assertEquals(tuesday.minusDays(6), content.week.first().date)
        assertEquals(null, content.week.first().appTime)
        assertEquals(false, content.week.first().hasData)
        assertEquals(40.minutes, content.yesterday?.appTime)
    }

    @Test
    fun `duration-only days have app time but no count`() = runTest(mainDispatcherRule.testDispatcher) {
        tracking.days.value = mapOf(monday to listOf(activity(monday, 25)))
        val viewModel = TodayViewModel(tracking, dates, profiles, FakeReelCountingStatus())
        backgroundScope.launch { viewModel.uiState.collect() }

        val today = assertIs<TodayUiState.Content>(viewModel.uiState.value).today
        assertEquals(null, today.count)
        assertEquals(25.minutes, today.appTime)
    }
}
