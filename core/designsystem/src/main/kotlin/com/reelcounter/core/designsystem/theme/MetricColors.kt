package com.reelcounter.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Colors for one metric category: a tonal [container], text on it, and a stronger [accent]. */
@Immutable
data class MetricColorRole(
    val container: Color,
    val onContainer: Color,
    val accent: Color,
)

/**
 * Semantic colors for the four metric categories. They stay fixed under dynamic color, so
 * "Verified" and "Estimated" look the same on every device.
 *
 * Color never carries meaning alone: every badge pairs it with an icon and a text label.
 */
@Immutable
data class MetricColors(
    val verified: MetricColorRole,
    val estimated: MetricColorRole,
    val durationOnly: MetricColorRole,
    val unavailable: MetricColorRole,
)

internal val LightMetricColors = MetricColors(
    verified = MetricColorRole(
        container = Color(0xFFDDF3E5),
        onContainer = Color(0xFF0E4D2A),
        accent = Color(0xFF1E8A4C),
    ),
    estimated = MetricColorRole(
        container = Color(0xFFFFF1D6),
        onContainer = Color(0xFF6B4500),
        accent = Color(0xFFB26B00),
    ),
    durationOnly = MetricColorRole(
        container = Color(0xFFE8EDF6),
        onContainer = Color(0xFF2C3A52),
        accent = Color(0xFF5A6B87),
    ),
    // Replaced at runtime with the active scheme's neutrals; see ReelCounterTheme.
    unavailable = MetricColorRole(
        container = Color(0xFFE4E1E9),
        onContainer = Color(0xFF46464F),
        accent = Color(0xFF767680),
    ),
)

internal val DarkMetricColors = MetricColors(
    verified = MetricColorRole(
        container = Color(0xFF113A24),
        onContainer = Color(0xFFBFF0D1),
        accent = Color(0xFF6FD39A),
    ),
    estimated = MetricColorRole(
        container = Color(0xFF3D2C08),
        onContainer = Color(0xFFFFDDA0),
        accent = Color(0xFFF0B85C),
    ),
    durationOnly = MetricColorRole(
        container = Color(0xFF222B3A),
        onContainer = Color(0xFFD5DEEE),
        accent = Color(0xFF9DB0CF),
    ),
    unavailable = MetricColorRole(
        container = Color(0xFF35343A),
        onContainer = Color(0xFFC7C5D0),
        accent = Color(0xFF91909A),
    ),
)

val LocalMetricColors = staticCompositionLocalOf { LightMetricColors }
