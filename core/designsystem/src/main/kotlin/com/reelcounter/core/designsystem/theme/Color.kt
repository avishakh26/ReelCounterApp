package com.reelcounter.core.designsystem.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Brand palette: a neutral foundation with one refined indigo-violet accent. Color is reserved
// for primary actions, selection and the headline metric; everything else stays neutral.
//
// Surface roles used by the design system:
// - background / surface: the screen behind everything
// - surfaceContainerLowest: cards, fields, dialogs (white in light mode, raised in dark mode)
// - surfaceContainer*: tracks, chips and pressed or selected fills
// - outlineVariant: hairline borders and dividers

internal val LightColors = lightColorScheme(
    primary = Color(0xFF5B4CDB),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFECEAFD),
    onPrimaryContainer = Color(0xFF241A73),
    inversePrimary = Color(0xFFB8AFFF),
    secondary = Color(0xFF5E5E6B),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFEDEDF1),
    onSecondaryContainer = Color(0xFF1E1E25),
    // Tertiary carries "positive" signals such as online presence.
    tertiary = Color(0xFF1E8A4C),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFDDF3E5),
    onTertiaryContainer = Color(0xFF0B3A1F),
    error = Color(0xFFC42B3C),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFCE6E8),
    onErrorContainer = Color(0xFF5C0A13),
    background = Color(0xFFF6F6F8),
    onBackground = Color(0xFF16161B),
    surface = Color(0xFFF6F6F8),
    onSurface = Color(0xFF16161B),
    surfaceVariant = Color(0xFFECECF0),
    onSurfaceVariant = Color(0xFF62626E),
    inverseSurface = Color(0xFF2A2A31),
    inverseOnSurface = Color(0xFFF3F3F6),
    outline = Color(0xFFA3A3AE),
    outlineVariant = Color(0xFFE4E4E9),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFFAFAFC),
    surfaceDim = Color(0xFFDCDCE2),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF2F2F5),
    surfaceContainer = Color(0xFFEDEDF1),
    surfaceContainerHigh = Color(0xFFE7E7EC),
    surfaceContainerHighest = Color(0xFFE1E1E7),
)

internal val DarkColors = darkColorScheme(
    primary = Color(0xFFA99FFF),
    onPrimary = Color(0xFF1E1466),
    primaryContainer = Color(0xFF2C2470),
    onPrimaryContainer = Color(0xFFE6E2FF),
    inversePrimary = Color(0xFF5B4CDB),
    secondary = Color(0xFFC3C3CE),
    onSecondary = Color(0xFF2A2A33),
    secondaryContainer = Color(0xFF26262D),
    onSecondaryContainer = Color(0xFFE4E4EA),
    tertiary = Color(0xFF6FD39A),
    onTertiary = Color(0xFF003A1C),
    tertiaryContainer = Color(0xFF0F3A23),
    onTertiaryContainer = Color(0xFFBFF0D1),
    error = Color(0xFFFF8A94),
    onError = Color(0xFF5C0A13),
    errorContainer = Color(0xFF4A141B),
    onErrorContainer = Color(0xFFFFD9DC),
    background = Color(0xFF0D0D10),
    onBackground = Color(0xFFECECF1),
    surface = Color(0xFF0D0D10),
    onSurface = Color(0xFFECECF1),
    surfaceVariant = Color(0xFF24242B),
    onSurfaceVariant = Color(0xFFA1A1AD),
    inverseSurface = Color(0xFFECECF1),
    inverseOnSurface = Color(0xFF1C1C22),
    outline = Color(0xFF5A5A66),
    outlineVariant = Color(0xFF2A2A32),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF2E2E35),
    surfaceDim = Color(0xFF0D0D10),
    // In dark mode cards sit slightly above the background instead of below it.
    surfaceContainerLowest = Color(0xFF16161B),
    surfaceContainerLow = Color(0xFF1A1A20),
    surfaceContainer = Color(0xFF1F1F25),
    surfaceContainerHigh = Color(0xFF26262D),
    surfaceContainerHighest = Color(0xFF2E2E35),
)
