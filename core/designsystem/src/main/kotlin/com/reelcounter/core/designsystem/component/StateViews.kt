package com.reelcounter.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.reelcounter.core.designsystem.icon.RcIcons
import com.reelcounter.core.designsystem.theme.Spacing

// Full-area states shown in place of content. Callers supply all text, so the design system
// holds no copy. :core:ui's StateContent fills in the defaults.

/** A centered spinner, for loads where a skeleton wouldn't match the final layout. */
@Composable
fun RcLoadingIndicator(modifier: Modifier = Modifier) {
    Box(modifier = modifier.padding(Spacing.xl), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

/** Nothing to show yet. [action] is an optional way forward, such as "Set up tracking". */
@Composable
fun EmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = RcIcons.Empty,
    action: (@Composable () -> Unit)? = null,
) {
    MessageState(icon = icon, title = title, message = message, modifier = modifier, action = action)
}

/** Loading failed. Show [onRetry] only when trying again can actually help. */
@Composable
fun ErrorState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    retryLabel: String? = null,
    onRetry: (() -> Unit)? = null,
) {
    MessageState(
        icon = RcIcons.Error,
        title = title,
        message = message,
        modifier = modifier,
        action = retryAction(retryLabel, onRetry),
    )
}

/** No connection and nothing cached to show. */
@Composable
fun OfflineState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    retryLabel: String? = null,
    onRetry: (() -> Unit)? = null,
) {
    MessageState(
        icon = RcIcons.Offline,
        title = title,
        message = message,
        modifier = modifier,
        action = retryAction(retryLabel, onRetry),
    )
}

/**
 * A slim notice above content that is still useful offline, e.g. cached friend stats.
 * Screen readers announce it when it appears.
 */
@Composable
fun OfflineBanner(
    text: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
        color = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(imageVector = RcIcons.Offline, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(text = text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private fun retryAction(retryLabel: String?, onRetry: (() -> Unit)?): (@Composable () -> Unit)? =
    if (retryLabel != null && onRetry != null) {
        { RcOutlinedButton(text = retryLabel, onClick = onRetry, leadingIcon = RcIcons.Retry, size = RcButtonSize.Small) }
    } else {
        null
    }

@Composable
private fun MessageState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier,
    action: (@Composable () -> Unit)?,
) {
    Box(modifier = modifier.padding(Spacing.xl), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.widthIn(max = 340.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            RcIconTile(
                icon = icon,
                size = 56.dp,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = Spacing.sm),
            )
            Text(
                text = title,
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (action != null) {
                Box(modifier = Modifier.padding(top = Spacing.md)) { action() }
            }
        }
    }
}
