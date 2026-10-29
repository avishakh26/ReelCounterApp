package com.reelcounter.core.designsystem.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * The ReelCounter theme.
 *
 * @param darkTheme follows the system by default. A user override arrives with Settings (Phase 8).
 * @param dynamicColor uses the wallpaper-based palette on Android 12+. Off by default so the brand
 * palette, and the meaning of its accent color, is the same on every device.
 */
@Composable
fun ReelCounterTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    val metricColors = remember(colorScheme, darkTheme) {
        (if (darkTheme) DarkMetricColors else LightMetricColors).withNeutralsFrom(colorScheme)
    }

    CompositionLocalProvider(LocalMetricColors provides metricColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = RcTypography,
            shapes = RcShapes,
            content = content,
        )
    }
}

/** Design tokens beyond what [MaterialTheme] provides. */
object ReelCounterTheme {
    val metricColors: MetricColors
        @Composable
        @ReadOnlyComposable
        get() = LocalMetricColors.current
}

// "Not tracked" is deliberately neutral, so it follows whichever surface palette is active.
private fun MetricColors.withNeutralsFrom(scheme: ColorScheme) = copy(
    unavailable = MetricColorRole(
        container = scheme.surfaceContainerHighest,
        onContainer = scheme.onSurfaceVariant,
        accent = scheme.outline,
    ),
)
