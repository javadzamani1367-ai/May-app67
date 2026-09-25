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
        FocusSessionEntity::class,
        TimeEntryEntity::class,
        HabitEntity::class,
        HabitLogEntity::class,
        PersonalEventEntity::class,
    ],
    version = 4,
    exportSchema = true,
    autoMigrations = [
        // v2: projects, labels, subtasks.
        AutoMigration(from = 1, to = 2),
        // v3: focus sessions, time entries, habits.
        AutoMigration(from = 2, to = 3),
        // v4: personal occasions (birthdays, anniversaries).
        AutoMigration(from = 3, to = 4),
    ],
)
abstract class RoozbanDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun reminderDao(): ReminderDao
    abstract fun projectDao(): ProjectDao
    abstract fun labelDao(): LabelDao
    abstract fun backupDao(): BackupDao
    abstract fun focusDao(): FocusDao
    abstract fun habitDao(): HabitDao
    abstract fun eventDao(): EventDao

    companion object {
        const val NAME = "roozban.db"
    }
}
