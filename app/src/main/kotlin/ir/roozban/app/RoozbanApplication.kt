package ir.roozban.app

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.glance.appwidget.updateAll
import dagger.hilt.android.HiltAndroidApp
import ir.roozban.app.entry.Shortcuts
import ir.roozban.app.widget.TodayWidget
import ir.roozban.core.alarm.DateNotifier
import ir.roozban.core.alarm.ReminderNotifier
import ir.roozban.core.alarm.ReminderReconciler
import ir.roozban.core.data.RoomTaskRepository
import ir.roozban.core.domain.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class RoozbanApplication : Application() {

    @Inject lateinit var notifier: ReminderNotifier
    @Inject lateinit var reconciler: ReminderReconciler
    @Inject lateinit var tasks: RoomTaskRepository
    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var dateNotifier: DateNotifier

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // The UI is Persian-only: pin the app locale so system components (pickers, dialogs,
        // accessibility services) are Persian and RTL too, whatever the device language.
        if (AppCompatDelegate.getApplicationLocales().isEmpty) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(PERSIAN))
        }
        notifier.createChannels()
        Shortcuts.publish(this)
        appScope.launch {
            // Keep the home-screen widget in step with the task list.
            tasks.observeOpenTasks().drop(1).conflate().collect {
                TodayWidget().updateAll(this@RoozbanApplication)
                delay(WIDGET_UPDATE_THROTTLE_MS)
            }
        }
        appScope.launch {
            // Habit and occasion channels follow the sounds chosen in settings.
            settings.settings.map { it.habitSound to it.eventSound }.distinctUntilChanged().collect { (habit, event) ->
                notifier.applySounds(habit, event)
            }
        }
        appScope.launch {
            // The date notification follows its switch and the Hijri offset.
            settings.settings.map { it.dateNotification to it.hijriOffset }.distinctUntilChanged().drop(1).collect {
                dateNotifier.refresh()
            }
        }
        appScope.launch {
            // Re-arm alarms (e.g. after a force-stop, which clears them) and drop old deleted tasks.
            reconciler.reconcile()
            tasks.purgeDeletedOlderThanDays(DELETED_RETENTION_DAYS)
        }
    }

    private companion object {
        const val PERSIAN = "fa-IR"
        const val DELETED_RETENTION_DAYS = 7L
        const val WIDGET_UPDATE_THROTTLE_MS = 1_000L
    }
}
