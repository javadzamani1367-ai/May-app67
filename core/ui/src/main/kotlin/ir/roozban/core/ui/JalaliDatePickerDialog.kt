package ir.roozban.core.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ir.roozban.core.calendar.JalaliDate
import ir.roozban.core.calendar.PersianDigits
import ir.roozban.core.calendar.PersianNames
import ir.roozban.core.calendar.PersianWeek
import ir.roozban.core.calendar.toJalali
import ir.roozban.core.designsystem.R as DsR
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Jalali month-grid date picker: Saturday-first weeks, Fridays marked, quick choices.
 * [onConfirm] receives null when the user picks «بدون تاریخ».
 */
@Composable
fun JalaliDatePickerDialog(
    initial: LocalDate?,
    today: LocalDate,
    onConfirm: (LocalDate?) -> Unit,
    onDismiss: () -> Unit,
) {
    var selected by remember { mutableStateOf(initial) }
    var month by remember { mutableStateOf((initial ?: today).toJalali().firstDayOfMonth()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onConfirm(selected) }) { Text(stringResource(R.string.picker_ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.picker_cancel)) } },
        text = {
            Column {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val nextSaturday = PersianWeek.startOfWeek(today).plusWeeks(1)
                    QuickChip(stringResource(R.string.picker_today)) { onConfirm(today) }
                    QuickChip(stringResource(R.string.picker_tomorrow)) { onConfirm(today.plusDays(1)) }
                    QuickChip(stringResource(R.string.picker_next_saturday)) { onConfirm(nextSaturday) }
                    QuickChip(stringResource(R.string.picker_no_date)) { onConfirm(null) }
                }
                MonthHeader(
                    month = month,
                    onPrevious = { month = month.plusMonths(-1) },
                    onNext = { month = month.plusMonths(1) },
                )
                AnimatedContent(targetState = month, label = "month") { shown ->
                    MonthGrid(shown, today, selected) { selected = it }
                }
            }
        },
    )
}

@Composable
private fun QuickChip(label: String, onClick: () -> Unit) {
    AssistChip(onClick = onClick, label = { Text(label) })
}

@Composable
private fun MonthHeader(month: JalaliDate, onPrevious: () -> Unit, onNext: () -> Unit) {
    // Right-to-left: the previous month sits on the right, the next month on the left.
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrevious) {
            Icon(painterResource(DsR.drawable.ic_chevron_right), stringResource(R.string.picker_previous_month))
        }
        Text(
            text = "${PersianNames.jalaliMonth(month.month)} ${PersianDigits.format(month.year)}",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
        )
        IconButton(onClick = onNext) {
            Icon(painterResource(DsR.drawable.ic_chevron_left), stringResource(R.string.picker_next_month))
        }
    }
}

@Composable
private fun MonthGrid(month: JalaliDate, today: LocalDate, selected: LocalDate?, onSelect: (LocalDate) -> Unit) {
    val first = month.toLocalDate()
    val leading = PersianWeek.indexOf(first.dayOfWeek)
    val cells = (List(leading) { null } + (0 until month.lengthOfMonth).map { first.plusDays(it.toLong()) })
        .chunked(7)
    Column {
        Row(Modifier.fillMaxWidth()) {
            PersianNames.WEEKDAYS_SHORT.forEachIndexed { i, label ->
                Text(
                    text = label,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (i == 6) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        cells.forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                for (i in 0 until 7) {
                    val day = week.getOrNull(i)
                    Box(Modifier.weight(1f).aspectRatio(1f), contentAlignment = Alignment.Center) {
                        if (day != null) DayCell(day, isToday = day == today, isSelected = day == selected, onClick = { onSelect(day) })
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(day: LocalDate, isToday: Boolean, isSelected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val textColor = when {
        isSelected -> colors.onPrimary
        day.dayOfWeek == DayOfWeek.FRIDAY -> colors.error
        else -> colors.onSurface
    }
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(if (isSelected) colors.primary else Color.Transparent)
            .then(if (isToday && !isSelected) Modifier.border(1.dp, colors.primary, CircleShape) else Modifier)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(PersianDigits.format(day.toJalali().day), color = textColor, style = MaterialTheme.typography.bodyMedium)
    }
}
