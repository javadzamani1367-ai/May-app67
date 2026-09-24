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

/** Full-screen alarm, shown over the lock screen. The sound comes from the insistent notification. */
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
            finish()
            return
        }
        lifecycleScope.launch { task = tasks.get(taskId) }
        setContent {
            RoozbanTheme {
                AlarmScreen(
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

        fun pendingIntent(context: Context, taskId: String): PendingIntent =
            PendingIntent.getActivity(
                context,
                0,
                Intent(context, AlarmActivity::class.java)
                    .setData(AndroidAlarmScheduler.taskUri(taskId))
                    .putExtra(EXTRA_TASK_ID, taskId)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
    }
}

@Composable
private fun AlarmScreen(title: String, time: String, onDone: () -> Unit, onSnooze: () -> Unit, onDismiss: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(time, style = MaterialTheme.typography.displayLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.alarm_title), style = MaterialTheme.typography.titleMedium)
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
