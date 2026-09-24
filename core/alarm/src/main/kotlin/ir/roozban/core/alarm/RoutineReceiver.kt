package ir.roozban.core.alarm

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.AndroidEntryPoint
import ir.roozban.core.domain.FocusService
import ir.roozban.core.domain.HabitRepository
import ir.roozban.core.domain.HabitUseCases
import ir.roozban.core.domain.ReviewKind
import ir.roozban.core.domain.RoutineReminders
import ir.roozban.core.model.Habit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/** Focus timer, habit reminder and review nudge alarms, plus their notification actions. */
@AndroidEntryPoint
class RoutineReceiver : BroadcastReceiver() {

    @Inject lateinit var focus: FocusService
    @Inject lateinit var routines: RoutineReminders
    @Inject lateinit var habits: HabitRepository
    @Inject lateinit var habitUseCases: HabitUseCases
    @Inject lateinit var notifier: ReminderNotifier
    @Inject lateinit var clock: Clock

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(EXTRA_ID)
        val pending = goAsync()
        scope.launch {
            try {
                when (intent.action) {
                    ACTION_FOCUS_END -> focus.onAlarm()
                    ACTION_FOCUS_PAUSE -> focus.pause()
                    ACTION_FOCUS_RESUME -> focus.resume()
                    ACTION_FOCUS_STOP -> focus.stop()
                    ACTION_FOCUS_SKIP -> focus.skip()
                    ACTION_FOCUS_NEXT -> focus.startNext()
                    ACTION_HABIT_FIRE -> if (id != null) routines.onHabitAlarm(id)?.let { showHabit(context, it) }
                    ACTION_HABIT_DONE -> if (id != null) {
                        NotificationManagerCompat.from(context).cancel(habitNotificationId(id))
                        habits.get(id)?.let { habit ->
                            val today = LocalDate.now(clock)
                            habitUseCases.setCount(habit, today, habit.targetPerDay)
                        }
                    }
                    ACTION_REVIEW_FIRE -> {
                        val kind = id?.let { runCatching { ReviewKind.valueOf(it) }.getOrNull() }
                        if (kind != null && routines.onReviewAlarm(kind)) showReview(context, kind)
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }

    private fun showHabit(context: Context, habit: Habit) {
        if (!notifier.canPost()) return
        val done = PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, RoutineReceiver::class.java)
                .setAction(ACTION_HABIT_DONE)
                .setData(AndroidRoutineAlarms.habitUri(habit.id))
                .putExtra(EXTRA_ID, habit.id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, ReminderNotifier.CHANNEL_HABITS)
            .setSmallIcon(R.drawable.ic_stat_habit)
            .setContentTitle(habit.name)
            .setContentText(context.getString(R.string.habit_reminder_text))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(AppLinks.open(context, AppLinks.HABITS))
            .addAction(0, context.getString(R.string.action_done), done)
            .build()
        notify(context, habitNotificationId(habit.id), notification)
    }

    private fun showReview(context: Context, kind: ReviewKind) {
        if (!notifier.canPost()) return
        val daily = kind == ReviewKind.DAILY
        val notification = NotificationCompat.Builder(context, ReminderNotifier.CHANNEL_HABITS)
            .setSmallIcon(R.drawable.ic_stat_reminder)
            .setContentTitle(context.getString(if (daily) R.string.review_daily_title else R.string.review_weekly_title))
            .setContentText(context.getString(if (daily) R.string.review_daily_text else R.string.review_weekly_text))
            .setAutoCancel(true)
            .setContentIntent(AppLinks.open(context, if (daily) AppLinks.DAILY_REVIEW else AppLinks.WEEKLY_REVIEW))
            .build()
        notify(context, kind.name.hashCode(), notification)
    }

    private fun notify(context: Context, id: Int, notification: android.app.Notification) {
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (e: SecurityException) {
            // Permission revoked.
        }
    }

    companion object {
        const val ACTION_FOCUS_END = "ir.roozban.action.FOCUS_END"
        const val ACTION_FOCUS_PAUSE = "ir.roozban.action.FOCUS_PAUSE"
        const val ACTION_FOCUS_RESUME = "ir.roozban.action.FOCUS_RESUME"
        const val ACTION_FOCUS_STOP = "ir.roozban.action.FOCUS_STOP"
        const val ACTION_FOCUS_SKIP = "ir.roozban.action.FOCUS_SKIP"
        const val ACTION_FOCUS_NEXT = "ir.roozban.action.FOCUS_NEXT"
        const val ACTION_HABIT_FIRE = "ir.roozban.action.HABIT_FIRE"
        const val ACTION_HABIT_DONE = "ir.roozban.action.HABIT_DONE"
        const val ACTION_REVIEW_FIRE = "ir.roozban.action.REVIEW_FIRE"
        const val EXTRA_ID = "id"

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        fun habitNotificationId(habitId: String): Int = ("habit:$habitId").hashCode()
    }
}
