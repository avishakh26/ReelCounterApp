package com.reelcounter.core.data.tracking

import androidx.room.withTransaction
import com.reelcounter.core.database.ReelCounterDatabase
import com.reelcounter.core.database.dao.DailyUsageDao
import com.reelcounter.core.database.dao.DayCoverageDao
import com.reelcounter.core.database.dao.IngestStateDao
import com.reelcounter.core.database.dao.PlatformSettingsDao
import com.reelcounter.core.database.dao.ReelCountDao
import com.reelcounter.core.database.dao.UsageSessionDao
import com.reelcounter.core.database.dao.ZoneChangeDao
import com.reelcounter.core.database.model.DailyUsageEntity
import com.reelcounter.core.database.model.DayCoverageEntity
import com.reelcounter.core.database.model.IngestStateEntity
import com.reelcounter.core.database.model.PlatformSettingsEntity
import com.reelcounter.core.database.model.UsageSessionEntity
import com.reelcounter.core.database.model.ZoneChangeEntity
import com.reelcounter.core.domain.tracking.ReelEstimate
import com.reelcounter.core.domain.tracking.UsageSession
import com.reelcounter.core.domain.tracking.ZoneChange
import com.reelcounter.core.model.MetricQuality
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

internal data class DailyRow(
    val date: LocalDate,
    val platformId: String,
    val foregroundMs: Long,
    val sessionCount: Int,
    val quality: MetricQuality,
    val estimate: ReelEstimate?,
    val estimationVersion: Int?,
    val userAdjusted: Boolean,
    val tzId: String,
    val isFinal: Boolean,
    val revision: Long,
    val dirty: Boolean,
) {
    /** Same figures, ignoring bookkeeping; used to avoid rewriting unchanged rows. */
    fun sameFiguresAs(other: DailyRow): Boolean =
        copy(revision = 0, dirty = false, isFinal = false) == other.copy(revision = 0, dirty = false, isFinal = false)
}

internal data class DayCoverage(val date: LocalDate, val fromMs: Long, val toMs: Long)

internal data class PlatformSettings(
    val platformId: String,
    val isTracked: Boolean,
    val estimationEnabled: Boolean?,
    val shortFormShare: Double?,
    val secondsPerReel: Double?,
)

internal data class IngestState(val watermarkMs: Long, val lastSuccessMs: Long, val lastRevision: Long = 0)

/** Identifies one uploaded row version, so only that exact version is marked synced. */
internal data class RowVersion(val date: LocalDate, val platformId: String, val revision: Long)

/** Everything the ingestor and repositories persist, behind an interface so tests run in memory. */
internal interface TrackingLocalStore {
    suspend fun <T> transaction(block: suspend () -> T): T

    suspend fun ingestState(): IngestState?
    suspend fun saveIngestState(state: IngestState)
    fun observeLastSuccess(): Flow<Long?>

    /** A revision greater than every one handed out before, and at least [nowMs]. */
    suspend fun nextRevision(nowMs: Long): Long

    suspend fun dirtyRows(limit: Int): List<DailyRow>
    suspend fun markSynced(versions: List<RowVersion>)
    suspend fun markRejected(version: RowVersion, error: String)

    /** Deletes all tracking data recorded for the previous account. */
    suspend fun clearForNewOwner()

    suspend fun earliestOpenSessionStart(): Long?
    suspend fun replaceSessionsFrom(fromMs: Long, sessions: List<UsageSession>)
    suspend fun sessionsOverlapping(fromMs: Long, toMs: Long): List<UsageSession>
    suspend fun closeOpenSessions()
    suspend fun pruneSessionsEndedBefore(beforeMs: Long)

    suspend fun zoneChanges(): List<ZoneChange>
    suspend fun latestZone(): ZoneChange?
    suspend fun recordZone(change: ZoneChange)

    suspend fun dailyRows(dates: Collection<LocalDate>): List<DailyRow>
    suspend fun nonFinalRows(): List<DailyRow>
    suspend fun saveDailyRows(rows: List<DailyRow>)
    suspend fun finalizeBefore(date: LocalDate, revision: Long)
    fun observeDay(date: LocalDate): Flow<List<DailyRow>>

