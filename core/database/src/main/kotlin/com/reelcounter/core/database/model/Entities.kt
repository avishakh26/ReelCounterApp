package com.reelcounter.core.database.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

/**
 * A foreground session of one tracked app, derived from the usage log. Keyed by
 * (package, start) so re-reading an overlapping window can't duplicate it. Pruned after a few
 * weeks (data minimization); the daily totals are kept.
 */
@Entity(
    tableName = "usage_sessions",
    primaryKeys = ["package_name", "start_ms"],
    indices = [Index("start_ms"), Index("end_ms")],
)
data class UsageSessionEntity(
    @ColumnInfo(name = "package_name") val packageName: String,
    @ColumnInfo(name = "start_ms") val startMs: Long,
    @ColumnInfo(name = "end_ms") val endMs: Long,
    @ColumnInfo(name = "is_open") val isOpen: Boolean,
)

/**
 * One platform's figures for one local day. [date] is ISO yyyy-MM-dd, which sorts correctly as
 * text. [quality] holds a MetricQuality name. [dirty] marks rows not yet uploaded (Phase 5).
 */
@Entity(tableName = "daily_usage", primaryKeys = ["date", "platform_id"])
data class DailyUsageEntity(
    val date: String,
    @ColumnInfo(name = "platform_id") val platformId: String,
    @ColumnInfo(name = "foreground_ms") val foregroundMs: Long,
    @ColumnInfo(name = "session_count") val sessionCount: Int,
    val quality: String,
    @ColumnInfo(name = "estimated_reels") val estimatedReels: Int?,
    @ColumnInfo(name = "estimated_low") val estimatedLow: Int?,
    @ColumnInfo(name = "estimated_high") val estimatedHigh: Int?,
    @ColumnInfo(name = "estimation_version") val estimationVersion: Int?,
    @ColumnInfo(name = "user_adjusted") val userAdjusted: Boolean,
    @ColumnInfo(name = "tz_id") val tzId: String,
    @ColumnInfo(name = "is_final") val isFinal: Boolean,
    val revision: Long,
    val dirty: Boolean,
    /** Set when the server permanently rejected this row (e.g. a failed check); it isn't retried. v2. */
    @ColumnInfo(name = "sync_error") val syncError: String? = null,
)

/**
 * The part of a local day that was actually read from the usage log. A day with no coverage
 * shows "Not tracked" rather than zero.
 */
@Entity(tableName = "day_coverage")
data class DayCoverageEntity(
    @androidx.room.PrimaryKey val date: String,
    @ColumnInfo(name = "covered_from_ms") val coveredFromMs: Long,
    @ColumnInfo(name = "covered_to_ms") val coveredToMs: Long,
)

/** The user's choices for one platform. A missing row means "defaults". */
@Entity(tableName = "platform_settings")
data class PlatformSettingsEntity(
    @androidx.room.PrimaryKey @ColumnInfo(name = "platform_id") val platformId: String,
    @ColumnInfo(name = "is_tracked") val isTracked: Boolean,
    /** null = the platform's default (decision D1). */
    @ColumnInfo(name = "estimation_enabled") val estimationEnabled: Boolean?,
    @ColumnInfo(name = "short_form_share") val shortFormShare: Double?,
    @ColumnInfo(name = "seconds_per_reel") val secondsPerReel: Double?,
    @ColumnInfo(name = "updated_at_ms") val updatedAtMs: Long,
)

/** The device time zone from [atMs] onward. */
@Entity(tableName = "zone_changes")
data class ZoneChangeEntity(
    @androidx.room.PrimaryKey @ColumnInfo(name = "at_ms") val atMs: Long,
    @ColumnInfo(name = "zone_id") val zoneId: String,
)

/** Single row (id = 0): how far the usage log has been read. */
@Entity(tableName = "ingest_state")
data class IngestStateEntity(
    @androidx.room.PrimaryKey val id: Int = 0,
    @ColumnInfo(name = "watermark_ms") val watermarkMs: Long,
    @ColumnInfo(name = "last_success_ms") val lastSuccessMs: Long,
    /**
     * The highest revision handed out. Revisions decide upload conflicts, so they must only grow,
     * even if the user turns the clock back. v2.
     */
    @ColumnInfo(name = "last_revision", defaultValue = "0") val lastRevision: Long = 0,
)

/**
 * Reels counted one by one by the accessibility service: one row per local date and platform.
 * Holds only a number, never anything about what was watched.
 */
@Entity(tableName = "reel_counts", primaryKeys = ["date", "platform_id"])
data class ReelCountEntity(
    val date: String,
    @ColumnInfo(name = "platform_id") val platformId: String,
    val count: Int,
)
