package ir.roozban.app

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.glance.appwidget.updateAll
import dagger.hilt.android.HiltAndroidApp
import ir.roozban.app.entry.Shortcuts
import ir.roozban.app.widget.TodayWidget
import ir.roozban.core.alarm.ReminderNotifier
import ir.roozban.core.alarm.ReminderReconciler
import ir.roozban.core.data.RoomTaskRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class RoozbanApplication : Application() {

    @Inject lateinit var notifier: ReminderNotifier
    @Inject lateinit var reconciler: ReminderReconciler
    @Inject lateinit var tasks: RoomTaskRepository

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
