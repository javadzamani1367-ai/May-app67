package ir.ilam.inspection.field.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** How many items are in each sync state, for the home screen's queue line. */
data class QueueCounts(val pending: Int, val sent: Int, val failed: Int)

@Dao
interface FieldDao {

    /** Everything that has left draft, newest first: the queue the user watches. */
    @Query("SELECT * FROM field_item WHERE sync_state != 0 ORDER BY updated_at DESC")
    fun observeQueue(): Flow<List<FieldItemEntity>>

    @Query(
        "SELECT " +
            "COALESCE(SUM(CASE WHEN sync_state = 1 THEN 1 ELSE 0 END), 0) AS pending, " +
            "COALESCE(SUM(CASE WHEN sync_state = 2 THEN 1 ELSE 0 END), 0) AS sent, " +
            "COALESCE(SUM(CASE WHEN sync_state = 3 THEN 1 ELSE 0 END), 0) AS failed " +
            "FROM field_item"
    )
    fun observeCounts(): Flow<QueueCounts>

    /** Items waiting to go, oldest first, so the earliest report reaches the office first. */
    @Query("SELECT * FROM field_item WHERE sync_state IN (1, 3) ORDER BY created_at")
    suspend fun unsent(): List<FieldItemEntity>

    @Query("SELECT * FROM field_item WHERE id = :id")
    suspend fun item(id: String): FieldItemEntity?

    @Upsert
    suspend fun saveItem(item: FieldItemEntity)

    @Query("UPDATE field_item SET sync_state = :state, sync_error = :error, sent_at = :sentAt WHERE id = :id")
    suspend fun setSyncState(id: String, state: Int, error: String?, sentAt: Long?)

    @Query("UPDATE field_item SET tracking_code = :code, server_status = :status WHERE id = :id")
    suspend fun setServerState(id: String, code: String?, status: Int?)

    @Query("SELECT * FROM field_file WHERE item_id = :itemId")
    suspend fun files(itemId: String): List<FieldFileEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveFile(file: FieldFileEntity)

    @Query("UPDATE field_file SET uploaded = :uploaded, complete = :complete WHERE id = :id")
    suspend fun setUploaded(id: String, uploaded: Long, complete: Boolean)

    @Query("UPDATE field_file SET asset_type = :assetType, plate = :plate, note = COALESCE(:note, note) WHERE id = :id")
    suspend fun assign(id: String, assetType: Int, plate: String, note: String?)

    @Query("DELETE FROM field_file WHERE id = :id")
    suspend fun deleteFile(id: String)

    @Query("DELETE FROM field_item WHERE id = :id")
    suspend fun deleteItem(id: String)

    /** Drafts, newest first: work the user started and has not finished. */
    @Query("SELECT * FROM field_item WHERE sync_state = 0 ORDER BY updated_at DESC")
    fun observeDrafts(): Flow<List<FieldItemEntity>>

    @Query("SELECT * FROM field_file WHERE item_id = :itemId ORDER BY captured_at")
    fun observeFiles(itemId: String): Flow<List<FieldFileEntity>>

    /** The local copy of a file is gone (purged after sending); the row stays as the record. */
    @Query("UPDATE field_file SET path = NULL WHERE id = :id")
    suspend fun forgetPath(id: String)
}
