package ir.roozban.core.domain

import ir.roozban.core.testing.FakeAlarmScheduler
import ir.roozban.core.testing.FakeReminderRepository
import ir.roozban.core.testing.FakeSettingsRepository
import ir.roozban.core.testing.FakeTaskRepository
import ir.roozban.core.testing.TestClock
import ir.roozban.core.testing.jalali
import ir.roozban.core.model.UserSettings

/** Wires the use cases to fakes, at پنجشنبه ۲ مهر ۱۴۰۵ ۱۰:۰۰. */
class Harness(settings: UserSettings = UserSettings()) {
    val clock = TestClock(jalali("1405-07-02").atTime(10, 0))
    val tasks = FakeTaskRepository()
    val reminders = FakeReminderRepository()
    val settings = FakeSettingsRepository(settings)
    val scheduler = FakeAlarmScheduler()
    val sync = ReminderSync(reminders, tasks, this.settings, scheduler, clock)
    val parser = QuickAddParser()
    val add = AddTaskUseCase(tasks, this.settings, sync, clock)
    val update = UpdateTaskUseCase(tasks, sync, clock)
    val complete = CompleteTaskUseCase(tasks, sync, clock)
    val delete = DeleteTaskUseCase(tasks, sync)

    suspend fun quickAdd(text: String) = add(parser.parse(text, clock.now, settings.current()))!!
}
