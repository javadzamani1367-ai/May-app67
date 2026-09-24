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
import ir.roozban.core.data.RoomReminderRepository
import ir.roozban.core.data.RoomTaskRepository
import ir.roozban.core.database.ReminderDao
import ir.roozban.core.database.RoozbanDatabase
import ir.roozban.core.database.TaskDao
import ir.roozban.core.datastore.DataStoreSettingsRepository
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
        @Singleton
        fun settingsRepository(@ApplicationContext context: Context): SettingsRepository =
            DataStoreSettingsRepository(
                PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("settings") },
            )
    }
}
