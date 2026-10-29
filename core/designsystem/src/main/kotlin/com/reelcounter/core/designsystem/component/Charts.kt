package com.reelcounter.core.designsystem.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.reelcounter.core.designsystem.theme.Spacing
import com.reelcounter.core.designsystem.util.rememberReducedMotion

/**
 * One bar of [RcBarChart].
 *
 * @param value the bar's height in any unit; null means "no data", drawn as a stub, never as zero.
 * @param valueLabel short text shown above a [highlighted] bar, e.g. "42".
 * @param description what a screen reader says for this bar, e.g. "Monday: about 42 reels".
 */
@Immutable
data class RcBar(
    val label: String,
    val value: Float?,
    val valueLabel: String,
    val description: String,
    val highlighted: Boolean = false,
)

/**
 * A compact vertical bar chart for a handful of days. Highlighted bars use the accent color;
 * the rest use a light tint of it. Bars grow in once when first shown, unless the user turned
 * animations off.
 */
@Composable
fun RcBarChart(
    bars: List<RcBar>,
    modifier: Modifier = Modifier,
    chartHeight: Dp = 120.dp,
) {
    val max = bars.maxOfOrNull { it.value ?: 0f }?.takeIf { it > 0f } ?: 1f
    val reducedMotion = rememberReducedMotion()
    val progress = remember { Animatable(if (reducedMotion) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!reducedMotion) progress.animateTo(1f, tween(durationMillis = 550, easing = FastOutSlowInEasing))
    }

    val colors = MaterialTheme.colorScheme
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(chartHeight + 20.dp),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalAlignment = Alignment.Bottom,
        ) {
            bars.forEach { bar ->
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clearAndSetSemantics { contentDescription = bar.description },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom,
                ) {
                    if (bar.highlighted && bar.value != null) {
                        Text(
                            text = bar.valueLabel,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = colors.primary,
                            maxLines = 1,
                            modifier = Modifier.padding(bottom = Spacing.xs),
                        )
                    }
                    val shape = RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp, bottomStart = 2.dp, bottomEnd = 2.dp)
                    if (bar.value == null) {
                        Box(
                            modifier = Modifier
                                .widthIn(max = 28.dp)
                                .fillMaxWidth(0.62f)
                                .height(4.dp)
                                .background(colors.surfaceContainerHigh, shape),
                        )
                    } else {
                        val fraction = (bar.value / max).coerceIn(0f, 1f) * progress.value
                        Box(
                            modifier = Modifier
                                .widthIn(max = 28.dp)
                                .fillMaxWidth(0.62f)
                                // A small floor keeps tiny-but-real values visible.
                                .height((chartHeight * fraction).coerceAtLeast(if (bar.value > 0f) 4.dp else 2.dp))
                                .background(
                                    if (bar.highlighted) colors.primary else colors.primary.copy(alpha = 0.22f),
                                    shape,
                                ),
                        )
                    }
                }
            }
        }
        HorizontalDivider(thickness = 1.dp, color = colors.outlineVariant)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Spacing.sm)
                .clearAndSetSemantics {},
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            bars.forEach { bar ->
                Text(
                    text = bar.label,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = if (bar.highlighted) FontWeight.SemiBold else FontWeight.Medium,
                    ),
                    color = if (bar.highlighted) colors.onSurface else colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                )
            }
        }
    }
}

/** A thin horizontal proportion bar, e.g. one platform's share of the week. */
@Composable
fun RcProgressBar(
    fraction: Float,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(6.dp)
            .background(colors.surfaceContainerHigh, RoundedCornerShape(3.dp)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(6.dp)
                .background(colors.primary, RoundedCornerShape(3.dp)),
        )
    }
}
