package com.reelcounter.feature.today

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import com.reelcounter.core.domain.tracking.TrackingStatus
import com.reelcounter.core.model.UserProfile
import com.reelcounter.core.preview.PreviewTheme
import com.reelcounter.core.preview.SampleActivity
import com.reelcounter.core.ui.metric.DaySummary
import com.reelcounter.core.ui.metric.summarize
import java.time.LocalDate
import kotlin.time.Duration.Companion.minutes

private val previewDate = LocalDate.of(2026, 10, 20)

// Fictional week for previews only; one day has no data to show the "no data" stub.
private val previewWeek: List<DaySummary> = listOf(120, 95, null, 140, 88, 110).mapIndexed { index, count ->
    val date = previewDate.minusDays((6 - index).toLong())
    DaySummary(
        date = date,
        count = count,
        countLow = count?.let { it * 3 / 4 },
        countHigh = count?.let { it * 5 / 4 },
        appTime = count?.let { (it / 2).minutes },
        isEstimate = count != null,
    )
} + SampleActivity.today.summarize(previewDate)

private fun content(status: TrackingStatus = TrackingStatus.Active) = TodayUiState.Content(
    date = previewDate,
    activities = SampleActivity.today,
    totalAppTime = 153.minutes,
    lastUpdatedMs = 1_792_000_000_000,
    status = status,
    isRefreshing = false,
    yesterday = previewWeek[5],
    week = previewWeek,
    profile = UserProfile(id = "1", username = "sam", displayName = "Sam Rivera", bio = null, avatarUrl = null),
)

@PreviewLightDark
@PreviewScreenSizes
@Composable
private fun TodayScreenWithActivityPreview() = PreviewTheme {
    TodayScreen(uiState = content(), onRefresh = {}, onSetUpTracking = {})
}

@PreviewLightDark
@Composable
private fun TodayScreenPausedPreview() = PreviewTheme {
    TodayScreen(uiState = content(TrackingStatus.Paused), onRefresh = {}, onSetUpTracking = {})
}

@PreviewLightDark
@Composable
private fun TodayScreenNeedsSetupPreview() = PreviewTheme {
    TodayScreen(uiState = TodayUiState.NeedsSetup(greetingName = "Sam"), onRefresh = {}, onSetUpTracking = {})
}