    suspend fun coverage(dates: Collection<LocalDate>): List<DayCoverage>
    suspend fun saveCoverage(rows: List<DayCoverage>)

    /** Adds one reel counted by the accessibility service. */
    suspend fun addReelView(date: LocalDate, platformId: String)
    /** Counted reels by (date, platform id); dates without any are absent. */
    suspend fun reelCounts(dates: Collection<LocalDate>): Map<Pair<LocalDate, String>, Int>

    suspend fun platformSettings(): List<PlatformSettings>
    suspend fun savePlatformSettings(settings: PlatformSettings, atMs: Long)
    fun observePlatformSettings(): Flow<List<PlatformSettings>>
}

internal class RoomTrackingLocalStore @Inject constructor(
    private val database: ReelCounterDatabase,
    private val sessions: UsageSessionDao,
    private val daily: DailyUsageDao,
    private val coverage: DayCoverageDao,
    private val settings: PlatformSettingsDao,
    private val zones: ZoneChangeDao,
    private val ingest: IngestStateDao,
    private val reelCounts: ReelCountDao,
) : TrackingLocalStore {

    override suspend fun addReelView(date: LocalDate, platformId: String) = reelCounts.increment(date.toString(), platformId)

    override suspend fun reelCounts(dates: Collection<LocalDate>): Map<Pair<LocalDate, String>, Int> =
        if (dates.isEmpty()) {
            emptyMap()
        } else {
            reelCounts.forDates(dates.map(LocalDate::toString)).associate { (LocalDate.parse(it.date) to it.platformId) to it.count }
        }

    override suspend fun <T> transaction(block: suspend () -> T): T = database.withTransaction { block() }

    override suspend fun ingestState() = ingest.get()?.let { IngestState(it.watermarkMs, it.lastSuccessMs, it.lastRevision) }

    override suspend fun saveIngestState(state: IngestState) = ingest.upsert(
        IngestStateEntity(watermarkMs = state.watermarkMs, lastSuccessMs = state.lastSuccessMs, lastRevision = state.lastRevision),
    )

    override fun observeLastSuccess(): Flow<Long?> = ingest.observeLastSuccess()

    override suspend fun nextRevision(nowMs: Long): Long = database.withTransaction {
        val current = ingest.get()
        val revision = maxOf(nowMs, (current?.lastRevision ?: 0L) + 1)
        ingest.upsert((current ?: IngestStateEntity(watermarkMs = 0, lastSuccessMs = 0)).copy(lastRevision = revision))
        revision
    }

    override suspend fun dirtyRows(limit: Int) = daily.dirty(limit).map { it.toRow() }

    override suspend fun markSynced(versions: List<RowVersion>) = database.withTransaction {
        versions.forEach { daily.markSynced(it.date.toString(), it.platformId, it.revision) }
    }

    override suspend fun markRejected(version: RowVersion, error: String) =
        daily.markRejected(version.date.toString(), version.platformId, version.revision, error)

    override suspend fun clearForNewOwner() = database.withTransaction {
        sessions.deleteAll()
        daily.deleteAll()
        coverage.deleteAll()
        settings.deleteAll()
        ingest.deleteAll()
        reelCounts.deleteAll()
    }

    override suspend fun earliestOpenSessionStart(): Long? = sessions.earliestOpenStart()

    override suspend fun replaceSessionsFrom(fromMs: Long, sessions: List<UsageSession>) {
        this.sessions.deleteStartingFrom(fromMs)
        this.sessions.upsert(sessions.map { UsageSessionEntity(it.packageName, it.startMs, it.endMs, it.isOpen) })
    }

    override suspend fun sessionsOverlapping(fromMs: Long, toMs: Long) =
        sessions.overlapping(fromMs, toMs).map { UsageSession(it.packageName, it.startMs, it.endMs, it.isOpen) }

    override suspend fun closeOpenSessions() = sessions.closeOpen()

    override suspend fun pruneSessionsEndedBefore(beforeMs: Long) {
        sessions.deleteEndedBefore(beforeMs)
        zones.pruneBefore(beforeMs)
    }

    override suspend fun zoneChanges() = zones.all().map { ZoneChange(it.atMs, ZoneId.of(it.zoneId)) }

    override suspend fun latestZone() = zones.latest()?.let { ZoneChange(it.atMs, ZoneId.of(it.zoneId)) }

    override suspend fun recordZone(change: ZoneChange) = zones.insert(ZoneChangeEntity(change.atMs, change.zoneId.id))

    override suspend fun dailyRows(dates: Collection<LocalDate>) =
        if (dates.isEmpty()) emptyList() else daily.forDates(dates.map(LocalDate::toString)).map { it.toRow() }

    override suspend fun nonFinalRows() = daily.nonFinal().map { it.toRow() }

    override suspend fun saveDailyRows(rows: List<DailyRow>) {
        if (rows.isNotEmpty()) daily.upsert(rows.map { it.toEntity() })
    }

    override suspend fun finalizeBefore(date: LocalDate, revision: Long) = daily.finalizeBefore(date.toString(), revision)

    override fun observeDay(date: LocalDate): Flow<List<DailyRow>> = daily.observeDay(date.toString()).map { rows -> rows.map { it.toRow() } }

    override suspend fun coverage(dates: Collection<LocalDate>) =
        if (dates.isEmpty()) emptyList() else coverage.forDates(dates.map(LocalDate::toString)).map { it.toCoverage() }

    override suspend fun saveCoverage(rows: List<DayCoverage>) {
        if (rows.isNotEmpty()) coverage.upsert(rows.map { DayCoverageEntity(it.date.toString(), it.fromMs, it.toMs) })
    }

    override suspend fun platformSettings() = settings.all().map { it.toSettings() }

    override suspend fun savePlatformSettings(settings: PlatformSettings, atMs: Long) = this.settings.upsert(
        PlatformSettingsEntity(
            platformId = settings.platformId,
            isTracked = settings.isTracked,
            estimationEnabled = settings.estimationEnabled,
            shortFormShare = settings.shortFormShare,
            secondsPerReel = settings.secondsPerReel,
            updatedAtMs = atMs,
        ),
    )

    override fun observePlatformSettings(): Flow<List<PlatformSettings>> = settings.observeAll().map { rows -> rows.map { it.toSettings() } }
}

