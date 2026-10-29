package com.reelcounter.core.designsystem.component

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * A confirmation dialog. Set [isDestructive] for irreversible actions such as deleting data:
 * the confirm button turns red so the consequence is visible before the tap.
 *
 * Tapping outside or pressing back calls [onDismiss], never [onConfirm].
 */
@Composable
fun RcAlertDialog(
    title: String,
    text: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    dismissText: String? = null,
    icon: ImageVector? = null,
    isDestructive: Boolean = false,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        iconContentColor = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        icon = if (icon != null) {
            { Icon(imageVector = icon, contentDescription = null) }
        } else {
            null
        },
        title = { Text(text = title, style = MaterialTheme.typography.titleLarge) },
        text = { Text(text = text, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = if (isDestructive) {
                    ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                } else {
                    ButtonDefaults.textButtonColors()
                },
            ) {
                Text(text = confirmText)
            }
        },
        dismissButton = if (dismissText != null) {
            { TextButton(onClick = onDismiss) { Text(text = dismissText) } }
        } else {
            null
        },
    )
}
