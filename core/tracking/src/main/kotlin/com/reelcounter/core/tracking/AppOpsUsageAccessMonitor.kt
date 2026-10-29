package com.reelcounter.core.tracking

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Process
import androidx.core.content.getSystemService
import com.reelcounter.core.domain.tracking.UsageAccessMonitor
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject

/**
 * Usage access is an app-op, not a runtime permission: the user toggles it in system settings
 * at any time. This watches the op so the app notices a revoke or grant immediately.
 */
internal class AppOpsUsageAccessMonitor @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : UsageAccessMonitor {

    override val isGranted: Flow<Boolean> = callbackFlow {
        val appOps = context.getSystemService<AppOpsManager>()
        if (appOps == null) {
            trySend(false)
            close()
            return@callbackFlow
        }
        val listener = AppOpsManager.OnOpChangedListener { _, packageName ->
            if (packageName == context.packageName) trySend(isGrantedNow())
        }
        appOps.startWatchingMode(AppOpsManager.OPSTR_GET_USAGE_STATS, context.packageName, listener)
        trySend(isGrantedNow())
        awaitClose { appOps.stopWatchingMode(listener) }
    }.distinctUntilChanged()

    override fun isGrantedNow(): Boolean {
        val appOps = context.getSystemService<AppOpsManager>() ?: return false
        // checkOpNoThrow (API 19+) only reads the mode; it doesn't record an access the way noteOp does.
        // As of SDK 37 it is the non-deprecated form again, and unsafeCheckOpNoThrow is deprecated.
        val mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        // MODE_DEFAULT defers to the permission grant (some OEM builds report it that way).
        return if (mode == AppOpsManager.MODE_DEFAULT) {
            context.checkCallingOrSelfPermission(Manifest.permission.PACKAGE_USAGE_STATS) == PackageManager.PERMISSION_GRANTED
        } else {
            mode == AppOpsManager.MODE_ALLOWED
        }
    }
}
