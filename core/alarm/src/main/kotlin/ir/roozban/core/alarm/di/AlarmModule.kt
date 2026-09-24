package ir.roozban.core.alarm.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import ir.roozban.core.alarm.AndroidAlarmScheduler
import ir.roozban.core.alarm.AndroidFocusSystem
import ir.roozban.core.alarm.AndroidRoutineAlarms
import ir.roozban.core.domain.AlarmScheduler
import ir.roozban.core.domain.FocusSystem
import ir.roozban.core.domain.RoutineAlarms

@Module
@InstallIn(SingletonComponent::class)
abstract class AlarmModule {
    @Binds
    abstract fun alarmScheduler(impl: AndroidAlarmScheduler): AlarmScheduler

    @Binds
    abstract fun focusSystem(impl: AndroidFocusSystem): FocusSystem

    @Binds
    abstract fun routineAlarms(impl: AndroidRoutineAlarms): RoutineAlarms
}
