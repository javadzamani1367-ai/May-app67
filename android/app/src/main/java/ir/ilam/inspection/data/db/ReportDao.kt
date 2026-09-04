package ir.ilam.inspection.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ReportDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(report: ReportEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(reports: List<ReportEntity>)

    @Update
    suspend fun update(report: ReportEntity)

    @Query("DELETE FROM reports WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT * FROM reports WHERE id = :id")
    suspend fun byId(id: String): ReportEntity?

    @Query("SELECT * FROM reports WHERE id = :id")
    fun observeById(id: String): Flow<ReportEntity?>

    /** Oldest first, so cases that have been waiting longest surface at the top. */
    @Query("SELECT * FROM reports WHERE status = :status ORDER BY report_date ASC, created_at ASC")
    fun observeByStatusOldestFirst(status: Int): Flow<List<ReportEntity>>

    @Query("SELECT * FROM reports WHERE status = :status ORDER BY visit_date DESC, updated_at DESC")
    fun observeByStatusNewestFirst(status: Int): Flow<List<ReportEntity>>

    @Query(
        """
        SELECT * FROM reports
        WHERE (:status IS NULL OR status = :status)
          AND (:query = '' OR tracking_code LIKE '%' || :query || '%'
               OR temp_code LIKE '%' || :query || '%'
               OR address LIKE '%' || :query || '%'
               OR subscription_number LIKE '%' || :query || '%'
               OR file_number LIKE '%' || :query || '%'
               OR owner_name LIKE '%' || :query || '%')
        ORDER BY updated_at DESC
        """
    )
    fun search(status: Int?, query: String): Flow<List<ReportEntity>>

    @Query(
        """
        SELECT * FROM reports
        WHERE (:status IS NULL OR status = :status)
          AND (:county IS NULL OR county = :county)
          AND (:reportType IS NULL OR report_type = :reportType)
          AND (:expert IS NULL OR expert_code = :expert)
          AND (:fromDate IS NULL OR report_date >= :fromDate)
          AND (:toDate IS NULL OR report_date <= :toDate)
        ORDER BY report_date DESC
        """
    )
    suspend fun filter(
        status: Int?,
        county: String?,
        reportType: Int?,
        expert: String?,
        fromDate: Long?,
        toDate: Long?
    ): List<ReportEntity>

    @Query("SELECT COUNT(*) FROM reports WHERE status = :status")
    fun countByStatus(status: Int): Flow<Int>

    @Query("SELECT COUNT(*) FROM reports")
    fun countAll(): Flow<Int>

    @Query("SELECT report_type AS bucket, COUNT(*) AS total FROM reports GROUP BY report_type")
    fun countByType(): Flow<List<Bucket>>

    @Query("SELECT IFNULL(county, '') AS bucket, COUNT(*) AS total FROM reports GROUP BY county")
    fun countByCounty(): Flow<List<TextBucket>>

    @Query(
        """
        SELECT IFNULL(SUM(d.power_watt), 0) FROM devices d
        INNER JOIN reports r ON r.id = d.report_id
        """
    )
    fun totalDiscoveredPower(): Flow<Double>

    @Query("SELECT COUNT(*) FROM reports WHERE tracking_code = :code")
    suspend fun countByTrackingCode(code: String): Int

    /** Daily counter behind temporary codes such as `M-01-050614-T0003`. */
    @Query("SELECT COUNT(*) FROM reports WHERE temp_code IS NOT NULL AND created_at BETWEEN :from AND :to")
    suspend fun countTempCodesInRange(from: Long, to: Long): Int

    /** Incremental sync: only what changed since the last acknowledged push. */
    @Query("SELECT * FROM reports WHERE synced_at IS NULL OR updated_at > synced_at ORDER BY updated_at ASC")
    suspend fun pendingSync(): List<ReportEntity>

    @Query(
        """
        SELECT id, updated_at FROM reports
        WHERE updated_at > :since ORDER BY updated_at ASC
        """
    )
    suspend fun manifestSince(since: Long): List<ManifestRow>

    @Query("SELECT COUNT(*) FROM reports WHERE synced_at IS NULL OR updated_at > synced_at")
    fun pendingSyncCount(): Flow<Int>

    @Query("UPDATE reports SET synced_at = :timestamp WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<String>, timestamp: Long)
}

data class Bucket(val bucket: Int, val total: Int)

data class TextBucket(val bucket: String, val total: Int)

data class ManifestRow(
    val id: String,
    @androidx.room.ColumnInfo(name = "updated_at") val updatedAt: Long
)
