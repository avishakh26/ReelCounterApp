package com.reelcounter.core.ui.metric

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.reelcounter.core.designsystem.component.RcCard
import com.reelcounter.core.designsystem.theme.Spacing
import com.reelcounter.core.designsystem.theme.withTabularNumbers
import com.reelcounter.core.model.DailyPlatformActivity
import com.reelcounter.core.model.Platform
import com.reelcounter.core.model.PlatformMetric
import com.reelcounter.core.model.UnavailableReason
import com.reelcounter.core.ui.R
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * One platform's day, as a row for a list card. The row follows the display rules from the
 * Phase 1 docs:
 * - an estimate always shows "≈" and its range;
 * - duration is labeled "app time", never "reels time";
 * - a duration-only day shows no count;
 * - every figure carries its category badge.
 *
 * Screen readers read the row as one item.
 */
@Composable
fun PlatformActivityRow(
    activity: DailyPlatformActivity,
    modifier: Modifier = Modifier,
) {
    val locale = LocalConfiguration.current.locales[0]
    val metric = activity.metric

    Row(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {}
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalAlignment = Alignment.Top,
    ) {
        PlatformLogo(platform = activity.platform)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Text(text = activity.platform.displayName, style = MaterialTheme.typography.titleMedium)
            supportingText(metric, locale)?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // A counted figure is exact, so it needs no qualifying badge.
            if (metric !is PlatformMetric.Counted) {
                MetricQualityBadge(
                    quality = metric.quality,
                    userAdjusted = (metric as? PlatformMetric.Estimated)?.userAdjusted == true,
                    modifier = Modifier.padding(top = Spacing.xxs),
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = headline(metric),
                style = MaterialTheme.typography.titleLarge.withTabularNumbers(),
                color = if (metric is PlatformMetric.Unavailable) {
                    MaterialTheme.colorScheme.outline
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                textAlign = TextAlign.End,
            )
            unit(metric)?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** [PlatformActivityRow] on its own card. Prefer rows inside one list card for several platforms. */
@Composable
fun PlatformActivityCard(
    activity: DailyPlatformActivity,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    RcCard(modifier = modifier, onClick = onClick, contentPadding = PaddingValues(vertical = Spacing.xs)) {
        PlatformActivityRow(activity = activity)
    }
}

/**
 * The platform's logo, bundled with the app so it shows whether or not that app is installed here
 * (a friend's platform you don't use still gets its logo). Decorative: the platform's name is
 * always shown next to it. Unknown platforms fall back to a neutral letter tile.
 */
@Composable
fun PlatformLogo(platform: Platform, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    val logo = logoFor(platform.id)
    if (logo != null) {
        Image(
            painter = painterResource(logo),
            contentDescription = null,
            modifier = modifier
                .size(size)
                .clip(MaterialTheme.shapes.small)
                // The white YouTube and Facebook tiles need an edge on light cards.
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.small),
        )
    } else {
        PlatformMonogram(name = platform.displayName, modifier = modifier, size = size)
    }
}

@DrawableRes
private fun logoFor(platformId: String): Int? = when (platformId) {
    "tiktok" -> R.drawable.core_ui_logo_tiktok
    "instagram" -> R.drawable.core_ui_logo_instagram
    "youtube" -> R.drawable.core_ui_logo_youtube
    "facebook" -> R.drawable.core_ui_logo_facebook
    else -> null
}

/** A neutral letter tile, used when a platform's app isn't installed on this phone. */
@Composable
fun PlatformMonogram(name: String, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    Surface(
        modifier = modifier.size(size),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(text = name.take(1).uppercase(), style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun headline(metric: PlatformMetric): String = when (metric) {
    is PlatformMetric.Verified -> formatCount(metric.count)
    is PlatformMetric.Counted -> formatCount(metric.count)
    is PlatformMetric.Estimated -> stringResource(R.string.core_ui_metric_approx, formatCount(metric.count))
    is PlatformMetric.DurationOnly -> formatAppTime(metric.appTime)
    is PlatformMetric.Unavailable -> stringResource(R.string.core_ui_metric_no_value)
}

@Composable
private fun unit(metric: PlatformMetric): String? = when (metric) {
    is PlatformMetric.Verified -> pluralStringResource(R.plurals.core_ui_metric_unit_videos, metric.count)
    is PlatformMetric.Counted -> pluralStringResource(R.plurals.core_ui_metric_unit_reels, metric.count)
    is PlatformMetric.Estimated -> pluralStringResource(R.plurals.core_ui_metric_unit_reels, metric.count)
    is PlatformMetric.DurationOnly -> stringResource(R.string.core_ui_metric_unit_app_time)
    is PlatformMetric.Unavailable -> null
}

@Composable
private fun supportingText(metric: PlatformMetric, locale: Locale): String? = when (metric) {
    is PlatformMetric.Verified -> {
        val through = stringResource(R.string.core_ui_metric_verified_through, formatDate(metric.verifiedThrough, locale))
        metric.appTime?.let { appTime ->
            stringResource(
                R.string.core_ui_metric_joined,
                through,
                stringResource(R.string.core_ui_metric_app_time, formatAppTime(appTime)),
            )
        } ?: through
    }
    is PlatformMetric.Counted -> stringResource(R.string.core_ui_metric_app_time, formatAppTime(metric.appTime))
    // A range of one value ("Likely 0–0") says nothing, so only app time is shown then.
    is PlatformMetric.Estimated -> if (metric.low == metric.high) {
        stringResource(R.string.core_ui_metric_app_time, formatAppTime(metric.appTime))
    } else {
        stringResource(
            R.string.core_ui_metric_joined,
            stringResource(
                R.string.core_ui_metric_estimate_range,
                formatCount(metric.low),
                formatCount(metric.high),
            ),
            stringResource(R.string.core_ui_metric_app_time, formatAppTime(metric.appTime)),
        )
    }
    is PlatformMetric.DurationOnly -> stringResource(R.string.core_ui_metric_no_count_explainer)
    is PlatformMetric.Unavailable -> stringResource(metric.reason.messageRes)
}

private val UnavailableReason.messageRes: Int
    get() = when (this) {
        UnavailableReason.PERMISSION_MISSING -> R.string.core_ui_unavailable_permission_missing
        UnavailableReason.TRACKING_PAUSED -> R.string.core_ui_unavailable_tracking_paused
        UnavailableReason.APP_NOT_INSTALLED -> R.string.core_ui_unavailable_app_not_installed
        UnavailableReason.COVERAGE_GAP -> R.string.core_ui_unavailable_coverage_gap
        UnavailableReason.NOT_OBSERVABLE -> R.string.core_ui_unavailable_not_observable
    }

@Composable
private fun formatDate(date: LocalDate, locale: Locale): String =
    remember(date, locale) {
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).format(date)
    }
