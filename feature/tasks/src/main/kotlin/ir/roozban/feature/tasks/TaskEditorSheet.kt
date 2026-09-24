package ir.roozban.feature.tasks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.roozban.core.calendar.PersianDateFormatter
import ir.roozban.core.designsystem.R as DsR
import ir.roozban.core.domain.Undo
import ir.roozban.core.model.Quadrant
import ir.roozban.core.model.ReminderKind
import ir.roozban.core.model.Task
import ir.roozban.core.model.TaskDue
import ir.roozban.core.ui.JalaliDatePickerDialog
import ir.roozban.core.ui.TimePickerDialog
import java.time.LocalDate
import java.time.LocalTime

private val ESTIMATES = listOf(15, 30, 45, 60, 90, 120)
private val OFFSETS = listOf(0, 5, 15, 30, 60)

/** Edits one task. Changes are saved when the sheet is closed or «ذخیره» is tapped. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TaskEditorSheet(
    taskId: String,
    onClose: () -> Unit,
    onDeleted: (Undo) -> Unit,
    viewModel: TaskEditorViewModel = hiltViewModel(key = "editor-$taskId"),
) {
    LaunchedEffect(taskId) { viewModel.load(taskId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = { if (state?.canSave == true) viewModel.save(onClose) else { viewModel.discard(); onClose() } },
        sheetState = sheetState,
    ) {
        val s = state
        if (s != null) {
            val task = s.draft
            EditorContent(
                task = task,
                onTitle = viewModel::setTitle,
                onNotes = viewModel::setNotes,
                onPickDate = { showDate = true },
                onPickTime = { showTime = true },
                onClearTime = { viewModel.setTime(null) },
                onReminderKind = viewModel::setReminderKind,
                onReminderOffset = viewModel::setReminderOffset,
                onRemoveRecurrence = viewModel::removeRecurrence,
                onImportant = viewModel::setImportant,
                onUrgent = viewModel::setUrgent,
                onEstimate = viewModel::setEstimate,
                onSave = { viewModel.save(onClose) },
                onDelete = { viewModel.delete(onDeleted) },
                canSave = s.canSave,
                today = viewModel.today,
            )
            if (showDate) {
                JalaliDatePickerDialog(
                    initial = task.due?.date,
                    today = viewModel.today,
                    onConfirm = {
                        viewModel.setDate(it)
                        showDate = false
                    },
                    onDismiss = { showDate = false },
                )
            }
            if (showTime) {
                TimePickerDialog(
                    initial = (task.due as? TaskDue.At)?.time ?: LocalTime.of(9, 0),
                    onConfirm = {
                        viewModel.setTime(it)
                        showTime = false
                    },
                    onDismiss = { showTime = false },
                )
            }
        }
    }
}

@Composable
private fun EditorContent(
    task: Task,
    onTitle: (String) -> Unit,
    onNotes: (String) -> Unit,
    onPickDate: () -> Unit,
    onPickTime: () -> Unit,
    onClearTime: () -> Unit,
    onReminderKind: (ReminderKind?) -> Unit,
    onReminderOffset: (Int) -> Unit,
    onRemoveRecurrence: () -> Unit,
    onImportant: (Boolean) -> Unit,
    onUrgent: (Boolean) -> Unit,
    onEstimate: (Int?) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    canSave: Boolean,
    today: LocalDate,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp)
            .imePadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            value = task.title,
            onValueChange = onTitle,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.editor_title_hint)) },
            textStyle = MaterialTheme.typography.titleMedium,
        )

        val due = task.due
        EditorRow(DsR.drawable.ic_today, stringResource(R.string.editor_date), onClick = onPickDate) {
            Text(
                due?.let { PersianDateFormatter.relativeDay(it.date, today) } ?: stringResource(R.string.editor_no_date),
                color = MaterialTheme.colorScheme.primary,
            )
        }
        EditorRow(DsR.drawable.ic_schedule, stringResource(R.string.editor_time), onClick = onPickTime) {
            if (due is TaskDue.At) {
                Text(PersianDateFormatter.time(due.time), color = MaterialTheme.colorScheme.primary)
                TextButton(onClick = onClearTime) { Text(stringResource(R.string.editor_clear_time)) }
            } else {
                Text(stringResource(R.string.editor_no_time), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        if (due != null) {
            Section(DsR.drawable.ic_notifications, stringResource(R.string.editor_reminder)) {
                ChipRow {
                    val kind = task.reminder?.kind
                    FilterChip(kind == null, { onReminderKind(null) }, { Text(stringResource(R.string.editor_reminder_none)) })
                    FilterChip(kind == ReminderKind.NOTIFICATION, { onReminderKind(ReminderKind.NOTIFICATION) }, {
                        Text(stringResource(R.string.editor_reminder_notification))
                    })
                    FilterChip(kind == ReminderKind.ALARM, { onReminderKind(ReminderKind.ALARM) }, {
                        Text(stringResource(R.string.editor_reminder_alarm))
                    })
                }
                val reminder = task.reminder
                if (reminder != null && due is TaskDue.At) {
                    ChipRow {
                        OFFSETS.forEach { minutes ->
                            val label = if (minutes == 0) {
                                stringResource(R.string.editor_on_time)
                            } else {
                                stringResource(R.string.editor_minutes_before, TaskFormatter.duration(minutes))
                            }
                            FilterChip(reminder.offsetMinutes == minutes, { onReminderOffset(minutes) }, { Text(label) })
                        }
                    }
                } else if (reminder != null) {
                    Text(
                        stringResource(R.string.editor_reminder_all_day_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        task.recurrence?.let { rrule ->
            EditorRow(DsR.drawable.ic_repeat, stringResource(R.string.editor_repeat), onClick = null) {
                Text(TaskFormatter.recurrence(rrule).orEmpty(), color = MaterialTheme.colorScheme.primary)
                TextButton(onClick = onRemoveRecurrence) { Text(stringResource(R.string.editor_remove_repeat)) }
            }
        }

        Section(DsR.drawable.ic_flag, stringResource(R.string.editor_priority)) {
            ChipRow {
                FilterChip(task.important, { onImportant(!task.important) }, { Text(stringResource(R.string.editor_important)) })
                FilterChip(task.urgent, { onUrgent(!task.urgent) }, { Text(stringResource(R.string.editor_urgent)) })
            }
            Text(
                stringResource(
                    when (task.quadrant) {
                        Quadrant.DO_FIRST -> R.string.quadrant_do_first
                        Quadrant.SCHEDULE -> R.string.quadrant_schedule
                        Quadrant.DELEGATE -> R.string.quadrant_delegate
                        Quadrant.ELIMINATE, Quadrant.NONE -> R.string.quadrant_none
                    },
                ),
                style = MaterialTheme.typography.bodySmall,
                color = quadrantColor(task.quadrant),
            )
        }

        Section(DsR.drawable.ic_timer, stringResource(R.string.editor_estimate)) {
            ChipRow {
                FilterChip(task.estimateMinutes == null, { onEstimate(null) }, { Text(stringResource(R.string.editor_estimate_none)) })
                ESTIMATES.forEach { minutes ->
                    FilterChip(task.estimateMinutes == minutes, { onEstimate(minutes) }, { Text(TaskFormatter.duration(minutes)) })
                }
            }
        }

        OutlinedTextField(
            value = task.notes,
            onValueChange = onNotes,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.editor_notes_hint)) },
            minLines = 2,
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onDelete) {
                Icon(painterResource(DsR.drawable.ic_delete), contentDescription = null, tint = MaterialTheme.colorScheme.error)
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.editor_delete), color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.weight(1f))
            Button(onClick = onSave, enabled = canSave) { Text(stringResource(R.string.editor_save)) }
        }
    }
}

@Composable
private fun EditorRow(icon: Int, label: String, onClick: (() -> Unit)?, value: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        value()
    }
}

@Composable
private fun Section(icon: Int, label: String, content: @Composable () -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text(label, style = MaterialTheme.typography.bodyLarge)
        }
        Spacer(Modifier.height(6.dp))
        content()
    }
}

@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        content()
    }
}
