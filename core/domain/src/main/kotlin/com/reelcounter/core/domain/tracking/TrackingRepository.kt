package com.reelcounter.core.domain.tracking

import com.reelcounter.core.model.DailyPlatformActivity
import com.reelcounter.core.model.Platform
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/** Measuring app time on this device and turning it into daily figures. */
interface TrackingRepository {
    val status: Flow<TrackingStatus>

    /** When the usage log was last read successfully; null before the first read. */
    val lastUpdatedMs: Flow<Long?>

    /** Every tracked platform for [date], with its metric category. Updates as data arrives. */
    fun observeDay(date: LocalDate): Flow<List<DailyPlatformActivity>>

    /** Reads new usage events and updates today's and recent days' figures. Safe to call any time. */
    suspend fun refresh(): RefreshResult

    /** The user agreed to the prominent disclosure (Play User Data policy) for [version]. */
    suspend fun acceptDisclosure(version: Int)

    suspend fun setPaused(paused: Boolean)

    /** Records the device's current time zone after a time or time-zone change, then refreshes. */
    suspend fun onTimeChanged()
}

enum class TrackingStatus {
    /** The disclosure hasn't been accepted (or a newer version needs accepting). */
    NeedsDisclosure,

    /** Usage access isn't granted, or was revoked in system settings. */
    NeedsUsageAccess,
    Paused,
    Active,
}

enum class RefreshResult { Updated, NotPermitted, Paused, DeviceLocked }

/**
 * The disclosure text version the app currently shows. Bump it when what's collected or shared
 * changes, so everyone sees the new text and agrees again.
 *  1 (Phase 4): app time read and kept on the device.
 *  2 (Phase 5): daily totals also saved to the user's account.
 *  3: optional reel counting with an accessibility service.
 */
const val CURRENT_DISCLOSURE_VERSION = 4

/** Daily totals are uploaded only for users who agreed to a disclosure that says so. */
const val UPLOAD_CONSENT_VERSION = 2

/** Which platforms are tracked, and how each one is estimated. */
interface PlatformSettingsRepository {
    val platforms: Flow<List<PlatformConfiguration>>

    suspend fun setTracked(platformId: String, tracked: Boolean)

    /** Turning estimation off shows app time only (DURATION_ONLY) for the platform. */
    suspend fun setEstimationEnabled(platformId: String, enabled: Boolean)

    /** null resets a value to the platform default. Recomputes recent, non-final days. */
    suspend fun setEstimationOverrides(platformId: String, shortFormShare: Double?, secondsPerReel: Double?)
}

data class PlatformConfiguration(
    val platform: Platform,
    val isInstalled: Boolean,
    val isTracked: Boolean,
    val estimationEnabled: Boolean,
    val defaults: EstimationProfile,
    val shortFormShareOverride: Double?,
    val secondsPerReelOverride: Double?,
)

/** Whether this app may read the usage log (the PACKAGE_USAGE_STATS app-op). */
interface UsageAccessMonitor {
    /** Emits now and whenever the user changes the setting. */
    val isGranted: Flow<Boolean>

    fun isGrantedNow(): Boolean
}

/**
 * The id this phone uploads its daily rows under for [userId]. The same on every install of the app
 * on this phone, so a reinstall overwrites its earlier rows instead of adding a second copy that
 * friends would see summed. Different per account, and not a hardware identifier itself.
 */
interface DeviceIdProvider {
    fun deviceIdFor(userId: String): String
}

interface InstalledApps {
    /** True if any of [packages] is installed (they must be listed in the manifest's <queries>). */
    fun isAnyInstalled(packages: Set<String>): Boolean
}

/** Wall-clock time and the device zone, injectable so tests control them. */
interface TimeSource {
    fun nowMs(): Long
    fun zone(): java.time.ZoneId
}

/** Today's date, re-emitted at local midnight and when the time or zone changes. */
interface CurrentDateProvider {
    val today: Flow<LocalDate>
}
