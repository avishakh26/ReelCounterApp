package com.reelcounter.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.reelcounter.core.designsystem.theme.ReelCounterTheme
import com.reelcounter.core.designsystem.theme.Spacing

/** The meaning of a banner or status pill. Each tone pairs its color with an icon or text. */
enum class RcTone { Neutral, Accent, Positive, Warning, Critical }

@Immutable
internal data class ToneColors(val container: Color, val content: Color, val accent: Color)

@Composable
@ReadOnlyComposable
internal fun RcTone.colors(): ToneColors {
    val scheme = MaterialTheme.colorScheme
    val metric = ReelCounterTheme.metricColors
    return when (this) {
        RcTone.Neutral -> ToneColors(scheme.surfaceContainer, scheme.onSurface, scheme.onSurfaceVariant)
        RcTone.Accent -> ToneColors(scheme.primaryContainer, scheme.onPrimaryContainer, scheme.primary)
        RcTone.Positive -> ToneColors(metric.verified.container, metric.verified.onContainer, metric.verified.accent)
        RcTone.Warning -> ToneColors(metric.estimated.container, metric.estimated.onContainer, metric.estimated.accent)
        RcTone.Critical -> ToneColors(scheme.errorContainer, scheme.onErrorContainer, scheme.error)
    }
}

/**
 * An inline notice above content, e.g. "Usage access is off". It explains the situation in
 * [message] and, when there's something to do about it, offers one [actionLabel].
 * Screen readers announce it when it appears.
 */
@Composable
fun RcBanner(
    message: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    title: String? = null,
    tone: RcTone = RcTone.Neutral,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val colors = tone.colors()
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
        shape = MaterialTheme.shapes.large,
        color = colors.container,
        contentColor = colors.content,
    ) {
        Column(modifier = Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier
                        .padding(top = Spacing.xxs)
                        .size(20.dp),
                )
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                    if (title != null) Text(text = title, style = MaterialTheme.typography.titleSmall)
                    Text(text = message, style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (actionLabel != null && onAction != null) {
                RcButton(
                    text = actionLabel,
                    onClick = onAction,
                    size = RcButtonSize.Small,
                    modifier = Modifier.padding(start = 32.dp),
                )
            }
        }
    }
}

/** A compact status label with a colored dot, e.g. "Tracking on". The text carries the meaning. */
@Composable
fun RcStatusPill(
    text: String,
    modifier: Modifier = Modifier,
    tone: RcTone = RcTone.Neutral,
) {
    val colors = tone.colors()
    Surface(modifier = modifier, shape = CircleShape, color = colors.container, contentColor = colors.content) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.sm + Spacing.xxs, vertical = Spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(colors.accent, CircleShape),
            )
            Text(text = text, style = MaterialTheme.typography.labelMedium)
        }
    }
}
