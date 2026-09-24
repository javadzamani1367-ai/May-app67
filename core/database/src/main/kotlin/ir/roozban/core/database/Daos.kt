package ir.roozban.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    /** Open top-level tasks (subtasks are shown inside their parent). */
    @Transaction
    @Query(
        """
        SELECT * FROM task WHERE completed_at IS NULL AND deleted_at IS NULL AND parent_id IS NULL
        ORDER BY due_date IS NULL, due_date, due_minute IS NULL, due_minute, created_at
        """,
    )
    fun observeOpen(): Flow<List<TaskWithLabels>>

    @Transaction
    @Query(
        """
        SELECT * FROM task WHERE completed_at >= :since AND deleted_at IS NULL AND parent_id IS NULL
        ORDER BY completed_at DESC
        """,
    )
    fun observeCompletedSince(since: Long): Flow<List<TaskWithLabels>>

    @Transaction
    @Query("SELECT * FROM task WHERE parent_id = :parentId AND deleted_at IS NULL ORDER BY created_at")
    fun observeSubtasks(parentId: String): Flow<List<TaskWithLabels>>

    @Transaction
    @Query(
        """
        SELECT * FROM task WHERE project_id = :projectId AND completed_at IS NULL AND deleted_at IS NULL
        AND parent_id IS NULL
        ORDER BY due_date IS NULL, due_date, due_minute IS NULL, due_minute, created_at
        """,
    )
    fun observeProjectTasks(projectId: String): Flow<List<TaskWithLabels>>

    @Query(
        """
        SELECT parent_id, COUNT(*) AS total, SUM(completed_at IS NOT NULL) AS done FROM task
        WHERE parent_id IS NOT NULL AND deleted_at IS NULL GROUP BY parent_id
        """,
    )
    fun observeSubtaskProgress(): Flow<List<SubtaskProgressRow>>

    @Transaction
    @Query("SELECT * FROM task WHERE id = :id AND deleted_at IS NULL")
    fun observe(id: String): Flow<TaskWithLabels?>

    @Transaction
    @Query("SELECT * FROM task WHERE id = :id AND deleted_at IS NULL")
    suspend fun get(id: String): TaskWithLabels?

    @Upsert
    suspend fun upsertEntity(task: TaskEntity)

    @Query("DELETE FROM task_label WHERE task_id = :taskId")
    suspend fun clearLabels(taskId: String)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertLabels(links: List<TaskLabelEntity>)

    /** Writes the task and replaces its labels. */
    @Transaction
    suspend fun upsert(task: TaskEntity, labelIds: Collection<String> = emptyList()) {
        upsertEntity(task)
        clearLabels(task.id)
        if (labelIds.isNotEmpty()) insertLabels(labelIds.map { TaskLabelEntity(task.id, it) })
    }

    @Query("UPDATE task SET deleted_at = :at WHERE id = :id")
    suspend fun softDelete(id: String, at: Long)

    @Query("UPDATE task SET deleted_at = NULL WHERE id = :id")
    suspend fun restore(id: String)

    @Transaction
    @Query("SELECT * FROM task WHERE completed_at IS NULL AND deleted_at IS NULL AND due_date IS NOT NULL")
    suspend fun openWithDue(): List<TaskWithLabels>

    @Query("DELETE FROM task WHERE parent_id IN (SELECT id FROM task WHERE deleted_at IS NOT NULL AND deleted_at < :before)")
    suspend fun purgeOrphanedSubtasks(before: Long): Int

    @Query("DELETE FROM task WHERE deleted_at IS NOT NULL AND deleted_at < :before")
    suspend fun purgeDeletedTasks(before: Long): Int

    /** Permanently removes tasks (and their subtasks) deleted before [before]: the undo window has passed. */
    @Transaction
    suspend fun purgeDeleted(before: Long): Int = purgeOrphanedSubtasks(before) + purgeDeletedTasks(before)

    @Query("UPDATE task SET project_id = NULL WHERE project_id = :projectId")
    suspend fun detachProject(projectId: String)

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

@Dao
interface ProjectDao {
    @Query("SELECT * FROM project ORDER BY archived, sort_order, name")
    fun observeAll(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM project")
    suspend fun all(): List<ProjectEntity>

    @Query("SELECT * FROM project WHERE id = :id")
    suspend fun get(id: String): ProjectEntity?

    @Upsert
    suspend fun upsert(project: ProjectEntity)

    @Query("DELETE FROM project WHERE id = :id")
    suspend fun delete(id: String)

    @Query(
        """
        SELECT project_id, COUNT(*) AS count FROM task
        WHERE project_id IS NOT NULL AND completed_at IS NULL AND deleted_at IS NULL AND parent_id IS NULL
        GROUP BY project_id
        """,
    )
    fun observeOpenCounts(): Flow<List<ProjectCountRow>>
}

@Dao
interface LabelDao {
    @Query("SELECT * FROM label ORDER BY name")
    fun observeAll(): Flow<List<LabelEntity>>

    @Query("SELECT * FROM label")
    suspend fun all(): List<LabelEntity>

    @Upsert
    suspend fun upsert(label: LabelEntity)

    @Query("DELETE FROM label WHERE id = :id")
    suspend fun delete(id: String)
}

/** Whole-database export and import for backups. */
@Dao
interface BackupDao {
    @Query("SELECT * FROM task WHERE deleted_at IS NULL")
    suspend fun tasks(): List<TaskEntity>

    @Query("SELECT * FROM project")
    suspend fun projects(): List<ProjectEntity>

    @Query("SELECT * FROM label")
    suspend fun labels(): List<LabelEntity>

    @Query("SELECT * FROM task_label")
    suspend fun taskLabels(): List<TaskLabelEntity>

    @Query("SELECT * FROM task_completion")
    suspend fun completions(): List<CompletionEntity>

    @Query("DELETE FROM task")
    suspend fun clearTasks()

    @Query("DELETE FROM project")
    suspend fun clearProjects()

    @Query("DELETE FROM label")
    suspend fun clearLabels()

    @Query("DELETE FROM reminder")
    suspend fun clearReminders()

    @Insert
    suspend fun insertTasks(tasks: List<TaskEntity>)

    @Insert
    suspend fun insertProjects(projects: List<ProjectEntity>)

    @Insert
    suspend fun insertLabels(labels: List<LabelEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTaskLabels(links: List<TaskLabelEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCompletions(completions: List<CompletionEntity>)

    /** Replaces everything atomically; reminders are re-planned by the caller afterwards. */
    @Transaction
    suspend fun replaceAll(
        tasks: List<TaskEntity>,
        projects: List<ProjectEntity>,
        labels: List<LabelEntity>,
        taskLabels: List<TaskLabelEntity>,
        completions: List<CompletionEntity>,
    ) {
        clearReminders()
        clearTasks() // cascades to task_label and task_completion
        clearProjects()
        clearLabels()
        insertProjects(projects)
        insertLabels(labels)
        insertTasks(tasks)
        val taskIds = tasks.mapTo(HashSet()) { it.id }
        val labelIds = labels.mapTo(HashSet()) { it.id }
        insertTaskLabels(taskLabels.filter { it.taskId in taskIds && it.labelId in labelIds })
        insertCompletions(completions.filter { it.taskId in taskIds })
    }
}
