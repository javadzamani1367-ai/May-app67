package ir.roozban.feature.tools

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.roozban.core.calendar.PersianDateFormatter
import ir.roozban.core.calendar.PersianDigits
import ir.roozban.core.calendar.PersianNames
import ir.roozban.core.calendar.toJalali
import ir.roozban.core.designsystem.R as DsR
import ir.roozban.core.designsystem.components.AppCard
import ir.roozban.core.designsystem.components.RoozbanTopBar
import ir.roozban.core.designsystem.components.SectionTitle
import ir.roozban.core.designsystem.theme.Roozban
import ir.roozban.core.domain.AttendanceDay
import ir.roozban.core.domain.AttendanceMonth
import ir.roozban.core.domain.AttendanceReport
import ir.roozban.core.model.AttendanceEntry
import ir.roozban.core.model.AttendanceKind
import ir.roozban.core.model.AttendanceSettings
import ir.roozban.core.model.LeaveType
import ir.roozban.core.ui.JalaliDatePickerDialog
import ir.roozban.core.ui.TimePickerDialog
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** «ثبت تردد»: check in and out, missions and leave, with the month's report. */
@Composable
internal fun AttendanceScreen(onBack: () -> Unit, viewModel: AttendanceViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    var editing by remember { mutableStateOf<EntryDraft?>(null) }
    var settingsOpen by remember { mutableStateOf(false) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) context.contentResolver.openOutputStream(uri)?.use { it.write(viewModel.csv()) }
    }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { m ->
            val r = snackbar.showSnackbar(m.text, actionLabel = m.undo?.let { "بازگردانی" }, duration = SnackbarDuration.Short)
            if (r == SnackbarResult.ActionPerformed) m.undo?.let(viewModel::undo)
        }
    }
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            RoozbanTopBar(
                title = { Text("ثبت تردد") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(painterResource(DsR.drawable.ic_arrow_back), "بازگشت") } },
                actions = {
                    IconButton(onClick = { export.launch("attendance-${state.year}-${state.month}.csv") }) {
                        Icon(painterResource(DsR.drawable.ic_download), "خروجی اکسل")
                    }
                    IconButton(onClick = { settingsOpen = true }) { Icon(painterResource(DsR.drawable.ic_settings), "ساعت کاری") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { TodayCard(state, onIn = viewModel::checkIn, onOut = viewModel::checkOut) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { editing = EntryDraft.new(AttendanceKind.MISSION, state.today) }, modifier = Modifier.weight(1f)) { Text("مأموریت") }
                    OutlinedButton(onClick = { editing = EntryDraft.new(AttendanceKind.LEAVE, state.today) }, modifier = Modifier.weight(1f)) { Text("مرخصی") }
                    OutlinedButton(onClick = { editing = EntryDraft.new(AttendanceKind.WORK, state.today) }, modifier = Modifier.weight(1f)) { Text("ثبت دستی") }
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { viewModel.shiftMonth(-1) }) { Icon(painterResource(DsR.drawable.ic_chevron_right), "ماه قبل") }
                    Text(
                        "${PersianNames.jalaliMonth(state.month)} ${PersianDigits.format(state.year)}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { viewModel.shiftMonth(1) }) { Icon(painterResource(DsR.drawable.ic_chevron_left), "ماه بعد") }
                }
            }
            state.report?.let { r -> item { MonthSummary(r, state.today) } }
            val days = state.report?.days.orEmpty().filter { d ->
                d.present || d.leaveDay != null || d.leave.isNotEmpty() || d.missingCheckOut
            }.reversed()
            if (days.isEmpty()) {
                item { Text("در این ماه چیزی ثبت نشده.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(8.dp)) }
            } else {
                item { SectionTitle("روزها") }
            }
            items(days, key = { it.date.toEpochDay() }) { day ->
                DayCard(day, state.entries.filter { it.touches(day.date) }) { editing = EntryDraft.of(it) }
            }
        }
    }
    editing?.let { draft ->
        EntryDialog(
            draft = draft,
            today = state.today,
            onSave = { d ->
                editing = null
                val (start, end) = d.times()
                if (d.existing != null) {
                    viewModel.save(d.existing.copy(kind = d.kind, start = start, end = end, allDay = d.allDay, leaveType = d.leaveType.takeIf { d.kind == AttendanceKind.LEAVE }, note = d.note.trim()))
                } else {
                    viewModel.create(d.kind, start, end, d.allDay, d.leaveType, d.note)
                }
            },
            onDelete = draft.existing?.let { e -> { editing = null; viewModel.delete(e) } },
            onDismiss = { editing = null },
        )
    }
    if (settingsOpen) {
        ScheduleDialog(state.settings, onSave = { settingsOpen = false; viewModel.setSettings(it) }, onDismiss = { settingsOpen = false })
    }
}

private fun AttendanceEntry.touches(date: LocalDate): Boolean {
    val last = (end ?: start).toLocalDate()
    return !date.isBefore(start.toLocalDate()) && !date.isAfter(if (end == null && !allDay) date else last)
}

