package ir.roozban.core.data.di

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import ir.roozban.core.data.FileLearningStore
import ir.roozban.core.data.RoomBackupService
import ir.roozban.core.data.RoomEventRepository
import ir.roozban.core.data.RoomFocusRepository
import ir.roozban.core.data.RoomHabitRepository
import ir.roozban.core.data.RoomLabelRepository
import ir.roozban.core.data.RoomMemoryRepository
import ir.roozban.core.data.RoomProjectRepository
import ir.roozban.core.data.RoomReminderRepository
import ir.roozban.core.data.RoomTaskRepository
import ir.roozban.core.database.BackupDao
import ir.roozban.core.database.EventDao
import ir.roozban.core.database.FocusDao
import ir.roozban.core.database.HabitDao
import ir.roozban.core.database.LabelDao
import ir.roozban.core.database.MemoryDao
import ir.roozban.core.database.ProjectDao
import ir.roozban.core.database.ReminderDao
import ir.roozban.core.database.RoozbanDatabase
import ir.roozban.core.database.TaskDao
import ir.roozban.core.datastore.DataStoreFocusStateStore
import ir.roozban.core.datastore.DataStoreSettingsRepository
import ir.roozban.core.domain.BackupService
import ir.roozban.core.domain.Entitlements
import ir.roozban.core.domain.EventRepository
import ir.roozban.core.domain.FocusRepository
import ir.roozban.core.domain.FocusStateStore
import ir.roozban.core.domain.HabitRepository
import ir.roozban.core.domain.LabelRepository
import ir.roozban.core.domain.LearningStore
import ir.roozban.core.domain.MemoryRepository
import ir.roozban.core.domain.ProjectRepository
import ir.roozban.core.domain.ProvisionalEntitlements
import ir.roozban.core.domain.ReminderRepository
import ir.roozban.core.domain.SettingsRepository
import ir.roozban.core.domain.TaskRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {
    @Binds
    abstract fun taskRepository(impl: RoomTaskRepository): TaskRepository

    @Binds
    abstract fun reminderRepository(impl: RoomReminderRepository): ReminderRepository

    @Binds
    abstract fun projectRepository(impl: RoomProjectRepository): ProjectRepository

    @Binds
    abstract fun labelRepository(impl: RoomLabelRepository): LabelRepository

    @Binds
    abstract fun backupService(impl: RoomBackupService): BackupService

    @Binds
    abstract fun focusRepository(impl: RoomFocusRepository): FocusRepository

    @Binds
    abstract fun habitRepository(impl: RoomHabitRepository): HabitRepository

    @Binds
    abstract fun eventRepository(impl: RoomEventRepository): EventRepository

    @Binds
    abstract fun memoryRepository(impl: RoomMemoryRepository): MemoryRepository

    @Binds
    abstract fun learningStore(impl: FileLearningStore): LearningStore

    @Binds
    abstract fun entitlements(impl: ProvisionalEntitlements): Entitlements

    companion object {
        @Provides
        @Singleton
        fun database(@ApplicationContext context: Context): RoozbanDatabase =
            Room.databaseBuilder(context, RoozbanDatabase::class.java, RoozbanDatabase.NAME).build()

        @Provides
        fun taskDao(db: RoozbanDatabase): TaskDao = db.taskDao()

        @Provides
        fun reminderDao(db: RoozbanDatabase): ReminderDao = db.reminderDao()

        @Provides
        fun projectDao(db: RoozbanDatabase): ProjectDao = db.projectDao()

        @Provides
        fun labelDao(db: RoozbanDatabase): LabelDao = db.labelDao()

        @Provides
        fun backupDao(db: RoozbanDatabase): BackupDao = db.backupDao()

        @Provides
        fun focusDao(db: RoozbanDatabase): FocusDao = db.focusDao()

        @Provides
        fun habitDao(db: RoozbanDatabase): HabitDao = db.habitDao()

        @Provides
        fun eventDao(db: RoozbanDatabase): EventDao = db.eventDao()

        @Provides
        fun memoryDao(db: RoozbanDatabase): MemoryDao = db.memoryDao()

        @Provides
        @Singleton
        fun focusStateStore(@ApplicationContext context: Context): FocusStateStore =
            DataStoreFocusStateStore(
                PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("focus_state") },
            )

        @Provides
        @Singleton
        fun settingsRepository(@ApplicationContext context: Context): SettingsRepository =
            DataStoreSettingsRepository(
                PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("settings") },
            )
    }
}
