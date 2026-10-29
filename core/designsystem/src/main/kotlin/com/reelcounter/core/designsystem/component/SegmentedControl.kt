package com.reelcounter.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.reelcounter.core.designsystem.theme.Spacing

/** A single-choice switcher between two to four views of the same content, e.g. "Reels / App time". */
@Composable
fun RcSegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = colors.surfaceContainer,
    ) {
        Row(
            modifier = Modifier
                .padding(3.dp)
                .selectableGroup(),
        ) {
            options.forEachIndexed { index, option ->
                val selected = index == selectedIndex
                val container by animateColorAsState(
                    if (selected) colors.surfaceContainerLowest else Color.Transparent,
                    label = "segmentContainer",
                )
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(index) }),
                    shape = MaterialTheme.shapes.small,
                    color = container,
                    border = if (selected) BorderStroke(1.dp, colors.outlineVariant) else null,
                ) {
                    Box(
                        modifier = Modifier
                            .heightIn(min = 36.dp)
                            .padding(horizontal = Spacing.sm),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = option,
                            style = MaterialTheme.typography.labelLarge.copy(fontSize = MaterialTheme.typography.titleSmall.fontSize),
                            color = if (selected) colors.onSurface else colors.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}
