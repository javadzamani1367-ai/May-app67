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
}

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
