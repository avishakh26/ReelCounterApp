package com.reelcounter.core.data.di

import com.reelcounter.core.common.AppInitializer
import com.reelcounter.core.data.auth.DataStorePasswordResetFlag
import com.reelcounter.core.data.auth.DefaultAuthRepository
import com.reelcounter.core.data.auth.PasswordResetFlag
import com.reelcounter.core.data.profile.AndroidAvatarImageProcessor
import com.reelcounter.core.data.profile.DefaultProfileRepository
import com.reelcounter.core.data.social.DefaultFriendActivityRepository
import com.reelcounter.core.data.social.DefaultFriendsRepository
import com.reelcounter.core.data.social.DefaultPrivacyRepository
import com.reelcounter.core.data.social.RealtimeCoordinator
import com.reelcounter.core.data.sync.AccountDataGuard
import com.reelcounter.core.data.sync.ActivitySyncer
import com.reelcounter.core.data.tracking.DefaultPlatformSettingsRepository
import com.reelcounter.core.data.tracking.DefaultReelCountingRecorder
import com.reelcounter.core.data.tracking.DefaultTrackingRepository
import com.reelcounter.core.data.tracking.RoomTrackingLocalStore
import com.reelcounter.core.data.tracking.TrackingLocalStore
import com.reelcounter.core.domain.auth.AuthRepository
import com.reelcounter.core.domain.profile.AvatarImageProcessor
import com.reelcounter.core.domain.profile.ProfileRepository
import com.reelcounter.core.domain.social.FriendActivityRepository
import com.reelcounter.core.domain.social.FriendsRepository
import com.reelcounter.core.domain.social.PresenceRepository
import com.reelcounter.core.domain.social.PrivacyRepository
import com.reelcounter.core.domain.sync.ActivitySync
import com.reelcounter.core.domain.tracking.PlatformSettingsRepository
import com.reelcounter.core.domain.tracking.ReelCountingRecorder
import com.reelcounter.core.domain.tracking.TrackingRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
internal abstract class DataModule {

    @Binds
    abstract fun bindAuthRepository(impl: DefaultAuthRepository): AuthRepository

    @Binds
    abstract fun bindProfileRepository(impl: DefaultProfileRepository): ProfileRepository

    @Binds
    abstract fun bindAvatarImageProcessor(impl: AndroidAvatarImageProcessor): AvatarImageProcessor

    @Binds
    abstract fun bindPasswordResetFlag(impl: DataStorePasswordResetFlag): PasswordResetFlag

    @Binds
    abstract fun bindTrackingRepository(impl: DefaultTrackingRepository): TrackingRepository

    @Binds
    abstract fun bindPlatformSettingsRepository(impl: DefaultPlatformSettingsRepository): PlatformSettingsRepository

    @Binds
    abstract fun bindTrackingLocalStore(impl: RoomTrackingLocalStore): TrackingLocalStore

    @Binds
    abstract fun bindActivitySync(impl: ActivitySyncer): ActivitySync

    @Binds
    @IntoSet
    abstract fun bindReelCountingInitializer(impl: DefaultReelCountingRecorder): AppInitializer

    @Binds
    @IntoSet
    abstract fun bindAccountDataGuard(impl: AccountDataGuard): AppInitializer

    @Binds
    abstract fun bindFriendsRepository(impl: DefaultFriendsRepository): FriendsRepository

    @Binds
    abstract fun bindReelCountingRecorder(impl: DefaultReelCountingRecorder): ReelCountingRecorder

    @Binds
    abstract fun bindFriendActivityRepository(impl: DefaultFriendActivityRepository): FriendActivityRepository

    @Binds
    abstract fun bindPrivacyRepository(impl: DefaultPrivacyRepository): PrivacyRepository

    @Binds
    abstract fun bindPresenceRepository(impl: RealtimeCoordinator): PresenceRepository

    @Binds
    @IntoSet
    abstract fun bindRealtimeCoordinator(impl: RealtimeCoordinator): AppInitializer
}
