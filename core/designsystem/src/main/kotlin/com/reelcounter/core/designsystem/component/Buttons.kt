package com.reelcounter.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.reelcounter.core.designsystem.theme.Spacing

/**
 * Button sizes. [Regular] is for screen-level actions (full width in forms); [Small] fits inside
 * rows and cards, and still keeps a 40 dp target plus the row's own padding.
 */
enum class RcButtonSize(internal val minHeight: Dp, internal val horizontalPadding: Dp) {
    Regular(minHeight = Spacing.buttonHeight, horizontalPadding = Spacing.xl),
    Small(minHeight = 40.dp, horizontalPadding = Spacing.lg),
}

/**
 * Primary action: the one thing a screen most wants the user to do. While [loading] is true the
 * button shows a spinner and ignores taps, so an action can't be submitted twice.
 */
@Composable
fun RcButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    leadingIcon: ImageVector? = null,
    size: RcButtonSize = RcButtonSize.Regular,
) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = size.minHeight),
        enabled = enabled && !loading,
        shape = MaterialTheme.shapes.medium,
        elevation = null,
        contentPadding = contentPaddingFor(size),
    ) {
        ButtonContent(text = text, leadingIcon = leadingIcon, loading = loading)
    }
}

/** Secondary action, shown alongside an [RcButton]: a hairline border on the card surface. */
@Composable
fun RcOutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    leadingIcon: ImageVector? = null,
    size: RcButtonSize = RcButtonSize.Regular,
) {
    val colors = MaterialTheme.colorScheme
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = size.minHeight),
        enabled = enabled && !loading,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, if (enabled) colors.outlineVariant else colors.outlineVariant.copy(alpha = 0.5f)),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = colors.surfaceContainerLowest,
            contentColor = colors.onSurface,
        ),
        contentPadding = contentPaddingFor(size),
    ) {
        ButtonContent(text = text, leadingIcon = leadingIcon, loading = loading)
    }
}

/** A quiet filled action for rows and cards, e.g. "Add friend", when a primary button would shout. */
@Composable
fun RcTonalButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    leadingIcon: ImageVector? = null,
    size: RcButtonSize = RcButtonSize.Small,
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = size.minHeight),
        enabled = enabled && !loading,
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
        contentPadding = contentPaddingFor(size),
    ) {
        ButtonContent(text = text, leadingIcon = leadingIcon, loading = loading)
    }
}

/** Low-emphasis action, for example inside cards and dialogs. */
@Composable
fun RcTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
    isDestructive: Boolean = false,
) {
    TextButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = Spacing.minTouchTarget),
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        colors = if (isDestructive) {
            ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
        } else {
            ButtonDefaults.textButtonColors()
        },
        contentPadding = PaddingValues(horizontal = Spacing.md, vertical = Spacing.sm),
    ) {
        ButtonContent(text = text, leadingIcon = leadingIcon, loading = false)
    }
}

private fun contentPaddingFor(size: RcButtonSize) =
    PaddingValues(horizontal = size.horizontalPadding, vertical = Spacing.sm)

@Composable
private fun RowScope.ButtonContent(text: String, leadingIcon: ImageVector?, loading: Boolean) {
    when {
        loading -> CircularProgressIndicator(
            modifier = Modifier.size(18.dp),
            color = LocalContentColor.current,
            strokeWidth = 2.dp,
        )
        leadingIcon != null -> Icon(
            imageVector = leadingIcon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
    }
    if (loading || leadingIcon != null) {
        Spacer(Modifier.width(Spacing.sm))
    }
    Text(text = text, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
}