private fun DailyUsageEntity.toRow(): DailyRow {
    val count = estimatedReels
    val low = estimatedLow
    val high = estimatedHigh
    return toRow(if (count != null && low != null && high != null) ReelEstimate(count, low, high) else null)
}

private fun DailyUsageEntity.toRow(estimate: ReelEstimate?) = DailyRow(
    date = LocalDate.parse(date),
    platformId = platformId,
    foregroundMs = foregroundMs,
    sessionCount = sessionCount,
    quality = MetricQuality.valueOf(quality),
    estimate = estimate,
    estimationVersion = estimationVersion,
    userAdjusted = userAdjusted,
    tzId = tzId,
    isFinal = isFinal,
    revision = revision,
    dirty = dirty,
)

private fun DailyRow.toEntity() = DailyUsageEntity(
    date = date.toString(),
    platformId = platformId,
    foregroundMs = foregroundMs,
    sessionCount = sessionCount,
    quality = quality.name,
    estimatedReels = estimate?.count,
    estimatedLow = estimate?.low,
    estimatedHigh = estimate?.high,
    estimationVersion = estimationVersion,
    userAdjusted = userAdjusted,
    tzId = tzId,
    isFinal = isFinal,
    revision = revision,
    dirty = dirty,
)

private fun DayCoverageEntity.toCoverage() = DayCoverage(LocalDate.parse(date), coveredFromMs, coveredToMs)

private fun PlatformSettingsEntity.toSettings() =
    PlatformSettings(platformId, isTracked, estimationEnabled, shortFormShare, secondsPerReel)
