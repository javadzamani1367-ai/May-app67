package ir.roozban.core.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import ir.roozban.core.domain.AlarmScheduler
import ir.roozban.core.model.ReminderKind
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Schedules reminders with AlarmManager:
 * - alarms use `setAlarmClock` (highest reliability, shown in the status bar),
 * - notifications use `setExactAndAllowWhileIdle`,
 * - without exact-alarm permission both fall back to inexact `setAndAllowWhileIdle`.
 */
@Singleton
class AndroidAlarmScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) : AlarmScheduler {

    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    override fun schedule(taskId: String, kind: ReminderKind, triggerAtEpochMillis: Long) {
        val operation = ReminderReceiver.fireIntent(context, taskId, kind).toBroadcast(context, taskId, PendingIntent.FLAG_UPDATE_CURRENT)!!
        try {
            when {
                !canScheduleExact() ->
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtEpochMillis, operation)
                kind == ReminderKind.ALARM -> {
                    val show = context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
                        PendingIntent.getActivity(context, 0, it, PendingIntent.FLAG_IMMUTABLE)
                    }
                    alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAtEpochMillis, show), operation)
                }
                else -> alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtEpochMillis, operation)
            }
        } catch (e: SecurityException) {
            // Exact-alarm permission revoked between the check and the call: fall back to inexact.
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtEpochMillis, operation)
        }
    }

    override fun cancel(taskId: String) {
        val intent = Intent(context, ReminderReceiver::class.java).setAction(ReminderReceiver.ACTION_FIRE).setData(taskUri(taskId))
        intent.toBroadcast(context, taskId, PendingIntent.FLAG_NO_CREATE)?.let {
            alarmManager.cancel(it)
            it.cancel()
        }
    }

    fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    /** Daily inexact wake-up that brings reminders beyond the 7-day window into AlarmManager. */
    fun scheduleDailyReconcile(firstAtEpochMillis: Long) {
        val operation = PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, ReminderReceiver::class.java).setAction(ReminderReceiver.ACTION_RECONCILE),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        alarmManager.setInexactRepeating(AlarmManager.RTC, firstAtEpochMillis, AlarmManager.INTERVAL_DAY, operation)
    }

    companion object {
        /** One distinct PendingIntent per task: the data URI makes intents unequal. */
        fun taskUri(taskId: String): Uri = Uri.parse("roozban://reminder/$taskId")

        private fun Intent.toBroadcast(context: Context, taskId: String, flags: Int): PendingIntent? =
            PendingIntent.getBroadcast(context, 0, setData(taskUri(taskId)), flags or PendingIntent.FLAG_IMMUTABLE)
    }
}
