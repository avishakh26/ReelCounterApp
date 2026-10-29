package com.reelcounter.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewFontScale
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.reelcounter.core.designsystem.theme.Spacing
import com.reelcounter.core.model.DailyPlatformActivity
import com.reelcounter.core.model.MetricQuality
import com.reelcounter.core.preview.DailyPlatformActivityPreviewParameterProvider
import com.reelcounter.core.preview.PreviewTheme
import com.reelcounter.core.preview.SampleActivity
import com.reelcounter.core.ui.metric.MetricQualityBadge
import com.reelcounter.core.ui.metric.PlatformActivityCard
import com.reelcounter.core.ui.state.StateContent
import com.reelcounter.core.ui.state.UiState
import com.reelcounter.core.ui.state.UiText

@PreviewLightDark
@Composable
private fun PlatformActivityCardPreview(
    @PreviewParameter(DailyPlatformActivityPreviewParameterProvider::class) activity: DailyPlatformActivity,
) = PreviewTheme {
    PlatformActivityCard(activity = activity, modifier = Modifier.padding(Spacing.lg))
}

@PreviewFontScale
@Composable
private fun PlatformActivityCardFontScalePreview() = PreviewTheme {
    PlatformActivityCard(activity = SampleActivity.estimatedAdjusted, modifier = Modifier.padding(Spacing.lg))
}

@PreviewLightDark
@Composable
private fun MetricQualityBadgesPreview() = PreviewTheme {
    Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        MetricQuality.entries.forEach { MetricQualityBadge(quality = it) }
        MetricQualityBadge(quality = MetricQuality.ESTIMATED, userAdjusted = true)
    }
}

@PreviewLightDark
@Composable
private fun StateContentLoadingPreview() = PreviewTheme {
    StateContent(state = UiState.Loading) { _: Unit -> }
}

@PreviewLightDark
@Composable
private fun StateContentEmptyPreview() = PreviewTheme {
    StateContent(state = UiState.Empty()) { _: Unit -> }
}

@PreviewLightDark
@Composable
private fun StateContentErrorPreview() = PreviewTheme {
    StateContent(
        state = UiState.Error(UiText.Dynamic("We couldn't reach the server.")),
        onRetry = {},
    ) { _: Unit -> }
}

@PreviewLightDark
@Composable
private fun StateContentOfflinePreview() = PreviewTheme {
    StateContent(state = UiState.Offline, onRetry = {}) { _: Unit -> }
}
