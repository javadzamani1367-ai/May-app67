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
        MemoryFactEntity::class,
    ],
    version = 5,
    exportSchema = true,
    autoMigrations = [
        // v2: projects, labels, subtasks.
        AutoMigration(from = 1, to = 2),
        // v3: focus sessions, time entries, habits.
        AutoMigration(from = 2, to = 3),
        // v4: personal occasions (birthdays, anniversaries).
        AutoMigration(from = 3, to = 4),
        // v5: personal memory (what Roozban learned about the user).
        AutoMigration(from = 4, to = 5),
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
    abstract fun memoryDao(): MemoryDao

    companion object {
        const val NAME = "roozban.db"
    }
}
