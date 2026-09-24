package ir.roozban.core.alarm.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import ir.roozban.core.alarm.AndroidAlarmScheduler
import ir.roozban.core.domain.AlarmScheduler

@Module
@InstallIn(SingletonComponent::class)
abstract class AlarmModule {
    @Binds
    abstract fun alarmScheduler(impl: AndroidAlarmScheduler): AlarmScheduler
}
