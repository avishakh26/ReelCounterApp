package com.reelcounter.core.preview

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import com.reelcounter.core.designsystem.theme.ReelCounterTheme

/** Wraps a preview in the app theme with brand colors, so previews look the same on every machine. */
@Composable
fun PreviewTheme(content: @Composable () -> Unit) {
    ReelCounterTheme(dynamicColor = false) {
        Surface(content = content)
    }
}
