package com.reelcounter.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.reelcounter.core.database.model.DailyUsageEntity
import com.reelcounter.core.database.model.DayCoverageEntity
import com.reelcounter.core.database.model.IngestStateEntity
import com.reelcounter.core.database.model.PlatformSettingsEntity
import com.reelcounter.core.database.model.ReelCountEntity
import com.reelcounter.core.database.model.UsageSessionEntity
import com.reelcounter.core.database.model.ZoneChangeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UsageSessionDao {
    @Query("SELECT * FROM usage_sessions WHERE end_ms > :fromMs AND start_ms < :toMs ORDER BY start_ms")
    suspend fun overlapping(fromMs: Long, toMs: Long): List<UsageSessionEntity>

    @Query("SELECT MIN(start_ms) FROM usage_sessions WHERE is_open = 1")
    suspend fun earliestOpenStart(): Long?

    /** Sessions starting at or after [fromMs] are re-derived on each read, so they're replaced. */
    @Query("DELETE FROM usage_sessions WHERE start_ms >= :fromMs")
    suspend fun deleteStartingFrom(fromMs: Long)

    @Upsert
    suspend fun upsert(sessions: List<UsageSessionEntity>)

    @Query("DELETE FROM usage_sessions WHERE end_ms < :beforeMs")
    suspend fun deleteEndedBefore(beforeMs: Long)

    /** On pause: provisional sessions end where they are and are never extended into the pause. */
    @Query("UPDATE usage_sessions SET is_open = 0 WHERE is_open = 1")
    suspend fun closeOpen()

    @Query("DELETE FROM usage_sessions")
    suspend fun deleteAll()
}

@Dao
interface DailyUsageDao {
    @Query("SELECT * FROM daily_usage WHERE date = :date")
    fun observeDay(date: String): Flow<List<DailyUsageEntity>>

    @Query("SELECT * FROM daily_usage WHERE date IN (:dates)")
    suspend fun forDates(dates: List<String>): List<DailyUsageEntity>

    @Query("SELECT * FROM daily_usage WHERE is_final = 0")
    suspend fun nonFinal(): List<DailyUsageEntity>

    @Upsert
    suspend fun upsert(rows: List<DailyUsageEntity>)

    /** Freezes days before [date]; their usage events may already be gone, so they're never recomputed. */
    @Query("UPDATE daily_usage SET is_final = 1, dirty = 1, revision = :revision WHERE date < :date AND is_final = 0")
    suspend fun finalizeBefore(date: String, revision: Long)

    @Query("SELECT * FROM daily_usage WHERE dirty = 1 AND sync_error IS NULL ORDER BY date, platform_id LIMIT :limit")
    suspend fun dirty(limit: Int): List<DailyUsageEntity>

    /** Only clears the flag if the row wasn't changed again while it was being uploaded. */
    @Query("UPDATE daily_usage SET dirty = 0 WHERE date = :date AND platform_id = :platformId AND revision = :revision")
    suspend fun markSynced(date: String, platformId: String, revision: Long)

    @Query("UPDATE daily_usage SET sync_error = :error WHERE date = :date AND platform_id = :platformId AND revision = :revision")
    suspend fun markRejected(date: String, platformId: String, revision: Long, error: String)

    @Query("DELETE FROM daily_usage")
    suspend fun deleteAll()
}

@Dao
interface DayCoverageDao {
    @Query("SELECT * FROM day_coverage WHERE date = :date")
    fun observe(date: String): Flow<DayCoverageEntity?>

    @Query("SELECT * FROM day_coverage WHERE date IN (:dates)")
    suspend fun forDates(dates: List<String>): List<DayCoverageEntity>

    @Upsert
    suspend fun upsert(rows: List<DayCoverageEntity>)

    @Query("DELETE FROM day_coverage")
    suspend fun deleteAll()
}

@Dao
interface PlatformSettingsDao {
    @Query("SELECT * FROM platform_settings")
    fun observeAll(): Flow<List<PlatformSettingsEntity>>

    @Query("SELECT * FROM platform_settings")
    suspend fun all(): List<PlatformSettingsEntity>

    @Query("SELECT * FROM platform_settings WHERE platform_id = :platformId")
    suspend fun get(platformId: String): PlatformSettingsEntity?

    @Upsert
    suspend fun upsert(row: PlatformSettingsEntity)

    @Query("DELETE FROM platform_settings")
    suspend fun deleteAll()
}

@Dao
interface ZoneChangeDao {
    @Query("SELECT * FROM zone_changes ORDER BY at_ms")
    suspend fun all(): List<ZoneChangeEntity>

    @Query("SELECT * FROM zone_changes ORDER BY at_ms DESC LIMIT 1")
    suspend fun latest(): ZoneChangeEntity?

    @Upsert
    suspend fun insert(change: ZoneChangeEntity)

    /** Keeps the latest change before [beforeMs], which still applies to later sessions. */
    @Query("DELETE FROM zone_changes WHERE at_ms < (SELECT MAX(at_ms) FROM zone_changes WHERE at_ms < :beforeMs)")
    suspend fun pruneBefore(beforeMs: Long)
}

@Dao
interface IngestStateDao {
    @Query("SELECT * FROM ingest_state WHERE id = 0")
    suspend fun get(): IngestStateEntity?

    @Query("SELECT last_success_ms FROM ingest_state WHERE id = 0")
    fun observeLastSuccess(): Flow<Long?>

    @Upsert
    suspend fun upsert(state: IngestStateEntity)

    @Query("DELETE FROM ingest_state")
    suspend fun deleteAll()
}

@Dao
interface ReelCountDao {
    @Query("SELECT * FROM reel_counts WHERE date IN (:dates)")
    suspend fun forDates(dates: List<String>): List<ReelCountEntity>

    @Query("UPDATE reel_counts SET count = count + 1 WHERE date = :date AND platform_id = :platformId")
    suspend fun bump(date: String, platformId: String): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(row: ReelCountEntity)

    /** Adds one reel. Update-then-insert, because SQLite before Android 11 has no upsert syntax. */
    @Transaction
    suspend fun increment(date: String, platformId: String) {
        if (bump(date, platformId) == 0) insert(ReelCountEntity(date, platformId, count = 1))
    }

    @Query("DELETE FROM reel_counts")
    suspend fun deleteAll()
}
