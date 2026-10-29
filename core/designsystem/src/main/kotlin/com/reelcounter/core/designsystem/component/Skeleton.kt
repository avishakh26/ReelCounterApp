package com.reelcounter.core.designsystem.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.unit.dp
import com.reelcounter.core.designsystem.R
import com.reelcounter.core.designsystem.theme.Spacing
import com.reelcounter.core.designsystem.util.rememberReducedMotion

/**
 * A placeholder block with a moving shimmer. The animation is read only while drawing, so it
 * redraws without recomposing. It is static when the user has turned animations off.
 */
@Composable
fun SkeletonBlock(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.small,
) {
    val base = MaterialTheme.colorScheme.surfaceContainerHighest
    val highlight = MaterialTheme.colorScheme.surfaceContainerLow
    val progress = rememberShimmerProgress()

    Box(
        modifier = modifier
            .clip(shape)
            .clearAndSetSemantics {}
            .drawBehind {
                val p = progress?.value
                if (p == null) {
                    drawRect(base)
                } else {
                    val start = -size.width + p * 2 * size.width
                    drawRect(
                        Brush.linearGradient(
                            colors = listOf(base, highlight, base),
                            start = Offset(start, 0f),
                            end = Offset(start + size.width, size.height),
                        ),
                    )
                }
            },
    )
}

/** [lines] of placeholder text; the last line is shorter, like a real paragraph. */
@Composable
fun SkeletonText(
    modifier: Modifier = Modifier,
    lines: Int = 2,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        repeat(lines) { index ->
            SkeletonBlock(
                modifier = Modifier
                    .fillMaxWidth(if (index == lines - 1 && lines > 1) 0.6f else 1f)
                    .height(14.dp),
            )
        }
    }
}

/** Placeholder with the shape of a list card: an avatar, a title and two lines. */
@Composable
fun SkeletonCard(modifier: Modifier = Modifier) {
    RcCard(modifier = modifier) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SkeletonBlock(modifier = Modifier.size(40.dp), shape = CircleShape)
            SkeletonBlock(
                modifier = Modifier
                    .fillMaxWidth(0.4f)
                    .height(18.dp),
            )
        }
        SkeletonText(modifier = Modifier.padding(top = Spacing.md))
    }
}

/**
 * The default loading state for list screens. Screen readers announce it once as "Loading"
 * instead of reading each placeholder.
 */
@Composable
fun SkeletonList(
    modifier: Modifier = Modifier,
    count: Int = 3,
) {
    val loading = stringResource(R.string.core_designsystem_loading)
    Column(
        modifier = modifier
            .padding(Spacing.lg)
            .clearAndSetSemantics {
                contentDescription = loading
                progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
            },
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        repeat(count) { SkeletonCard(modifier = Modifier.fillMaxWidth()) }
    }
}

@Composable
private fun rememberShimmerProgress(): State<Float>? {
    if (rememberReducedMotion()) return null
    return rememberInfiniteTransition(label = "shimmer").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmerProgress",
    )
}