private fun hm(minutes: Int): String = PersianDigits.toPersian(AttendanceReport.hours(minutes))

private fun time(t: LocalDateTime): String = PersianDateFormatter.time(t.toLocalTime())

@Composable
private fun TodayCard(state: AttendanceUiState, onIn: () -> Unit, onOut: () -> Unit) {
    val today = state.report?.days?.firstOrNull { it.date == state.today }
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(PersianDateFormatter.fullDate(state.today.toJalali()), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            val open = state.open
            Text(
                if (open != null) "حاضر از ساعت ${time(open.start)}" else "خارج از محل کار",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (open != null) Roozban.colors.success.color else MaterialTheme.colorScheme.onSurface,
            )
            today?.let {
                Text("کارکرد امروز: ${hm(it.worked + it.mission)}" + if (it.required > 0) " از ${hm(it.required)}" else "", style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(12.dp))
            if (open == null) {
                Button(onClick = onIn, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("ثبت ورود", style = MaterialTheme.typography.titleMedium) }
            } else {
                Button(
                    onClick = onOut,
                    colors = ButtonDefaults.buttonColors(containerColor = Roozban.colors.error.color),
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                ) { Text("ثبت خروج", style = MaterialTheme.typography.titleMedium) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MonthSummary(r: AttendanceMonth, today: LocalDate) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        FlowRow(
            Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            maxItemsInEachRow = 3,
        ) {
            Stat("کارکرد", hm(r.workedMinutes + r.missionMinutes))
            Stat("موظف", hm(r.requiredMinutes))
            Stat("اضافه‌کار", hm(r.overtimeMinutes), Roozban.colors.success.color)
            Stat("کسر کار", hm(r.shortfallMinutes(today)), Roozban.colors.error.color)
            Stat("روز حضور", PersianDigits.format(r.presentDays))
            Stat("غیبت", PersianDigits.format(r.absentDays(today)))
            Stat("مأموریت", "${PersianDigits.format(r.missionDays)} روز" + if (r.missionMinutes > 0) " + ${hm(r.missionMinutes)}" else "")
            LeaveType.entries.forEach { t ->
                val days = r.leaveDays[t] ?: 0
                val minutes = r.leaveMinutes[t] ?: 0
                if (days > 0 || minutes > 0) {
                    Stat("مرخصی ${AttendanceReport.leaveName(t)}", listOfNotNull(
                        "${PersianDigits.format(days)} روز".takeIf { days > 0 },
                        hm(minutes).takeIf { minutes > 0 },
                    ).joinToString(" + "))
                }
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface) {
    Column(Modifier.width(100.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
private fun DayCard(day: AttendanceDay, entries: List<AttendanceEntry>, onEdit: (AttendanceEntry) -> Unit) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${PersianNames.weekday(day.date.dayOfWeek)} ${PersianDateFormatter.dayMonth(day.date.toJalali())}",
                    fontWeight = FontWeight.SemiBold,
                    color = if (day.holiday) Roozban.colors.holiday else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Text(hm(day.worked + day.mission), fontWeight = FontWeight.Bold)
            }
            if (day.missingCheckOut) Text("خروج ثبت نشده", color = Roozban.colors.error.color, style = MaterialTheme.typography.labelMedium)
            entries.forEach { e ->
                Text(
                    entryText(e),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth().clickable { onEdit(e) }.padding(vertical = 4.dp),
                )
            }
        }
    }
}

private fun entryText(e: AttendanceEntry): String {
    val what = when (e.kind) {
        AttendanceKind.WORK -> "حضور"
        AttendanceKind.MISSION -> "مأموریت"
        AttendanceKind.LEAVE -> "مرخصی ${AttendanceReport.leaveName(e.leaveType ?: LeaveType.ANNUAL)}"
    }
    val span = if (e.allDay) {
        val a = PersianDateFormatter.dayMonth(e.start.toLocalDate().toJalali())
        val b = e.end?.let { PersianDateFormatter.dayMonth(it.toLocalDate().toJalali()) }
        if (b == null || b == a) "روزانه ($a)" else "روزانه از $a تا $b"
    } else {
        "${time(e.start)} تا ${e.end?.let(::time) ?: "…"}"
    }
    return "$what: $span" + if (e.note.isNotBlank()) " — ${e.note}" else ""
}

/** An entry being added or edited. */
private data class EntryDraft(
    val existing: AttendanceEntry?,
    val kind: AttendanceKind,
    val allDay: Boolean,
    val fromDate: LocalDate,
    val toDate: LocalDate,
    val fromTime: LocalTime,
    val toTime: LocalTime?,
    val leaveType: LeaveType,
    val note: String,
) {
    fun times(): Pair<LocalDateTime, LocalDateTime?> = if (allDay) {
        fromDate.atStartOfDay() to maxOf(toDate, fromDate).atStartOfDay()
    } else {
        val start = fromDate.atTime(fromTime)
        // An end before the start is on the next day (a night shift).
        start to toTime?.let { t -> fromDate.atTime(t).let { if (it.isBefore(start)) it.plusDays(1) else it } }
    }

    companion object {
        fun new(kind: AttendanceKind, today: LocalDate) = EntryDraft(
            existing = null,
            kind = kind,
            allDay = kind != AttendanceKind.WORK,
            fromDate = today,
            toDate = today,
            fromTime = LocalTime.of(8, 0),
            toTime = LocalTime.of(16, 0),
            leaveType = LeaveType.ANNUAL,
            note = "",
        )

        fun of(e: AttendanceEntry) = EntryDraft(
            existing = e,
            kind = e.kind,
            allDay = e.allDay,
            fromDate = e.start.toLocalDate(),
            toDate = (e.end ?: e.start).toLocalDate(),
            fromTime = e.start.toLocalTime(),
            toTime = e.end?.toLocalTime(),
            leaveType = e.leaveType ?: LeaveType.ANNUAL,
            note = e.note,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EntryDialog(draft: EntryDraft, today: LocalDate, onSave: (EntryDraft) -> Unit, onDelete: (() -> Unit)?, onDismiss: () -> Unit) {
    var d by remember { mutableStateOf(draft) }
    var picking by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (draft.existing == null) "ثبت تازه" else "ویرایش") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(AttendanceKind.WORK to "حضور", AttendanceKind.MISSION to "مأموریت", AttendanceKind.LEAVE to "مرخصی").forEach { (k, label) ->
                        FilterChip(selected = d.kind == k, onClick = { d = d.copy(kind = k, allDay = if (k == AttendanceKind.WORK) false else d.allDay) }, label = { Text(label) })
                    }
                }
                if (d.kind == AttendanceKind.LEAVE) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        LeaveType.entries.forEach { t ->
                            FilterChip(selected = d.leaveType == t, onClick = { d = d.copy(leaveType = t) }, label = { Text(AttendanceReport.leaveName(t)) })
                        }
                    }
                }
                if (d.kind != AttendanceKind.WORK) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("روزانه (یک یا چند روز کامل)", modifier = Modifier.weight(1f))
                        Switch(checked = d.allDay, onCheckedChange = { d = d.copy(allDay = it) })
                    }
                }
                PickRow(if (d.allDay) "از تاریخ" else "تاریخ", PersianDateFormatter.fullDate(d.fromDate.toJalali())) { picking = "fromDate" }
                if (d.allDay) {
                    PickRow("تا تاریخ", PersianDateFormatter.fullDate(d.toDate.toJalali())) { picking = "toDate" }
                } else {
                    PickRow("از ساعت", PersianDateFormatter.time(d.fromTime)) { picking = "fromTime" }
                    PickRow("تا ساعت", d.toTime?.let(PersianDateFormatter::time) ?: "هنوز خارج نشده") { picking = "toTime" }
                }
                OutlinedTextField(value = d.note, onValueChange = { d = d.copy(note = it) }, label = { Text("توضیح") }, modifier = Modifier.fillMaxWidth())
                onDelete?.let { del -> TextButton(onClick = del) { Text("حذف", color = Roozban.colors.error.color) } }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(d) }) { Text("ذخیره") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
    when (picking) {
        "fromDate", "toDate" -> JalaliDatePickerDialog(
            initial = if (picking == "fromDate") d.fromDate else d.toDate,
            today = today,
            onConfirm = { date ->
                if (date != null) {
                    d = if (picking == "fromDate") d.copy(fromDate = date, toDate = maxOf(d.toDate, date)) else d.copy(toDate = maxOf(date, d.fromDate))
                }
                picking = null
            },
            onDismiss = { picking = null },
        )
        "fromTime", "toTime" -> TimePickerDialog(
            initial = (if (picking == "fromTime") d.fromTime else d.toTime) ?: LocalTime.of(16, 0),
            onConfirm = { t ->
                d = if (picking == "fromTime") d.copy(fromTime = t) else d.copy(toTime = t)
                picking = null
            },
            onDismiss = { picking = null },
        )
    }
}

@Composable
private fun PickRow(label: String, value: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun ScheduleDialog(current: AttendanceSettings, onSave: (AttendanceSettings) -> Unit, onDismiss: () -> Unit) {
    var daily by remember { mutableStateOf(current.dailyMinutes) }
    var thursday by remember { mutableStateOf(current.thursdayMinutes) }
    var picking by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ساعت کاری موظف") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PickRow("شنبه تا چهارشنبه", hm(daily)) { picking = "daily" }
                PickRow("پنجشنبه", if (thursday == 0) "تعطیل" else hm(thursday)) { picking = "thursday" }
                Text("جمعه‌ها و تعطیلات رسمی موظفی ندارند.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onClick = { onSave(AttendanceSettings(daily, thursday)) }) { Text("ذخیره") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
    picking?.let { which ->
        val m = if (which == "daily") daily else thursday
        TimePickerDialog(
            initial = LocalTime.of(m / 60 % 24, m % 60),
            onConfirm = { t ->
                val v = t.hour * 60 + t.minute
                if (which == "daily") daily = v else thursday = v
                picking = null
            },
            onDismiss = { picking = null },
        )
    }
}
