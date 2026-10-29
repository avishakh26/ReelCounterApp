package com.reelcounter.core.tracking

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.reelcounter.core.domain.tracking.CurrentDateProvider
import com.reelcounter.core.domain.tracking.InstalledApps
import com.reelcounter.core.domain.tracking.TimeSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

internal class SystemTimeSource @Inject constructor() : TimeSource {
    override fun nowMs(): Long = System.currentTimeMillis()

    // Android resets the process default zone when the device zone changes.
    override fun zone(): ZoneId = ZoneId.systemDefault()
}

internal class PackageManagerInstalledApps @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : InstalledApps {
    override fun isAnyInstalled(packages: Set<String>): Boolean = packages.any(::isInstalled)

    private fun isInstalled(packageName: String): Boolean = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(packageName, 0)
        }
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }
}

/**
 * Emits today's date now, at every local midnight, and whenever the date, time or zone is
 * changed. DATE_CHANGED alone isn't enough: it's delivered late while the device sleeps.
 */
internal class AndroidCurrentDateProvider @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val timeSource: TimeSource,
) : CurrentDateProvider {

    override val today: Flow<LocalDate> = callbackFlow {
        fun emitToday() {
            trySend(LocalDate.now(timeSource.zone()))
        }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) = emitToday()
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_DATE_CHANGED)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        // System broadcasts still arrive with RECEIVER_NOT_EXPORTED; other apps' don't.
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        emitToday()

        val midnightTicker = launch {
            while (true) {
                val zone = timeSource.zone()
                val nextMidnight = LocalDate.now(zone).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
                delay((nextMidnight - timeSource.nowMs()).coerceAtLeast(0) + 1_000)
                emitToday()
            }
        }
        awaitClose {
            midnightTicker.cancel()
            context.unregisterReceiver(receiver)
        }
    }.distinctUntilChanged().conflate()
}
