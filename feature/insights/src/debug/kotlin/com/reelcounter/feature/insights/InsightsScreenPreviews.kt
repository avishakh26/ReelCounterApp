package com.reelcounter.feature.insights

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.reelcounter.core.preview.PreviewTheme
import com.reelcounter.core.preview.SampleActivity
import java.time.LocalDate

private val previewToday = LocalDate.of(2026, 10, 20)

// Fictional activity for previews only: a few days with data and some without.
private val previewState = run {
    val dates = (13 downTo 0).map { previewToday.minusDays(it.toLong()) }
    val days = dates.mapIndexed { index, _ -> if (index % 4 == 1) emptyList() else SampleActivity.today }
    buildState(dates, days)
}

@PreviewLightDark
@Composable
private fun InsightsScreenPreview() = PreviewTheme {
    InsightsScreen(uiState = previewState)
}

@PreviewLightDark
@Composable
private fun InsightsScreenEmptyPreview() = PreviewTheme {
    InsightsScreen(uiState = InsightsUiState.Empty)
}
