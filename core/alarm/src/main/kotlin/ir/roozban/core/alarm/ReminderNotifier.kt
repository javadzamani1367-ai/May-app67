package ir.roozban.core.alarm

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import ir.roozban.core.calendar.PersianDateFormatter
import ir.roozban.core.model.ReminderKind
import ir.roozban.core.model.Task
import ir.roozban.core.model.TaskDue
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** Posts reminder notifications. Alarms repeat their sound until the user responds. */
@Singleton
class ReminderNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val clock: Clock,
) {
    private val manager = NotificationManagerCompat.from(context)

    fun createChannels() {
        val system = context.getSystemService(NotificationManager::class.java)
        // Channels are immutable once created: replaced ones get new ids and the old ones go.
        LEGACY_CHANNELS.forEach { system.deleteNotificationChannel(it) }
        val notificationSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val eventAudio = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        system.createNotificationChannels(
            listOf(
                NotificationChannel(CHANNEL_REMINDERS, context.getString(R.string.channel_reminders), NotificationManager.IMPORTANCE_HIGH)
                    .apply { description = context.getString(R.string.channel_reminders_desc) },
                NotificationChannel(CHANNEL_ALARMS, context.getString(R.string.channel_alarms), NotificationManager.IMPORTANCE_HIGH).apply {
                    description = context.getString(R.string.channel_alarms_desc)
                    setSound(alarmSound, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build())
                    enableVibration(true)
                },
                NotificationChannel(CHANNEL_MISSED, context.getString(R.string.channel_missed), NotificationManager.IMPORTANCE_DEFAULT)
                    .apply { description = context.getString(R.string.channel_missed_desc) },
                NotificationChannel(CHANNEL_FOCUS, context.getString(R.string.channel_focus), NotificationManager.IMPORTANCE_LOW).apply {
                    description = context.getString(R.string.channel_focus_desc)
                    setShowBadge(false)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                },
                // Alerting channels carry an explicit sound and vibration: some OEMs create
                // default-importance channels silent, which made habit reminders go unnoticed.
                NotificationChannel(CHANNEL_FOCUS_END, context.getString(R.string.channel_focus_end), NotificationManager.IMPORTANCE_HIGH).apply {
                    description = context.getString(R.string.channel_focus_end_desc)
                    setSound(notificationSound, eventAudio)
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 400, 200, 400)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                },
                NotificationChannel(CHANNEL_DATE, context.getString(R.string.channel_date), NotificationManager.IMPORTANCE_LOW).apply {
                    description = context.getString(R.string.channel_date_desc)
                    setShowBadge(false)
                    setSound(null, null)
                    enableVibration(false)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                },
                NotificationChannel(CHANNEL_EVENTS, context.getString(R.string.channel_events), NotificationManager.IMPORTANCE_HIGH).apply {
                    description = context.getString(R.string.channel_events_desc)
                    setSound(notificationSound, eventAudio)
                    enableVibration(true)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                },
                NotificationChannel(CHANNEL_HABITS, context.getString(R.string.channel_habits), NotificationManager.IMPORTANCE_HIGH).apply {
                    description = context.getString(R.string.channel_habits_desc)
                    setSound(notificationSound, eventAudio)
                    enableVibration(true)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                },
            ),
        )
    }

    fun canPost(): Boolean =
        (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            manager.areNotificationsEnabled()

    fun show(task: Task, kind: ReminderKind, missed: Boolean = false) {
        if (!canPost()) return
        val channel = when {
            missed -> CHANNEL_MISSED
            kind == ReminderKind.ALARM -> CHANNEL_ALARMS
            else -> CHANNEL_REMINDERS
        }
        val title = if (missed) context.getString(R.string.missed_prefix, task.title) else task.title
        val builder = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_reminder)
            .setContentTitle(title)
            .setContentText(whenLabel(task))
            .setCategory(if (kind == ReminderKind.ALARM && !missed) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openApp())
            .addAction(0, context.getString(R.string.action_done), ReminderReceiver.actionIntent(context, task.id, ReminderReceiver.ACTION_DONE))
            .addAction(0, context.getString(R.string.action_snooze_10), ReminderReceiver.snoozeIntent(context, task.id, 10))
            .addAction(0, context.getString(R.string.action_snooze_60), ReminderReceiver.snoozeIntent(context, task.id, 60))

        if (kind == ReminderKind.ALARM && !missed) {
            builder.setOngoing(true)
                .setFullScreenIntent(AlarmActivity.pendingIntent(context, task.id), true)
                .setDeleteIntent(ReminderReceiver.actionIntent(context, task.id, ReminderReceiver.ACTION_DISMISS))
        }
        val notification = builder.build()
        if (kind == ReminderKind.ALARM && !missed) notification.flags = notification.flags or Notification.FLAG_INSISTENT
        try {
            manager.notify(notificationId(task.id), notification)
        } catch (e: SecurityException) {
            // Permission revoked between the check and the call; nothing else to do.
        }
    }

    fun cancel(taskId: String) = manager.cancel(notificationId(taskId))

    private fun whenLabel(task: Task): String? = when (val due = task.due) {
        null -> null
        is TaskDue.AllDay -> PersianDateFormatter.relativeDay(due.date, LocalDate.now(clock))
        is TaskDue.At -> PersianDateFormatter.relativeDateTime(due.dateTime, LocalDate.now(clock))
    }

    private fun openApp(): PendingIntent? =
        context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
            PendingIntent.getActivity(context, 0, it, PendingIntent.FLAG_IMMUTABLE)
        }

    companion object {
        const val CHANNEL_REMINDERS = "reminders"
        const val CHANNEL_ALARMS = "alarms"
        const val CHANNEL_MISSED = "missed"
        const val CHANNEL_FOCUS = "focus"
        const val CHANNEL_FOCUS_END = "focus_alerts"
        const val CHANNEL_HABITS = "habit_reminders"
        const val CHANNEL_EVENTS = "personal_events"
        const val CHANNEL_DATE = "today_date"
        private val LEGACY_CHANNELS = listOf("focus_end", "habits")

        fun notificationId(taskId: String): Int = taskId.hashCode()
    }
}
