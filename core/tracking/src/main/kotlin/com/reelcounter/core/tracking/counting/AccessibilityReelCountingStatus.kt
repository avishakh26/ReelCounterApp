package com.reelcounter.core.tracking.counting

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import com.reelcounter.core.domain.tracking.ReelCountingStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject

/** Reads whether [ReelAccessibilityService] is turned on in the system's accessibility settings. */
internal class AccessibilityReelCountingStatus @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : ReelCountingStatus {

    private val component = ComponentName(context, ReelAccessibilityService::class.java)

    override val isAvailable: Boolean = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getServiceInfo(component, PackageManager.ComponentInfoFlags.of(0))
        } else {
            context.packageManager.getServiceInfo(component, 0)
        }
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    override fun isEnabledNow(): Boolean {
        val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?: return false
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == component }
    }

    override val isEnabled: Flow<Boolean> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(isEnabledNow())
            }
        }
        val uri = Settings.Secure.getUriFor(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        context.contentResolver.registerContentObserver(uri, false, observer)
        trySend(isEnabledNow())
        awaitClose { context.contentResolver.unregisterContentObserver(observer) }
    }.distinctUntilChanged()
}
