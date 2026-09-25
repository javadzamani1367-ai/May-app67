package ir.roozban.feature.tasks.calendar

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ir.roozban.core.calendar.City
import ir.roozban.core.calendar.HijriDates
import ir.roozban.core.calendar.IranCities
import ir.roozban.core.calendar.JalaliDate
import ir.roozban.core.calendar.PersianDateFormatter
import ir.roozban.core.calendar.PersianDigits
import ir.roozban.core.calendar.toJalali
import ir.roozban.core.designsystem.icons.eventIcon
import ir.roozban.core.designsystem.icons.eventKindName
import ir.roozban.core.designsystem.theme.TagColors
import ir.roozban.core.domain.EventUseCases
import ir.roozban.core.model.EventCalendar
import ir.roozban.core.model.EventKind
import ir.roozban.core.model.PersonalEvent
import ir.roozban.core.ui.JalaliDatePickerDialog
import ir.roozban.core.ui.TimePickerDialog
import ir.roozban.feature.tasks.R
import java.time.LocalDate

// ------------------------------------------------------------------ personal event editor

/** The Jalali date of an existing event's next occurrence, to prefill the editor. */
internal fun EventDraft.Companion.of(event: PersonalEvent, date: LocalDate) = EventDraft(
    id = event.id,
    title = event.title,
    kind = event.kind,
    color = event.color,
    date = date,
    calendar = event.calendar,
    knownYear = event.year != null,
    remindDaysBefore = event.remindDaysBefore,
    reminderTime = event.reminderTime,
    notes = event.notes,
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun EventEditorSheet(
    initial: EventDraft,
    today: LocalDate,
    hijriOffset: Int,
    onSave: (EventDraft) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    var draft by remember { mutableStateOf(initial) }
    var pickingDate by remember { mutableStateOf(false) }
    var pickingTime by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
            Text(stringResource(if (draft.id == null) R.string.event_new else R.string.event_edit), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EventKind.entries.forEach { kind ->
                    FilterChip(
                        selected = draft.kind == kind,
                        onClick = { draft = draft.copy(kind = kind) },
                        label = { Text(eventKindName(kind)) },
                        leadingIcon = { Icon(painterResource(eventIcon(kind)), null, modifier = Modifier.size(18.dp)) },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = draft.title,
                onValueChange = { draft = draft.copy(title = it) },
                label = { Text(stringResource(R.string.event_title_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                repeat(TagColors.count) { i ->
                    Box(
                        Modifier.size(30.dp).clip(CircleShape).background(TagColors.color(i))
                            .then(if (draft.color == i) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                            .clickable { draft = draft.copy(color = i) },
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = { pickingDate = true }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.event_date, PersianDateFormatter.dayMonthYear(draft.date.toJalali())))
            }
            Text(
                listOfNotNull(PersianDateFormatter.gregorian(draft.date), PersianDateFormatter.hijri(draft.date, hijriOffset)).joinToString("  |  "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.event_repeat_by), style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(4.dp))
            val calendars = listOf(EventCalendar.JALALI to R.string.event_cal_jalali, EventCalendar.GREGORIAN to R.string.event_cal_gregorian, EventCalendar.HIJRI to R.string.event_cal_hijri)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                calendars.forEachIndexed { i, (cal, label) ->
                    SegmentedButton(
                        selected = draft.calendar == cal,
                        onClick = { draft = draft.copy(calendar = cal) },
                        shape = SegmentedButtonDefaults.itemShape(i, calendars.size),
                    ) { Text(stringResource(label)) }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.event_known_year))
                    Text(stringResource(R.string.event_known_year_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = draft.knownYear, onCheckedChange = { draft = draft.copy(knownYear = it) })
            }
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.event_remind), style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(0 to R.string.event_remind_day, 1 to R.string.event_remind_1, 3 to R.string.event_remind_3, 7 to R.string.event_remind_7, 30 to R.string.event_remind_30)
                    .forEach { (days, label) ->
                        val on = days in draft.remindDaysBefore
                        FilterChip(
                            selected = on,
                            onClick = { draft = draft.copy(remindDaysBefore = if (on) draft.remindDaysBefore - days else draft.remindDaysBefore + days) },
                            label = { Text(stringResource(label)) },
                        )
                    }
            }
            if (draft.remindDaysBefore.isNotEmpty()) {
                TextButton(onClick = { pickingTime = true }) {
                    Text(stringResource(R.string.event_remind_at, PersianDateFormatter.time(draft.reminderTime)))
                }
            }
            OutlinedTextField(
                value = draft.notes,
                onValueChange = { draft = draft.copy(notes = it) },
                label = { Text(stringResource(R.string.event_notes)) },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) { Text(stringResource(R.string.event_delete), color = MaterialTheme.colorScheme.error) }
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_cancel)) }
                TextButton(onClick = { onSave(draft) }, enabled = draft.title.isNotBlank()) { Text(stringResource(R.string.event_save)) }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
    if (pickingDate) {
        JalaliDatePickerDialog(
            initial = draft.date,
            today = today,
            onConfirm = { d -> if (d != null) draft = draft.copy(date = d); pickingDate = false },
            onDismiss = { pickingDate = false },
        )
    }
    if (pickingTime) {
        TimePickerDialog(initial = draft.reminderTime, onConfirm = { draft = draft.copy(reminderTime = it); pickingTime = false }, onDismiss = { pickingTime = false })
    }
}

// ------------------------------------------------------------------ city picker

@Composable
internal fun CityPickerDialog(current: City?, onPick: (String?) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.calendar_pick_city)) },
        text = {
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                items(IranCities.all.sortedBy { it.name }, key = { it.id }) { city ->
                    Text(
                        city.name,
                        color = if (city.id == current?.id) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.fillMaxWidth().clickable { onPick(city.id) }.padding(vertical = 12.dp),
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_cancel)) } },
        dismissButton = if (current != null) {
            { TextButton(onClick = { onPick(null) }) { Text(stringResource(R.string.calendar_prayer_off)) } }
        } else {
            null
        },
    )
}

