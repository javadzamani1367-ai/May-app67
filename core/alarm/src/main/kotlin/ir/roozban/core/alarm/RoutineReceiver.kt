package ir.roozban.core.alarm

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.AndroidEntryPoint
import androidx.core.content.ContextCompat
import ir.roozban.core.calendar.PersianDateFormatter
import ir.roozban.core.calendar.PersianDigits
import ir.roozban.core.calendar.toJalali
import ir.roozban.core.designsystem.icons.eventIcon
import ir.roozban.core.domain.EventReminders
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
    @Inject lateinit var events: EventReminders
    @Inject lateinit var dateNotifier: DateNotifier

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
                    ACTION_DATE_REFRESH -> dateNotifier.refresh()
                    ACTION_EVENT_FIRE -> if (id != null) events.onAlarm(id)?.let { showEvent(context, it) }
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
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(AppLinks.open(context, AppLinks.HABITS))
            .addAction(0, context.getString(R.string.action_done), done)
            .build()
        notify(context, habitNotificationId(habit.id), notification)
    }

    private fun showEvent(context: Context, due: EventReminders.Due) {
        if (!notifier.canPost()) return
        val event = due.occurrence.event
        val count = due.occurrence.count?.takeIf { it > 0 }
        val title = buildString {
            append(event.title)
            if (count != null) append(" — ").append(context.getString(R.string.event_count, PersianDigits.format(count)))
        }
        val text = when (due.daysBefore) {
            0 -> context.getString(R.string.event_today)
            1 -> context.getString(R.string.event_tomorrow)
            else -> context.getString(R.string.event_in_days, PersianDigits.format(due.daysBefore))
        } + " · " + PersianDateFormatter.dayMonth(due.occurrence.date.toJalali())
        val notification = NotificationCompat.Builder(context, ReminderNotifier.CHANNEL_EVENTS)
            .setSmallIcon(eventIcon(event.kind))
            .setColor(ContextCompat.getColor(context, R.color.event_accent))
            .setContentTitle(title)
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(AppLinks.open(context, AppLinks.CALENDAR))
            .build()
        notify(context, ("event:" + event.id).hashCode(), notification)
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
        const val ACTION_EVENT_FIRE = "ir.roozban.action.EVENT_FIRE"
        const val ACTION_DATE_REFRESH = "ir.roozban.action.DATE_REFRESH"
        const val EXTRA_ID = "id"

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        fun habitNotificationId(habitId: String): Int = ("habit:$habitId").hashCode()
    }
}
