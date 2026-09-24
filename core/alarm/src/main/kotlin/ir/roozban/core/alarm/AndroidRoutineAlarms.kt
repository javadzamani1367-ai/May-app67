package ir.roozban.core.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import ir.roozban.core.domain.ReviewKind
import ir.roozban.core.domain.RoutineAlarms
import javax.inject.Inject
import javax.inject.Singleton

/** Habit and review reminders; one PendingIntent per habit / review kind (told apart by URI). */
@Singleton
class AndroidRoutineAlarms @Inject constructor(
    @ApplicationContext private val context: Context,
) : RoutineAlarms {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    override fun scheduleHabit(habitId: String, atEpochMillis: Long) =
        schedule(habitIntent(habitId, PendingIntent.FLAG_UPDATE_CURRENT)!!, atEpochMillis)

    override fun cancelHabit(habitId: String) = cancel(habitIntent(habitId, PendingIntent.FLAG_NO_CREATE))

    override fun scheduleReview(kind: ReviewKind, atEpochMillis: Long) =
        schedule(reviewIntent(kind, PendingIntent.FLAG_UPDATE_CURRENT)!!, atEpochMillis)

    override fun cancelReview(kind: ReviewKind) = cancel(reviewIntent(kind, PendingIntent.FLAG_NO_CREATE))

    private fun schedule(operation: PendingIntent, at: Long) {
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, operation)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, operation)
            }
        } catch (e: SecurityException) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, operation)
        }
    }

    private fun cancel(operation: PendingIntent?) {
        operation ?: return
        alarmManager.cancel(operation)
        operation.cancel()
    }

    private fun habitIntent(habitId: String, flags: Int): PendingIntent? = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, RoutineReceiver::class.java)
            .setAction(RoutineReceiver.ACTION_HABIT_FIRE)
            .setData(habitUri(habitId))
            .putExtra(RoutineReceiver.EXTRA_ID, habitId),
        flags or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun reviewIntent(kind: ReviewKind, flags: Int): PendingIntent? = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, RoutineReceiver::class.java)
            .setAction(RoutineReceiver.ACTION_REVIEW_FIRE)
            .setData(Uri.parse("roozban://review/${kind.name}"))
            .putExtra(RoutineReceiver.EXTRA_ID, kind.name),
        flags or PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        fun habitUri(habitId: String): Uri = Uri.parse("roozban://habit/$habitId")
    }
}