// ------------------------------------------------------------------ date converter

/**
 * Converts a date between the Jalali, Gregorian and Hijri calendars and tells how far it is from
 * today («۱۲۰ روز دیگر»).
 */
@Composable
internal fun DateConverterDialog(today: LocalDate, hijriOffset: Int, onGoTo: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    var source by remember { mutableStateOf(EventCalendar.JALALI) }
    val t = EventUseCases.parts(today, source, hijriOffset)
    var year by remember(source) { mutableStateOf(PersianDigits.format(t?.first ?: 1405)) }
    var month by remember(source) { mutableStateOf(PersianDigits.format(t?.second ?: 1)) }
    var day by remember(source) { mutableStateOf(PersianDigits.format(t?.third ?: 1)) }
    fun num(s: String) = PersianDigits.toAscii(s).trim().toIntOrNull()
    val date: LocalDate? = run {
        val y = num(year) ?: return@run null
        val m = num(month)?.takeIf { it in 1..12 } ?: return@run null
        val d = num(day)?.takeIf { it in 1..31 } ?: return@run null
        runCatching {
            when (source) {
                EventCalendar.JALALI -> if (d <= JalaliDate.monthLength(y, m)) JalaliDate.of(y, m, d).toLocalDate() else null
                EventCalendar.GREGORIAN -> LocalDate.of(y, m, d)
                EventCalendar.HIJRI -> HijriDates.toLocalDate(y, m, d, hijriOffset)?.takeIf { HijriDates.from(it, hijriOffset)?.day == d }
            }
        }.getOrNull()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.converter_title)) },
        text = {
            Column {
                val calendars = listOf(EventCalendar.JALALI to R.string.event_cal_jalali, EventCalendar.GREGORIAN to R.string.event_cal_gregorian, EventCalendar.HIJRI to R.string.event_cal_hijri)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    calendars.forEachIndexed { i, (cal, label) ->
                        SegmentedButton(selected = source == cal, onClick = { source = cal }, shape = SegmentedButtonDefaults.itemShape(i, calendars.size)) {
                            Text(stringResource(label))
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField(stringResource(R.string.converter_day), day, Modifier.weight(1f)) { day = it }
                    NumberField(stringResource(R.string.converter_month), month, Modifier.weight(1f)) { month = it }
                    NumberField(stringResource(R.string.converter_year), year, Modifier.weight(1.4f)) { year = it }
                }
                Spacer(Modifier.height(12.dp))
                if (date == null) {
                    Text(stringResource(R.string.converter_invalid), color = MaterialTheme.colorScheme.error)
                } else {
                    Text(PersianDateFormatter.fullDate(date.toJalali()), style = MaterialTheme.typography.titleMedium)
                    Text(PersianDateFormatter.gregorian(date))
                    PersianDateFormatter.hijri(date, hijriOffset)?.let { Text(it) }
                    val diff = date.toEpochDay() - today.toEpochDay()
                    Text(
                        when {
                            diff == 0L -> stringResource(R.string.calendar_today)
                            diff > 0 -> stringResource(R.string.converter_days_after, PersianDigits.format(diff))
                            else -> stringResource(R.string.converter_days_before, PersianDigits.format(-diff))
                        },
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { date?.let(onGoTo) }, enabled = date != null) { Text(stringResource(R.string.converter_show)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_cancel)) } },
    )
}

@Composable
private fun NumberField(label: String, value: String, modifier: Modifier, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(PersianDigits.toPersian(PersianDigits.toAscii(it).filter(Char::isDigit).take(4))) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}

