package ir.roozban.core.alarm

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import ir.roozban.core.domain.CompleteTaskUseCase
import ir.roozban.core.domain.ReminderRepository
import ir.roozban.core.domain.ReminderSync
import ir.roozban.core.domain.TaskRepository
import ir.roozban.core.model.ReminderKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Fires reminders and handles notification actions (done, snooze, dismiss). */
@AndroidEntryPoint
class ReminderReceiver : BroadcastReceiver() {

    @Inject lateinit var tasks: TaskRepository
    @Inject lateinit var reminders: ReminderRepository
    @Inject lateinit var sync: ReminderSync
    @Inject lateinit var complete: CompleteTaskUseCase
    @Inject lateinit var notifier: ReminderNotifier
    @Inject lateinit var reconciler: ReminderReconciler

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getStringExtra(EXTRA_TASK_ID)
        val pending = goAsync()
        scope.launch {
            try {
                when (intent.action) {
                    ACTION_FIRE -> if (taskId != null) fire(taskId, intent)
                    ACTION_DONE -> if (taskId != null) {
                        notifier.cancel(taskId)
                        tasks.get(taskId)?.let { complete(it) }
                    }
                    ACTION_SNOOZE -> if (taskId != null) {
                        notifier.cancel(taskId)
                        sync.snooze(taskId, intent.getLongExtra(EXTRA_MINUTES, 10))
                    }
                    ACTION_DISMISS -> if (taskId != null) notifier.cancel(taskId)
                    ACTION_RECONCILE -> reconciler.reconcile()
                }
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun fire(taskId: String, intent: Intent) {
        val task = tasks.get(taskId) ?: return
        if (task.isCompleted) return
        val kind = intent.getStringExtra(EXTRA_KIND)?.let { runCatching { ReminderKind.valueOf(it) }.getOrNull() }
            ?: ReminderKind.NOTIFICATION
        reminders.markFired(taskId)
        notifier.show(task, kind)
    }

    companion object {
        const val ACTION_FIRE = "ir.roozban.action.REMINDER_FIRE"
        const val ACTION_DONE = "ir.roozban.action.REMINDER_DONE"
        const val ACTION_SNOOZE = "ir.roozban.action.REMINDER_SNOOZE"
        const val ACTION_DISMISS = "ir.roozban.action.REMINDER_DISMISS"
        const val ACTION_RECONCILE = "ir.roozban.action.RECONCILE"
        private const val EXTRA_TASK_ID = "task_id"
        private const val EXTRA_KIND = "kind"
        private const val EXTRA_MINUTES = "minutes"

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        fun fireIntent(context: Context, taskId: String, kind: ReminderKind): Intent =
            Intent(context, ReminderReceiver::class.java)
                .setAction(ACTION_FIRE)
                .putExtra(EXTRA_TASK_ID, taskId)
                .putExtra(EXTRA_KIND, kind.name)

        fun actionIntent(context: Context, taskId: String, action: String): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                action.hashCode(),
                Intent(context, ReminderReceiver::class.java)
                    .setAction(action)
                    .setData(AndroidAlarmScheduler.taskUri(taskId))
                    .putExtra(EXTRA_TASK_ID, taskId),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

        fun snoozeIntent(context: Context, taskId: String, minutes: Long): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                minutes.toInt(),
                Intent(context, ReminderReceiver::class.java)
                    .setAction(ACTION_SNOOZE)
                    .setData(AndroidAlarmScheduler.taskUri(taskId))
                    .putExtra(EXTRA_TASK_ID, taskId)
                    .putExtra(EXTRA_MINUTES, minutes),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
    }
}
