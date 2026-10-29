package com.reelcounter.feature.insights

import com.reelcounter.core.domain.tracking.TrackingStatus
import com.reelcounter.core.model.DailyPlatformActivity
import com.reelcounter.core.model.Platform
import com.reelcounter.core.model.PlatformMetric
import com.reelcounter.core.model.UnavailableReason
import com.reelcounter.core.testing.FakeCurrentDateProvider
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

class InsightsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val today = LocalDate.parse("2026-10-20")
    private val tracking = FakeTrackingRepository(TrackingStatus.Active)
    private val dates = FakeCurrentDateProvider(today)
    private val tiktok = Platform("tiktok", "TikTok")
    private val youtube = Platform("youtube", "YouTube")

    private fun estimated(day: LocalDate, count: Int) =
        DailyPlatformActivity(tiktok, day, PlatformMetric.Estimated(appTime = 20.minutes, count = count, low = count - 2, high = count + 2))

    private fun gap(day: LocalDate) =
        DailyPlatformActivity(youtube, day, PlatformMetric.Unavailable(UnavailableReason.COVERAGE_GAP))

    @Test
    fun `a period without any recorded data is empty, not a chart of zeros`() = runTest(mainDispatcherRule.testDispatcher) {
        tracking.days.value = mapOf(today to listOf(gap(today)))
        val viewModel = InsightsViewModel(tracking, dates)
        backgroundScope.launch { viewModel.uiState.collect() }

        assertEquals(InsightsUiState.Empty, viewModel.uiState.value)
    }

    @Test
    fun `weeks are split into the last seven days and the seven before`() = runTest(mainDispatcherRule.testDispatcher) {
        val lastWeek = today.minusDays(9)
        tracking.days.value = mapOf(
            today to listOf(estimated(today, 10), gap(today)),
            today.minusDays(1) to listOf(estimated(today.minusDays(1), 30)),
            lastWeek to listOf(estimated(lastWeek, 5)),
        )
        val viewModel = InsightsViewModel(tracking, dates)
        backgroundScope.launch { viewModel.uiState.collect() }

        val content = assertIs<InsightsUiState.Content>(viewModel.uiState.value)
        assertEquals(7, content.thisWeek.size)
        assertEquals(today, content.thisWeek.last().date)
        assertEquals(10, content.thisWeek.last().count)
        assertEquals(5, content.previousWeek.single { it.hasData }.count)
        // Only this week counts towards the per-app totals; a platform with no data has none.
        assertEquals(40, content.platforms.single { it.platform == tiktok }.count)
        assertEquals(null, content.platforms.single { it.platform == youtube }.count)
        assertEquals(true, content.hasCounts)
    }
}
