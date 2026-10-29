package com.reelcounter.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.PreviewFontScale
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.reelcounter.core.designsystem.component.EmptyState
import com.reelcounter.core.designsystem.component.ErrorState
import com.reelcounter.core.designsystem.component.OfflineBanner
import com.reelcounter.core.designsystem.component.OfflineState
import com.reelcounter.core.designsystem.component.RcAlertDialog
import com.reelcounter.core.designsystem.component.RcButton
import com.reelcounter.core.designsystem.component.RcOutlinedButton
import com.reelcounter.core.designsystem.component.RcStatCard
import com.reelcounter.core.designsystem.component.RcTag
import com.reelcounter.core.designsystem.component.RcTextButton
import com.reelcounter.core.designsystem.component.RcTopAppBar
import com.reelcounter.core.designsystem.component.SkeletonList
import com.reelcounter.core.designsystem.icon.RcIcons
import com.reelcounter.core.designsystem.theme.ReelCounterTheme
import com.reelcounter.core.designsystem.theme.Spacing

// Debug-only previews. This module can't depend on :core:preview (that module depends on this
// one), so it wraps previews in the theme directly. Brand colors, not dynamic, keep them stable.

@Composable
private fun PreviewSurface(content: @Composable () -> Unit) {
    ReelCounterTheme(dynamicColor = false) {
        Surface(content = content)
    }
}

@PreviewLightDark
@Composable
private fun ColorRolesPreview() = PreviewSurface {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        listOf(
            "primary" to scheme.primary,
            "primaryContainer" to scheme.primaryContainer,
            "secondaryContainer" to scheme.secondaryContainer,
            "tertiary" to scheme.tertiary,
            "surfaceContainerLow" to scheme.surfaceContainerLow,
            "error" to scheme.error,
        ).forEach { (name, color) -> Swatch(name, color) }
    }
}

@Composable
private fun Swatch(name: String, color: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Box(
            Modifier
                .size(24.dp)
                .background(color, MaterialTheme.shapes.extraSmall),
        )
        Text(name, style = MaterialTheme.typography.bodySmall)
    }
}

@PreviewLightDark
@PreviewFontScale
@Composable
private fun TypographyPreview() = PreviewSurface {
    Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text("1,284", style = MaterialTheme.typography.displaySmall)
        Text("Headline", style = MaterialTheme.typography.headlineMedium)
        Text("Title", style = MaterialTheme.typography.titleMedium)
        Text("Body text explains a number in plain words.", style = MaterialTheme.typography.bodyMedium)
        Text("LABEL", style = MaterialTheme.typography.labelMedium)
    }
}

@PreviewLightDark
@Composable
private fun ButtonsPreview() = PreviewSurface {
    Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        RcButton(text = "Continue", onClick = {})
        RcButton(text = "Saving", onClick = {}, loading = true)
        RcButton(text = "Disabled", onClick = {}, enabled = false)
        RcOutlinedButton(text = "Try again", onClick = {}, leadingIcon = RcIcons.Retry)
        RcTextButton(text = "Learn more", onClick = {})
    }
}

@PreviewLightDark
@Composable
private fun CardsAndTagsPreview() = PreviewSurface {
    val metric = ReelCounterTheme.metricColors
    Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        RcStatCard(
            label = "App time today",
            value = "1 h 12 min",
            supportingText = "Across 3 platforms",
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            RcTag("Verified", RcIcons.Verified, metric.verified.container, metric.verified.onContainer)
            RcTag("Estimated", RcIcons.Estimated, metric.estimated.container, metric.estimated.onContainer)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            RcTag("App time only", RcIcons.DurationOnly, metric.durationOnly.container, metric.durationOnly.onContainer)
            RcTag("Not tracked", RcIcons.Unavailable, metric.unavailable.container, metric.unavailable.onContainer)
        }
    }
}

@PreviewLightDark
@Composable
private fun TopAppBarPreview() = PreviewSurface {
    RcTopAppBar(
        title = "How we count",
        navigationIcon = RcIcons.Back,
        navigationIconContentDescription = "Back",
    )
}

@PreviewLightDark
@Composable
private fun SkeletonPreview() = PreviewSurface {
    SkeletonList(count = 2)
}

@PreviewLightDark
@Composable
private fun EmptyStatePreview() = PreviewSurface {
    EmptyState(
        title = "No activity yet",
        message = "Today's app time and estimates appear here once tracking is set up.",
        icon = RcIcons.Today,
        action = { RcButton(text = "Set up tracking", onClick = {}) },
    )
}

@PreviewLightDark
@Composable
private fun ErrorStatePreview() = PreviewSurface {
    ErrorState(
        title = "Something went wrong",
        message = "We couldn't load this page.",
        retryLabel = "Try again",
        onRetry = {},
    )
}

@PreviewLightDark
@Composable
private fun OfflinePreview() = PreviewSurface {
    Column {
        OfflineBanner(text = "You're offline. Showing saved data.")
        OfflineState(
            title = "You're offline",
            message = "Reconnect to load this page.",
            retryLabel = "Try again",
            onRetry = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun DestructiveDialogPreview() = PreviewSurface {
    RcAlertDialog(
        title = "Delete account?",
        text = "This permanently deletes your profile and activity history.",
        confirmText = "Delete",
        dismissText = "Cancel",
        onConfirm = {},
        onDismiss = {},
        isDestructive = true,
    )
}
