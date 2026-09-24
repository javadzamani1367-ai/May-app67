package ir.roozban.core.database

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        TaskEntity::class,
        ReminderEntity::class,
        CompletionEntity::class,
        ProjectEntity::class,
        LabelEntity::class,
        TaskLabelEntity::class,
    ],
    version = 2,
    exportSchema = true,
    autoMigrations = [
        // v2: projects, labels, subtasks.
        AutoMigration(from = 1, to = 2),
    ],
)
abstract class RoozbanDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun reminderDao(): ReminderDao
    abstract fun projectDao(): ProjectDao
    abstract fun labelDao(): LabelDao
    abstract fun backupDao(): BackupDao

    companion object {
        const val NAME = "roozban.db"
    }
}
