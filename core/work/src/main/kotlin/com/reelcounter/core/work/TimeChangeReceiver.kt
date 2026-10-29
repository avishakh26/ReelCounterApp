package com.reelcounter.core.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.reelcounter.core.common.dispatchers.ApplicationScope
import com.reelcounter.core.domain.tracking.TrackingRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Records the new time zone the moment it changes, so sessions are dated where they happened
 * (travel), and recomputes recent days after a manual clock change.
 */
class TimeChangeReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Dependencies {
        fun trackingRepository(): TrackingRepository

        @ApplicationScope
        fun applicationScope(): CoroutineScope
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in HANDLED_ACTIONS) return
        val dependencies = EntryPointAccessors.fromApplication(context.applicationContext, Dependencies::class.java)
        val pending = goAsync()
        dependencies.applicationScope().launch {
            try {
                // Broadcast receivers get about 10 s; the read normally takes well under one.
                withTimeoutOrNull(RECEIVER_BUDGET_MS) { dependencies.trackingRepository().onTimeChanged() }
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        val HANDLED_ACTIONS = setOf(Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_TIME_CHANGED)
        const val RECEIVER_BUDGET_MS = 8_000L
    }
}
