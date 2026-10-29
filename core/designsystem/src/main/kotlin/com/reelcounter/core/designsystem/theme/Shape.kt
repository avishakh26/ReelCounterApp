package com.reelcounter.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Corner radii. Each role has one job, so corners stay consistent:
 * - extraSmall: tags and chart bars
 * - small: icon tiles and segmented-control thumbs
 * - medium: buttons and text fields
 * - large: cards
 * - extraLarge: dialogs and bottom sheets
 */
internal val RcShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp),
)
