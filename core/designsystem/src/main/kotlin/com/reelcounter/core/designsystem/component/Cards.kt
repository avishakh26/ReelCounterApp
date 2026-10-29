package com.reelcounter.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.reelcounter.core.designsystem.theme.Spacing

/**
 * The base card: a raised surface with a hairline border and no shadow, so screens stay calm.
 * Pass [onClick] to make the whole card one tappable target. Without it, its content is read by
 * screen readers as a single item unless [mergeSemantics] is false (cards that hold controls).
 *
 * Use [contentPadding] = [PaddingValues] (0) for cards that hold full-bleed list rows.
 */
@Composable
fun RcCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(Spacing.lg),
    mergeSemantics: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)
    val border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    val shape = MaterialTheme.shapes.large
    val inner = Modifier.padding(contentPadding)
    if (onClick != null) {
        Card(onClick = onClick, modifier = modifier, shape = shape, colors = colors, border = border) {
            Column(modifier = inner, content = content)
        }
    } else {
        Card(
            modifier = if (mergeSemantics) modifier.semantics(mergeDescendants = true) {} else modifier,
            shape = shape,
            colors = colors,
            border = border,
        ) {
            Column(modifier = inner, content = content)
        }
    }
}

/** A card holding a vertical list of [RcListRow]s or [RcSwitchRow]s, separated by inset dividers. */
@Composable
fun RcListCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    RcCard(
        modifier = modifier,
        contentPadding = PaddingValues(vertical = Spacing.xs),
        mergeSemantics = false,
        content = content,
    )
}

/**
 * A single headline number with its label, e.g. "Total app time / 1 h 12 min".
 * [badge] sits under the value and is meant for a metric category tag.
 */
@Composable
fun RcStatCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    onClick: (() -> Unit)? = null,
    badge: (@Composable () -> Unit)? = null,
) {
    RcCard(modifier = modifier, onClick = onClick) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(text = value, style = MaterialTheme.typography.headlineSmall)
            if (supportingText != null) {
                Text(
                    text = supportingText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            badge?.invoke()
        }
    }
}
