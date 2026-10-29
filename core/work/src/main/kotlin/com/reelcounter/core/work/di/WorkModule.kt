package com.reelcounter.core.work.di

import android.content.Context
import com.reelcounter.core.common.AppInitializer
import com.reelcounter.core.domain.sync.SyncScheduler
import com.reelcounter.core.work.TrackingWork
import com.reelcounter.core.work.WorkManagerSyncScheduler
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
internal abstract class WorkModule {

    @Binds
    abstract fun bindSyncScheduler(impl: WorkManagerSyncScheduler): SyncScheduler

    companion object {
        /** Schedules the hourly usage-log read at app start (idempotent). */
        @Provides
        @IntoSet
        fun provideTrackingWorkInitializer(@ApplicationContext context: Context): AppInitializer =
            AppInitializer { TrackingWork.schedule(context) }
    }
}
