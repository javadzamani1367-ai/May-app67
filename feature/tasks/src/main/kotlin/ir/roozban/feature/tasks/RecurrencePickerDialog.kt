package ir.roozban.feature.tasks

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ir.roozban.core.calendar.PersianDigits
import ir.roozban.core.calendar.PersianNames
import ir.roozban.core.calendar.PersianWeek
import ir.roozban.core.recurrence.Frequency
import ir.roozban.core.recurrence.RecurrenceSpec
import java.time.DayOfWeek

/**
 * Chooses a recurrence: none, every N days / weeks (on chosen weekdays) / Jalali months (same day or
 * last day) / years. [startDay] is the task's date, used as the default weekday.
 */
@Composable
internal fun RecurrencePickerDialog(
    initial: RecurrenceSpec?,
    startDay: DayOfWeek,
    onConfirm: (RecurrenceSpec?) -> Unit,
    onDismiss: () -> Unit,
) {
    var frequency by remember { mutableStateOf(initial?.frequency) }
    var interval by remember { mutableStateOf(initial?.interval ?: 1) }
    var weekdays by remember { mutableStateOf(initial?.byWeekdays?.ifEmpty { null } ?: setOf(startDay)) }
    var lastDay by remember { mutableStateOf(initial?.jalaliMonthDay == -1) }

    fun build(): RecurrenceSpec? = when (val f = frequency) {
        null -> null
        Frequency.WEEKLY -> RecurrenceSpec(f, interval, byWeekdays = weekdays.ifEmpty { setOf(startDay) })
        Frequency.MONTHLY -> RecurrenceSpec(f, interval, jalaliMonthDay = if (lastDay) -1 else null)
        else -> RecurrenceSpec(f, interval)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.repeat_title)) },
        confirmButton = { TextButton(onClick = { onConfirm(build()) }) { Text(stringResource(R.string.dialog_ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_cancel)) } },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(frequency == null, { frequency = null }, { Text(stringResource(R.string.repeat_none)) })
                    FilterChip(frequency == Frequency.DAILY, { frequency = Frequency.DAILY }, { Text(stringResource(R.string.repeat_daily)) })
                    FilterChip(frequency == Frequency.WEEKLY, { frequency = Frequency.WEEKLY }, { Text(stringResource(R.string.repeat_weekly)) })
                    FilterChip(frequency == Frequency.MONTHLY, { frequency = Frequency.MONTHLY }, { Text(stringResource(R.string.repeat_monthly)) })
                    FilterChip(frequency == Frequency.YEARLY, { frequency = Frequency.YEARLY }, { Text(stringResource(R.string.repeat_yearly)) })
                }
                val f = frequency
                if (f != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.repeat_every))
                        FilledTonalIconButton(onClick = { interval = (interval - 1).coerceAtLeast(1) }, modifier = Modifier.padding(horizontal = 8.dp)) {
                            Text("−")
                        }
                        Text(PersianDigits.format(interval), style = MaterialTheme.typography.titleMedium)
                        FilledTonalIconButton(onClick = { interval = (interval + 1).coerceAtMost(99) }, modifier = Modifier.padding(horizontal = 8.dp)) {
                            Text("+")
                        }
                        Text(
                            stringResource(
                                when (f) {
                                    Frequency.DAILY -> R.string.repeat_unit_day
                                    Frequency.WEEKLY -> R.string.repeat_unit_week
                                    Frequency.MONTHLY -> R.string.repeat_unit_month
                                    Frequency.YEARLY -> R.string.repeat_unit_year
                                },
                            ),
                        )
                    }
                }
                if (f == Frequency.WEEKLY) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        PersianWeek.DAYS.forEachIndexed { i, day ->
                            FilterChip(
                                selected = day in weekdays,
                                onClick = { weekdays = if (day in weekdays) weekdays - day else weekdays + day },
                                label = { Text(PersianNames.WEEKDAYS_SHORT[i]) },
                            )
                        }
                    }
                }
                if (f == Frequency.MONTHLY) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(!lastDay, { lastDay = false }, { Text(stringResource(R.string.repeat_month_same_day)) })
                        FilterChip(lastDay, { lastDay = true }, { Text(stringResource(R.string.repeat_month_last_day)) })
                    }
                }
                build()?.let {
                    Text(TaskFormatter.recurrence(it), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
    )
}
