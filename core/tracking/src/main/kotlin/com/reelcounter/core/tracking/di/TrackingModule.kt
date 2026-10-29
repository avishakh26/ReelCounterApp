package com.reelcounter.core.tracking.di

import com.reelcounter.core.domain.tracking.CurrentDateProvider
import com.reelcounter.core.domain.tracking.InstalledApps
import com.reelcounter.core.domain.tracking.ReelCountingStatus
import com.reelcounter.core.domain.tracking.TimeSource
import com.reelcounter.core.domain.tracking.UsageAccessMonitor
import com.reelcounter.core.domain.tracking.UsageEventSource
import com.reelcounter.core.tracking.AndroidCurrentDateProvider
import com.reelcounter.core.tracking.AppOpsUsageAccessMonitor
import com.reelcounter.core.tracking.PackageManagerInstalledApps
import com.reelcounter.core.tracking.SystemTimeSource
import com.reelcounter.core.tracking.UsageStatsEventSource
import com.reelcounter.core.tracking.counting.AccessibilityReelCountingStatus
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal abstract class TrackingModule {
    @Binds abstract fun bindUsageEventSource(impl: UsageStatsEventSource): UsageEventSource

    @Binds abstract fun bindUsageAccessMonitor(impl: AppOpsUsageAccessMonitor): UsageAccessMonitor

    @Binds abstract fun bindInstalledApps(impl: PackageManagerInstalledApps): InstalledApps

    @Binds abstract fun bindTimeSource(impl: SystemTimeSource): TimeSource

    @Binds abstract fun bindCurrentDateProvider(impl: AndroidCurrentDateProvider): CurrentDateProvider

    @Binds abstract fun bindReelCountingStatus(impl: AccessibilityReelCountingStatus): ReelCountingStatus
}
