package ir.roozban.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Query(
        """
        SELECT * FROM task WHERE completed_at IS NULL AND deleted_at IS NULL
        ORDER BY due_date IS NULL, due_date, due_minute IS NULL, due_minute, created_at
        """,
    )
    fun observeOpen(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM task WHERE completed_at >= :since AND deleted_at IS NULL ORDER BY completed_at DESC")
    fun observeCompletedSince(since: Long): Flow<List<TaskEntity>>

    @Query("SELECT * FROM task WHERE id = :id AND deleted_at IS NULL")
    fun observe(id: String): Flow<TaskEntity?>

    @Query("SELECT * FROM task WHERE id = :id AND deleted_at IS NULL")
    suspend fun get(id: String): TaskEntity?

    @Upsert
    suspend fun upsert(task: TaskEntity)

    @Query("UPDATE task SET deleted_at = :at WHERE id = :id")
    suspend fun softDelete(id: String, at: Long)

    @Query("UPDATE task SET deleted_at = NULL WHERE id = :id")
    suspend fun restore(id: String)

    @Query("SELECT * FROM task WHERE completed_at IS NULL AND deleted_at IS NULL AND due_date IS NOT NULL")
    suspend fun openWithDue(): List<TaskEntity>

    /** Permanently removes tasks deleted before [before] (undo window has passed). */
    @Query("DELETE FROM task WHERE deleted_at IS NOT NULL AND deleted_at < :before")
    suspend fun purgeDeleted(before: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCompletion(completion: CompletionEntity)

    @Query("DELETE FROM task_completion WHERE task_id = :taskId AND occurrence = :occurrence")
    suspend fun deleteCompletion(taskId: String, occurrence: Long)

    @Query("SELECT * FROM task_completion WHERE task_id = :taskId ORDER BY occurrence")
    suspend fun completions(taskId: String): List<CompletionEntity>
}

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminder WHERE task_id = :taskId")
    suspend fun get(taskId: String): ReminderEntity?

    @Upsert
    suspend fun upsert(reminder: ReminderEntity)

    @Query("DELETE FROM reminder WHERE task_id = :taskId")
    suspend fun delete(taskId: String)

    @Query("SELECT * FROM reminder WHERE state = 'PENDING' AND trigger_at <= :until ORDER BY trigger_at")
    suspend fun pendingUntil(until: Long): List<ReminderEntity>

    @Query("UPDATE reminder SET state = 'FIRED' WHERE task_id = :taskId")
    suspend fun markFired(taskId: String)
}
