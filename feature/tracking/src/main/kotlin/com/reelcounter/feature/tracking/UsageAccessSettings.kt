package com.reelcounter.feature.tracking

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/**
 * Opens the system Usage access screen, on this app's own entry where the device supports it.
 * Usage access can't be requested with a dialog; the user grants it there.
 */
internal fun Context.openUsageAccessSettings() {
    val forThisApp = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, Uri.fromParts("package", packageName, null))
    val candidates = listOf(forThisApp, Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS), Intent(Settings.ACTION_SETTINGS))
    for (intent in candidates) {
        try {
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        } catch (_: ActivityNotFoundException) {
            // Some OEM builds lack the app-specific page; fall back to the list, then to Settings.
        }
    }
}

/** Opens the system Accessibility settings, where the user turns on ReelCounter reel counting. */
internal fun Context.openAccessibilitySettings() {
    for (intent in listOf(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS), Intent(Settings.ACTION_SETTINGS))) {
        try {
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        } catch (_: ActivityNotFoundException) {
            // Fall back to the main Settings screen.
        }
    }
}

/**
 * Opens this app's App info page. On Android 13+, apps installed from a file must first get
 * "Allow restricted settings" there (⋮ menu) before their accessibility service can be turned on.
 */
internal fun Context.openAppDetailsSettings() {
    try {
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    } catch (_: ActivityNotFoundException) {
        openAccessibilitySettings()
    }
}
