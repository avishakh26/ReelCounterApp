package com.reelcounter.core.common.di

import com.reelcounter.core.common.AppForegroundMonitor
import com.reelcounter.core.common.ProcessAppForegroundMonitor
import com.reelcounter.core.common.dispatchers.ApplicationScope
import com.reelcounter.core.common.dispatchers.Dispatcher
import com.reelcounter.core.common.dispatchers.RcDispatchers
import com.reelcounter.core.common.network.ConnectivityManagerNetworkMonitor
import com.reelcounter.core.common.network.NetworkMonitor
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class CommonModule {

    @Binds
    abstract fun bindNetworkMonitor(impl: ConnectivityManagerNetworkMonitor): NetworkMonitor

    @Binds
    abstract fun bindAppForegroundMonitor(impl: ProcessAppForegroundMonitor): AppForegroundMonitor

    companion object {
        @Provides
        @Dispatcher(RcDispatchers.IO)
        fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

        @Provides
        @Dispatcher(RcDispatchers.Default)
        fun provideDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default

        // SupervisorJob: one failed child mustn't cancel unrelated app-wide work.
        @Provides
        @Singleton
        @ApplicationScope
        fun provideApplicationScope(
            @Dispatcher(RcDispatchers.Default) dispatcher: CoroutineDispatcher,
        ): CoroutineScope = CoroutineScope(SupervisorJob() + dispatcher)
    }
}
