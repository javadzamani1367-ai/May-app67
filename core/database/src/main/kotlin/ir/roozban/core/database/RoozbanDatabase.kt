package ir.roozban.core.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [TaskEntity::class, ReminderEntity::class, CompletionEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class RoozbanDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun reminderDao(): ReminderDao

    companion object {
        const val NAME = "roozban.db"
    }
}
