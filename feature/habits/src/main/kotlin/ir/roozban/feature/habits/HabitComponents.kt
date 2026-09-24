package ir.roozban.feature.habits

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ir.roozban.core.calendar.PersianDateFormatter
import ir.roozban.core.calendar.PersianDigits
import ir.roozban.core.calendar.PersianNames
import ir.roozban.core.calendar.PersianWeek
import ir.roozban.core.designsystem.theme.TagColors
import ir.roozban.core.domain.HabitDayStatus
import ir.roozban.core.domain.HabitUseCases
import ir.roozban.core.model.HabitSchedule
import ir.roozban.core.ui.TimePickerDialog
import java.time.DayOfWeek
import java.time.LocalTime

/** A day as a circle: filled when done, ring for partial, snowflake tint for frozen, cross-out color for missed. */
@Composable
internal fun DayDot(cell: HabitDayCell, target: Int, color: Color, size: Dp, label: String?, onClick: (() -> Unit)?) {
    val scheme = MaterialTheme.colorScheme
    val (fill, textColor, border) = when (cell.status) {
        HabitDayStatus.DONE -> Triple(color, scheme.surface, null)
        HabitDayStatus.FROZEN -> Triple(scheme.tertiaryContainer, scheme.onTertiaryContainer, null)
        HabitDayStatus.MISSED -> Triple(scheme.errorContainer.copy(alpha = 0.5f), scheme.onErrorContainer, null)
        HabitDayStatus.PARTIAL -> Triple(Color.Transparent, scheme.onSurface, color)
        HabitDayStatus.PENDING -> Triple(if (cell.count > 0) color.copy(alpha = 0.35f) else Color.Transparent, scheme.onSurface, color)
        HabitDayStatus.OFF -> Triple(if (cell.count > 0) color else Color.Transparent, if (cell.count > 0) scheme.surface else scheme.onSurfaceVariant, null)
        null -> Triple(Color.Transparent, scheme.outline, null)
    }
    var m = Modifier.size(size).clip(CircleShape).background(fill)
    if (border != null) m = m.border(2.dp, border, CircleShape)
    if (cell.isToday && border == null) m = m.border(2.dp, scheme.primary, CircleShape)
    if (onClick != null && !cell.isFuture) m = m.clickable(onClick = onClick)
    Box(m, contentAlignment = Alignment.Center) {
        val text = when {
            target > 1 && cell.count > 0 && cell.status != HabitDayStatus.DONE -> PersianDigits.format(cell.count)
            label != null -> label
            else -> null
        }
        if (text != null) Text(text, style = MaterialTheme.typography.labelMedium, color = textColor, textAlign = TextAlign.Center)
    }
}

@Composable
internal fun scheduleLabel(schedule: HabitSchedule): String = when (schedule) {
    HabitSchedule.Daily -> stringResource(R.string.habits_daily)
    is HabitSchedule.Weekdays -> schedule.days.sortedBy { PersianWeek.indexOf(it) }.joinToString("، ") { PersianNames.weekday(it) }
    is HabitSchedule.TimesPerWeek -> stringResource(R.string.habits_times_value, PersianDigits.format(schedule.times))
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun HabitEditorSheet(initial: HabitDraft, onSave: (HabitDraft) -> Unit, onDismiss: () -> Unit) {
    var draft by remember { mutableStateOf(initial) }
    var pickingTime by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
            OutlinedTextField(
                value = draft.name,
                onValueChange = { draft = draft.copy(name = it) },
                label = { Text(stringResource(R.string.habits_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.habits_color), style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                repeat(TagColors.count) { i ->
                    val c = TagColors.color(i)
                    Box(
                        Modifier.size(30.dp).clip(CircleShape).background(c)
                            .then(if (draft.color == i) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                            .clickable { draft = draft.copy(color = i) },
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.habits_schedule), style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            val kinds = listOf(R.string.habits_daily, R.string.habits_weekdays, R.string.habits_times_per_week)
            val selected = when (draft.schedule) {
                HabitSchedule.Daily -> 0
                is HabitSchedule.Weekdays -> 1
                is HabitSchedule.TimesPerWeek -> 2
            }
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                kinds.forEachIndexed { i, label ->
                    SegmentedButton(
                        selected = i == selected,
                        onClick = {
                            draft = draft.copy(
                                schedule = when (i) {
                                    0 -> HabitSchedule.Daily
                                    1 -> HabitSchedule.Weekdays(setOf(DayOfWeek.SATURDAY, DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY))
                                    else -> HabitSchedule.TimesPerWeek(3)
                                },
                            )
                        },
                        shape = SegmentedButtonDefaults.itemShape(i, kinds.size),
                    ) { Text(stringResource(label), maxLines = 1) }
                }
            }
            Spacer(Modifier.height(8.dp))
            when (val s = draft.schedule) {
                is HabitSchedule.Weekdays -> FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (0 until 7).map(PersianWeek::dayAt).forEach { day ->
                        val on = day in s.days
                        FilterChip(
                            selected = on,
                            onClick = {
                                val days = if (on) s.days - day else s.days + day
                                if (days.isNotEmpty()) draft = draft.copy(schedule = HabitSchedule.Weekdays(days))
                            },
                            label = { Text(PersianNames.weekday(day)) },
                        )
                    }
                }
                is HabitSchedule.TimesPerWeek -> Stepper(
                    stringResource(R.string.habits_times_value, PersianDigits.format(s.times)),
                    onMinus = { if (s.times > 1) draft = draft.copy(schedule = HabitSchedule.TimesPerWeek(s.times - 1)) },
                    onPlus = { if (s.times < 7) draft = draft.copy(schedule = HabitSchedule.TimesPerWeek(s.times + 1)) },
                )
                HabitSchedule.Daily -> Unit
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.habits_target), modifier = Modifier.weight(1f))
                Stepper(
                    stringResource(R.string.habits_target_value, PersianDigits.format(draft.target)),
                    onMinus = { if (draft.target > 1) draft = draft.copy(target = draft.target - 1) },
                    onPlus = { if (draft.target < HabitUseCases.MAX_TARGET) draft = draft.copy(target = draft.target + 1) },
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.habits_reminder), modifier = Modifier.weight(1f))
                TextButton(onClick = { pickingTime = true }) {
                    Text(draft.reminder?.let { PersianDateFormatter.time(it) } ?: stringResource(R.string.habits_reminder_off))
                }
                if (draft.reminder != null) {
                    TextButton(onClick = { draft = draft.copy(reminder = null) }) { Text(stringResource(R.string.habits_reminder_off)) }
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.habits_cancel)) }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = { onSave(draft) }, enabled = draft.name.isNotBlank()) { Text(stringResource(R.string.habits_save)) }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
    if (pickingTime) {
        TimePickerDialog(
            initial = draft.reminder ?: LocalTime.of(20, 0),
            onConfirm = { draft = draft.copy(reminder = it); pickingTime = false },
            onDismiss = { pickingTime = false },
        )
    }
}

@Composable
private fun Stepper(value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onMinus) { Text("−", style = MaterialTheme.typography.titleLarge) }
        Text(value, textAlign = TextAlign.Center, modifier = Modifier.width(96.dp))
        IconButton(onClick = onPlus) { Text("+", style = MaterialTheme.typography.titleLarge) }
    }
}
