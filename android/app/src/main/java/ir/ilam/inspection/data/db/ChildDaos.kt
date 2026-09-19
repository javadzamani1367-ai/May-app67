package ir.ilam.inspection.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DeviceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(device: DeviceEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(devices: List<DeviceEntity>)

    @Update
    suspend fun update(device: DeviceEntity)

    @Delete
    suspend fun delete(device: DeviceEntity)

    @Query("SELECT * FROM devices WHERE report_id = :reportId ORDER BY row_number ASC")
    fun observeFor(reportId: String): Flow<List<DeviceEntity>>

    @Query("SELECT * FROM devices WHERE report_id = :reportId ORDER BY row_number ASC")
    suspend fun listFor(reportId: String): List<DeviceEntity>

    @Query("SELECT IFNULL(MAX(row_number), 0) FROM devices WHERE report_id = :reportId")
    suspend fun maxRow(reportId: String): Int

    @Query("SELECT COUNT(*) FROM devices WHERE report_id = :reportId AND serial_number = :serial")
    suspend fun countSerial(reportId: String, serial: String): Int

    @Query("SELECT IFNULL(SUM(power_watt), 0) FROM devices WHERE report_id = :reportId")
    suspend fun totalPower(reportId: String): Double

    /** Replacing a pulled case: its children go together, not one by one. */
    @Query("DELETE FROM devices WHERE report_id = :reportId")
    suspend fun deleteFor(reportId: String)
}

@Dao
interface AttendeeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(attendee: AttendeeEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(attendees: List<AttendeeEntity>)

    @Delete
    suspend fun delete(attendee: AttendeeEntity)

    @Query("SELECT * FROM attendees WHERE report_id = :reportId")
    fun observeFor(reportId: String): Flow<List<AttendeeEntity>>

    @Query("SELECT * FROM attendees WHERE report_id = :reportId")
    suspend fun listFor(reportId: String): List<AttendeeEntity>

    @Query("DELETE FROM attendees WHERE report_id = :reportId")
    suspend fun deleteFor(reportId: String)
}

@Dao
interface MediaDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(media: MediaEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(media: List<MediaEntity>)

    @Update
    suspend fun update(media: MediaEntity)

    @Delete
    suspend fun delete(media: MediaEntity)

    @Query("SELECT * FROM media WHERE report_id = :reportId ORDER BY captured_at ASC")
    fun observeFor(reportId: String): Flow<List<MediaEntity>>

    @Query("SELECT * FROM media WHERE report_id = :reportId ORDER BY captured_at ASC")
    suspend fun listFor(reportId: String): List<MediaEntity>

    @Query("SELECT * FROM media WHERE id = :id")
    suspend fun byId(id: String): MediaEntity?

    @Query("SELECT COUNT(*) FROM media WHERE report_id = :reportId AND type = 0")
    suspend fun photoCount(reportId: String): Int

    @Query("DELETE FROM media WHERE report_id = :reportId")
    suspend fun deleteFor(reportId: String)
}

@Dao
interface AttachmentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(attachment: AttachmentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(attachments: List<AttachmentEntity>)

    @Delete
    suspend fun delete(attachment: AttachmentEntity)

    @Query("SELECT * FROM attachments WHERE report_id = :reportId ORDER BY added_at ASC")
    fun observeFor(reportId: String): Flow<List<AttachmentEntity>>

    @Query("SELECT * FROM attachments WHERE report_id = :reportId ORDER BY added_at ASC")
    suspend fun listFor(reportId: String): List<AttachmentEntity>

    @Query("SELECT * FROM attachments WHERE id = :id")
    suspend fun byId(id: String): AttachmentEntity?

    @Query("DELETE FROM attachments WHERE report_id = :reportId")
    suspend fun deleteFor(reportId: String)
}

@Dao
interface DispatchDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(dispatch: DispatchEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(dispatches: List<DispatchEntity>)

    @Query("SELECT * FROM dispatches WHERE report_id = :reportId ORDER BY dispatched_at DESC")
    fun observeFor(reportId: String): Flow<List<DispatchEntity>>

    @Query("SELECT * FROM dispatches WHERE report_id = :reportId ORDER BY dispatched_at DESC")
    suspend fun listFor(reportId: String): List<DispatchEntity>

    @Query("SELECT * FROM dispatches WHERE id = :id")
    suspend fun byId(id: String): DispatchEntity?

    /**
     * Unit performance from this phone's own rows. The server has the whole
     * picture; this is what a manager can still produce with no connection.
     * `now` is passed in rather than read inside the query so the same instant
     * decides every row — a query that called a clock could put two rows on
     * opposite sides of a deadline.
     */
    @Query(
        "SELECT unit AS unit, " +
            "COUNT(*) AS sent, " +
            "SUM(status >= 1) AS seen, " +
            "SUM(status = 2) AS answered, " +
            "SUM(deadline_at IS NOT NULL AND status <> 2 AND deadline_at < :now) AS overdue, " +
            "SUM(deadline_at IS NOT NULL AND status = 2 AND answered_at <= deadline_at) AS onTime, " +
            "AVG(CASE WHEN answered_at IS NOT NULL THEN answered_at - dispatched_at END) AS avgMillis " +
            "FROM dispatches WHERE dispatched_at BETWEEN :from AND :to GROUP BY unit"
    )
    suspend fun performance(from: Long, to: Long, now: Long): List<UnitPerformanceRow>
}

/** The raw aggregate, before it is turned into a [ir.ilam.inspection.data.model.UnitPerformance]. */
data class UnitPerformanceRow(
    val unit: Int,
    val sent: Int,
    val seen: Int,
    val answered: Int,
    val overdue: Int,
    val onTime: Int,
    val avgMillis: Double?
)

@Dao
interface SettingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(setting: SettingEntity)

    @Query("SELECT value FROM settings WHERE key = :key")
    suspend fun value(key: String): String?

    @Query("SELECT * FROM settings")
    fun observeAll(): Flow<List<SettingEntity>>

    @Query("DELETE FROM settings WHERE key = :key")
    suspend fun remove(key: String)
}
