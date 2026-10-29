package com.reelcounter.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

private val Base = Typography()

/**
 * The type scale, on the system sans-serif so it renders natively on every device. Larger sizes
 * get tighter tracking and heavier weights for a confident hierarchy; display styles use tabular
 * figures so counts don't shift width as they change.
 *
 * - display*: headline metrics (displayLarge is the dashboard's main number)
 * - headline*: screen titles
 * - titleLarge / titleMedium: section and card titles
 * - body*: running text and descriptions
 * - label*: buttons, tags, captions and chart labels
 */
internal val RcTypography = Typography(
    displayLarge = Base.displayLarge.copy(
        fontSize = 56.sp,
        lineHeight = 60.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.03).em,
    ).withTabularNumbers(),
    displayMedium = Base.displayMedium.copy(
        fontSize = 44.sp,
        lineHeight = 48.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.025).em,
    ).withTabularNumbers(),
    displaySmall = Base.displaySmall.copy(
        fontSize = 34.sp,
        lineHeight = 40.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.02).em,
    ).withTabularNumbers(),
    headlineLarge = Base.headlineLarge.copy(
        fontSize = 30.sp,
        lineHeight = 36.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.02).em,
    ),
    headlineMedium = Base.headlineMedium.copy(
        fontSize = 28.sp,
        lineHeight = 34.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.015).em,
    ),
    headlineSmall = Base.headlineSmall.copy(
        fontSize = 22.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.01).em,
    ),
    titleLarge = Base.titleLarge.copy(
        fontSize = 20.sp,
        lineHeight = 26.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.005).em,
    ),
    titleMedium = Base.titleMedium.copy(
        fontSize = 16.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.em,
    ),
    titleSmall = Base.titleSmall.copy(
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.em,
    ),
    bodyLarge = Base.bodyLarge.copy(fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.em),
    bodyMedium = Base.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.em),
    bodySmall = Base.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.01.em),
    labelLarge = Base.labelLarge.copy(
        fontSize = 15.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.em,
    ),
    labelMedium = Base.labelMedium.copy(
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.01.em,
    ).withTabularNumbers(),
    labelSmall = Base.labelSmall.copy(
        fontSize = 11.sp,
        lineHeight = 14.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.02.em,
    ).withTabularNumbers(),
)

/** Fixed-width digits, for numbers that update in place or line up in columns. */
fun TextStyle.withTabularNumbers(): TextStyle = copy(fontFeatureSettings = "tnum")
