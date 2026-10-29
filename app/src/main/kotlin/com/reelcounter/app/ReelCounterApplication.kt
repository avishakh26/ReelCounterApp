package com.reelcounter.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.reelcounter.core.common.AppInitializer
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class ReelCounterApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    /** Start-up work contributed by modules (background schedules, the account data guard). */
    @Inject
    lateinit var initializers: Set<@JvmSuppressWildcards AppInitializer>

    // WorkManager is initialized on demand with Hilt's factory (the default initializer is
    // removed in the manifest), so workers can have injected dependencies.
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        initializers.forEach { it.initialize() }
    }
}
