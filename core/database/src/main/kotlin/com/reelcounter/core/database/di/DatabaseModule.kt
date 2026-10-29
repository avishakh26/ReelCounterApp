package com.reelcounter.core.database.di

import android.content.Context
import androidx.room.Room
import com.reelcounter.core.database.ReelCounterDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): ReelCounterDatabase =
        Room.databaseBuilder(context, ReelCounterDatabase::class.java, "reelcounter.db").build()

    @Provides fun usageSessionDao(db: ReelCounterDatabase) = db.usageSessionDao()

    @Provides fun dailyUsageDao(db: ReelCounterDatabase) = db.dailyUsageDao()

    @Provides fun dayCoverageDao(db: ReelCounterDatabase) = db.dayCoverageDao()

    @Provides fun platformSettingsDao(db: ReelCounterDatabase) = db.platformSettingsDao()

    @Provides fun zoneChangeDao(db: ReelCounterDatabase) = db.zoneChangeDao()

    @Provides fun ingestStateDao(db: ReelCounterDatabase) = db.ingestStateDao()

    @Provides fun reelCountDao(db: ReelCounterDatabase) = db.reelCountDao()
}
