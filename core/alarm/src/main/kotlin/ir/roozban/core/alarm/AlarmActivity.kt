package ir.roozban.core.alarm

import android.app.KeyguardManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import ir.roozban.core.calendar.PersianDateFormatter
import ir.roozban.core.designsystem.theme.RoozbanTheme
import ir.roozban.core.domain.CompleteTaskUseCase
import ir.roozban.core.domain.ReminderSync
import ir.roozban.core.domain.TaskRepository
import ir.roozban.core.model.Task
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalTime
import javax.inject.Inject

/**
 * Full-screen alarm, shown over the lock screen. The sound comes from the notification (insistent
 * for alarms). With «نمایش تمام‌صفحه» on, plain reminders, habits and occasions open it too; those
 * without a task only show their text and a close button.
 */
@AndroidEntryPoint
class AlarmActivity : ComponentActivity() {

    @Inject lateinit var tasks: TaskRepository
    @Inject lateinit var complete: CompleteTaskUseCase
    @Inject lateinit var sync: ReminderSync
    @Inject lateinit var notifier: ReminderNotifier
    @Inject lateinit var clock: Clock

    private var task by mutableStateOf<Task?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockScreen()
        val taskId = intent.getStringExtra(EXTRA_TASK_ID)
        if (taskId == null) {
            val title = intent.getStringExtra(EXTRA_TITLE)
            if (title == null) {
                finish()
                return
            }
            val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0)
            setContent {
                RoozbanTheme {
                    MessageScreen(
                        title = title,
                        text = intent.getStringExtra(EXTRA_TEXT).orEmpty(),
                        time = PersianDateFormatter.time(LocalTime.now(clock)),
                        onClose = {
                            getSystemService(android.app.NotificationManager::class.java)?.cancel(notificationId)
                            finish()
                        },
                    )
                }
            }
            return
        }
        val alarm = intent.getBooleanExtra(EXTRA_ALARM, true)
        lifecycleScope.launch { task = tasks.get(taskId) }
        setContent {
            RoozbanTheme {
                AlarmScreen(
                    heading = stringResource(if (alarm) R.string.alarm_title else R.string.reminder_title),
                    title = task?.title.orEmpty(),
                    time = PersianDateFormatter.time(LocalTime.now(clock)),
                    onDone = { act(taskId) { t -> complete(t) } },
                    onSnooze = { act(taskId) { sync.snooze(taskId, 10) } },
                    onDismiss = { act(taskId) { } },
                )
            }
        }
    }

    private fun act(taskId: String, block: suspend (Task) -> Unit) {
        lifecycleScope.launch {
            notifier.cancel(taskId)
            tasks.get(taskId)?.let { block(it) }
            finish()
        }
    }

    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            getSystemService(KeyguardManager::class.java)?.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            )
        }
    }

    companion object {
        private const val EXTRA_TASK_ID = "task_id"
        private const val EXTRA_ALARM = "alarm"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_TEXT = "text"
        private const val EXTRA_NOTIFICATION_ID = "notification_id"

        fun pendingIntent(context: Context, taskId: String, alarm: Boolean = true): PendingIntent =
            PendingIntent.getActivity(
                context,
                0,
                Intent(context, AlarmActivity::class.java)
                    .setData(AndroidAlarmScheduler.taskUri(taskId))
                    .putExtra(EXTRA_TASK_ID, taskId)
                    .putExtra(EXTRA_ALARM, alarm)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

        /** A reminder without a task (a habit, an occasion): its text and a close button. */
        fun messageIntent(context: Context, notificationId: Int, title: String, text: String?): PendingIntent =
            PendingIntent.getActivity(
                context,
                notificationId,
                Intent(context, AlarmActivity::class.java)
                    .setData(android.net.Uri.parse("roozban://alert/$notificationId"))
                    .putExtra(EXTRA_TITLE, title)
                    .putExtra(EXTRA_TEXT, text)
                    .putExtra(EXTRA_NOTIFICATION_ID, notificationId)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
    }
}

@Composable
private fun MessageScreen(title: String, text: String, time: String, onClose: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(time, style = MaterialTheme.typography.displayLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Spacer(Modifier.height(24.dp))
            Text(title, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
            if (text.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(text, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(48.dp))
            Button(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_dismiss)) }
        }
    }
}

@Composable
private fun AlarmScreen(heading: String, title: String, time: String, onDone: () -> Unit, onSnooze: () -> Unit, onDismiss: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(time, style = MaterialTheme.typography.displayLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Spacer(Modifier.height(8.dp))
            Text(heading, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(24.dp))
            Text(title, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
            Spacer(Modifier.height(48.dp))
            Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_done)) }
            Spacer(Modifier.height(12.dp))
            FilledTonalButton(onClick = onSnooze, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_snooze_10)) }
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_dismiss)) }
        }
    }
}
