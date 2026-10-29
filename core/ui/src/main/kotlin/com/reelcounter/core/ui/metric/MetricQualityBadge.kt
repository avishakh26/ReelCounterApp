package com.reelcounter.core.ui.metric

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.reelcounter.core.designsystem.component.RcTag
import com.reelcounter.core.designsystem.icon.RcIcons
import com.reelcounter.core.designsystem.theme.MetricColorRole
import com.reelcounter.core.designsystem.theme.MetricColors
import com.reelcounter.core.designsystem.theme.ReelCounterTheme
import com.reelcounter.core.designsystem.theme.Spacing
import com.reelcounter.core.model.MetricQuality
import com.reelcounter.core.ui.R

/**
 * The category label every reel figure carries (Verified, Estimated, App time only, Not tracked).
 * When [userAdjusted] is true, an "Adjusted by you" tag follows, so a manual correction is never
 * mistaken for a measurement.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MetricQualityBadge(
    quality: MetricQuality,
    modifier: Modifier = Modifier,
    userAdjusted: Boolean = false,
) {
    val colors = ReelCounterTheme.metricColors
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        val role = colors.roleFor(quality)
        RcTag(
            text = stringResource(quality.labelRes),
            icon = quality.icon,
            containerColor = role.container,
            contentColor = role.onContainer,
        )
        if (userAdjusted) {
            RcTag(
                text = stringResource(R.string.core_ui_metric_user_adjusted),
                icon = RcIcons.UserAdjusted,
                containerColor = colors.unavailable.container,
                contentColor = colors.unavailable.onContainer,
            )
        }
    }
}

@get:StringRes
val MetricQuality.labelRes: Int
    get() = when (this) {
        MetricQuality.VERIFIED -> R.string.core_ui_metric_verified
        MetricQuality.COUNTED -> R.string.core_ui_metric_counted
        MetricQuality.ESTIMATED -> R.string.core_ui_metric_estimated
        MetricQuality.DURATION_ONLY -> R.string.core_ui_metric_duration_only
        MetricQuality.UNAVAILABLE -> R.string.core_ui_metric_unavailable
    }

val MetricQuality.icon: ImageVector
    get() = when (this) {
        MetricQuality.VERIFIED -> RcIcons.Verified
        MetricQuality.COUNTED -> RcIcons.Reels
        MetricQuality.ESTIMATED -> RcIcons.Estimated
        MetricQuality.DURATION_ONLY -> RcIcons.DurationOnly
        MetricQuality.UNAVAILABLE -> RcIcons.Unavailable
    }

fun MetricColors.roleFor(quality: MetricQuality): MetricColorRole = when (quality) {
    MetricQuality.VERIFIED, MetricQuality.COUNTED -> verified
    MetricQuality.ESTIMATED -> estimated
    MetricQuality.DURATION_ONLY -> durationOnly
    MetricQuality.UNAVAILABLE -> unavailable
}
