package com.reelcounter.core.common

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject

/** Whether any of the app's UI is visible. Used to keep realtime connections foreground-only. */
fun interface AppForegroundMonitor {
    fun isForeground(): Flow<Boolean>
}

internal class ProcessAppForegroundMonitor @Inject constructor() : AppForegroundMonitor {
    override fun isForeground(): Flow<Boolean> = callbackFlow {
        val lifecycle = ProcessLifecycleOwner.get().lifecycle
        val observer = object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                trySend(true)
            }

            override fun onStop(owner: LifecycleOwner) {
                trySend(false)
            }
        }
        // Lifecycle observers must be added and removed on the main thread.
        lifecycle.addObserver(observer)
        awaitClose { lifecycle.removeObserver(observer) }
    }.flowOn(Dispatchers.Main.immediate).distinctUntilChanged()
}
