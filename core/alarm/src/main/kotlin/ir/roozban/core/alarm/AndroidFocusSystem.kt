package ir.roozban.core.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import ir.roozban.core.calendar.PersianDigits
import ir.roozban.core.domain.FocusSystem
import ir.roozban.core.model.FocusPhase
import ir.roozban.core.model.FocusState
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The timer's platform side without a foreground service: an exact alarm for the end of the phase
 * and an ongoing notification whose countdown the system draws by itself.
 */
@Singleton
class AndroidFocusSystem @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dnd: FocusDnd,
    private val notifier: ReminderNotifier,
) : FocusSystem {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)
    private val manager = NotificationManagerCompat.from(context)

    override fun scheduleEnd(atEpochMillis: Long) {
        val operation = endIntent(PendingIntent.FLAG_UPDATE_CURRENT)!!
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atEpochMillis, operation)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atEpochMillis, operation)
            }
        } catch (e: SecurityException) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atEpochMillis, operation)
        }
    }

    override fun cancelEnd() {
        endIntent(PendingIntent.FLAG_NO_CREATE)?.let {
            alarmManager.cancel(it)
            it.cancel()
        }
    }

    override fun show(state: FocusState, taskTitle: String?) = post(state, taskTitle, alert = null)

    override fun announcePhaseEnd(finished: FocusPhase, next: FocusState, taskTitle: String?) =
        post(next, taskTitle, alert = finished)

    override fun setSilenced(on: Boolean): Boolean = dnd.set(on)

    override fun canSilence(): Boolean = dnd.hasAccess()

    private fun post(state: FocusState, taskTitle: String?, alert: FocusPhase?) {
        if (state == FocusState.Idle) {
            manager.cancel(NOTIFICATION_ID)
            return
        }
        if (!notifier.canPost()) return
        val channel = if (alert != null) ReminderNotifier.CHANNEL_FOCUS_END else ReminderNotifier.CHANNEL_FOCUS
        val builder = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_focus)
            .setCategory(if (alert != null) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_PROGRESS)
            .setContentIntent(AppLinks.open(context, AppLinks.FOCUS))
            .setOnlyAlertOnce(alert == null)
            .setSilent(alert == null)
            .setPriority(if (alert != null) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_LOW)
            .setSubText(taskTitle)
        val phase = when (state) {
            is FocusState.Running -> state.phase
            is FocusState.Paused -> state.phase
            is FocusState.Ready -> state.phase
            FocusState.Idle -> return
        }
        val title = buildString {
            if (alert != null) append(context.getString(if (alert == FocusPhase.WORK) R.string.focus_done_title else R.string.focus_break_over_title)).append(" — ")
            append(phaseName(phase))
        }
        builder.setContentTitle(title)
        when (state) {
            is FocusState.Running -> builder
                .setOngoing(true)
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
                .setWhen(state.endsAt.toEpochMilli())
                .setShowWhen(true)
                .addAction(0, context.getString(R.string.focus_pause), action(RoutineReceiver.ACTION_FOCUS_PAUSE))
                .addAction(0, context.getString(R.string.focus_skip), action(RoutineReceiver.ACTION_FOCUS_SKIP))
                .addAction(0, context.getString(R.string.focus_stop), action(RoutineReceiver.ACTION_FOCUS_STOP))
            is FocusState.Paused -> builder
                .setOngoing(true)
                .setShowWhen(false)
                .setContentText(context.getString(R.string.focus_paused_left, minutesLabel(state.remainingMillis)))
                .addAction(0, context.getString(R.string.focus_resume), action(RoutineReceiver.ACTION_FOCUS_RESUME))
                .addAction(0, context.getString(R.string.focus_stop), action(RoutineReceiver.ACTION_FOCUS_STOP))
            is FocusState.Ready -> builder
                .setOngoing(false)
                .setAutoCancel(true)
                .setShowWhen(false)
                .setContentText(context.getString(R.string.focus_ready_text))
                .addAction(0, context.getString(R.string.focus_start), action(RoutineReceiver.ACTION_FOCUS_NEXT))
                .addAction(0, context.getString(R.string.focus_stop), action(RoutineReceiver.ACTION_FOCUS_STOP))
            FocusState.Idle -> Unit
        }
        try {
            manager.notify(NOTIFICATION_ID, builder.build())
        } catch (e: SecurityException) {
            // Notification permission revoked.
        }
    }

    private fun phaseName(phase: FocusPhase): String = context.getString(
        when (phase) {
            FocusPhase.WORK -> R.string.focus_phase_work
            FocusPhase.SHORT_BREAK -> R.string.focus_phase_short_break
            FocusPhase.LONG_BREAK -> R.string.focus_phase_long_break
        },
    )

    private fun minutesLabel(millis: Long): String {
        val total = millis / 1000
        return PersianDigits.format2((total / 60).toInt()) + ":" + PersianDigits.format2((total % 60).toInt())
    }

    private fun action(action: String): PendingIntent = PendingIntent.getBroadcast(
        context,
        action.hashCode(),
        Intent(context, RoutineReceiver::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun endIntent(flags: Int): PendingIntent? = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, RoutineReceiver::class.java).setAction(RoutineReceiver.ACTION_FOCUS_END),
        flags or PendingIntent.FLAG_IMMUTABLE,
    )

    private companion object {
        const val NOTIFICATION_ID = 0x0F0C05
    }
}
