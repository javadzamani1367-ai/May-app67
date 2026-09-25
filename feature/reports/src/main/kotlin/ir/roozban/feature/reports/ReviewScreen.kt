package ir.roozban.feature.reports

import androidx.compose.ui.text.font.FontWeight
import ir.roozban.core.designsystem.theme.Roozban
import ir.roozban.core.designsystem.components.RoozbanTopBar
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.roozban.core.calendar.PersianDateFormatter
import ir.roozban.core.calendar.PersianDigits
import ir.roozban.core.calendar.PersianNames
import ir.roozban.core.calendar.PersianWeek
import ir.roozban.core.designsystem.R as DsR
import ir.roozban.core.designsystem.theme.TagColors
import ir.roozban.core.domain.DailyReview
import ir.roozban.core.domain.ReviewKind
import ir.roozban.core.domain.WeeklyReview
import ir.roozban.core.model.Task
import ir.roozban.core.model.TaskDue
import ir.roozban.core.model.UserSettings
import ir.roozban.core.ui.JalaliDatePickerDialog
import ir.roozban.core.ui.TimePickerDialog
import java.time.LocalDate
import java.time.LocalTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReviewScreen(onBack: () -> Unit, viewModel: ReviewViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var picking by remember { mutableStateOf<Task?>(null) }
    val actions = TaskActions(
        onTomorrow = { viewModel.moveTo(it, state.today.plusDays(1)) },
        onPick = { picking = it },
        onDone = viewModel::complete,
        onDelete = viewModel::delete,
    )
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            RoozbanTopBar(
                title = { Text(stringResource(if (state.kind == ReviewKind.DAILY) R.string.review_daily_title else R.string.review_weekly_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(painterResource(DsR.drawable.ic_arrow_back), stringResource(R.string.reports_back)) }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            state.daily?.let { DailyBody(it, state.today, actions, viewModel) }
            state.weekly?.let { WeeklyBody(it, state.today, actions, viewModel) }
            ReminderSection(state.kind, state.settings, viewModel)
            Spacer(Modifier.height(32.dp))
        }
    }
    picking?.let { task ->
        JalaliDatePickerDialog(
            initial = task.due?.date,
            today = state.today,
            onConfirm = { date -> if (date != null) viewModel.moveTo(task, date); picking = null },
            onDismiss = { picking = null },
        )
    }
}

private class TaskActions(
    val onTomorrow: (Task) -> Unit,
    val onPick: (Task) -> Unit,
    val onDone: (Task) -> Unit,
    val onDelete: (Task) -> Unit,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DailyBody(review: DailyReview, today: LocalDate, actions: TaskActions, viewModel: ReviewViewModel) {
    Section(stringResource(R.string.review_done_today) + " · " + stringResource(R.string.review_done_count, PersianDigits.format(review.completed.size)), DsR.drawable.ic_check, Roozban.colors.success.color) {
        if (review.completed.isEmpty()) {
            Muted(stringResource(R.string.review_nothing_done))
        } else {
            review.completed.forEach {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
                    Icon(painterResource(DsR.drawable.ic_check), null, tint = Roozban.colors.completed.color, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(it.title, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (review.focusMinutes > 0) {
            Spacer(Modifier.height(8.dp))
            Muted(stringResource(R.string.review_focus_today, formatMinutes(review.focusMinutes)))
        }
    }
    LeftoverSection(stringResource(R.string.review_leftover), review.leftover, today, actions, viewModel)
    if (review.habits.isNotEmpty()) {
        Section(stringResource(R.string.review_habits), DsR.drawable.ic_fire, Roozban.colors.streak.color) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                review.habits.forEach { h ->
                    FilterChip(
                        selected = !h.due,
                        onClick = { viewModel.tapHabit(h.habit) },
                        label = {
                            val count = if (h.habit.targetPerDay > 1) " ${PersianDigits.format(h.count)}/${PersianDigits.format(h.habit.targetPerDay)}" else ""
                            Text(h.habit.name + count)
                        },
                        leadingIcon = {
                            val color = TagColors.color(h.habit.color)
                            androidx.compose.foundation.Canvas(Modifier.size(10.dp)) { drawCircle(color) }
                        },
                    )
                }
            }
        }
    }
    Section(stringResource(R.string.review_tomorrow_plan), DsR.drawable.ic_upcoming, Roozban.colors.info.color) {
        if (review.tomorrow.isEmpty()) {
            Muted(stringResource(R.string.review_tomorrow_empty))
        } else {
            review.tomorrow.forEach { t ->
                Row(Modifier.padding(vertical = 2.dp)) {
                    Text(t.title, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    (t.due as? TaskDue.At)?.let { Muted(PersianDateFormatter.time(it.time)) }
                }
            }
        }
    }
}

@Composable
private fun WeeklyBody(review: WeeklyReview, today: LocalDate, actions: TaskActions, viewModel: ReviewViewModel) {
    val r = review.report
    val p = review.previous
    Spacer(Modifier.height(8.dp))
    Text(formatPeriod(r.period, wholeMonth = false), style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatCard(stringResource(R.string.reports_completed), PersianDigits.format(r.completed), formatChange(r.completed, p.completed), Modifier.weight(1f), role = Roozban.colors.success, icon = DsR.drawable.ic_check)
        StatCard(stringResource(R.string.reports_tracked), formatMinutes(r.trackedMinutes), formatChange(r.trackedMinutes, p.trackedMinutes), Modifier.weight(1f), role = Roozban.colors.info, icon = DsR.drawable.ic_schedule)
    }
    Section(stringResource(R.string.reports_completed_per_day), DsR.drawable.ic_check, Roozban.colors.success.color) {
        BarChart(r.days.map { it.completed }, r.days.map { PersianNames.WEEKDAYS_SHORT[PersianWeek.indexOf(it.date.dayOfWeek)] },
            Roozban.colors.success.color, height = 100.dp, highlight = r.days.indexOfFirst { it.date == today }.takeIf { it >= 0 })
    }
    if (review.habits.isNotEmpty()) {
        Section(stringResource(R.string.reports_habits), DsR.drawable.ic_fire, Roozban.colors.streak.color) {
            review.habits.forEach { hw ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                    Text(hw.habit.name, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(120.dp))
                    LinearProgressIndicator(
                        progress = { if (hw.expected == 0) 0f else (hw.done.toFloat() / hw.expected).coerceAtMost(1f) },
                        color = TagColors.color(hw.habit.color),
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                    )
                    Muted(stringResource(R.string.review_habit_week, PersianDigits.format(hw.done), PersianDigits.format(hw.expected)))
                }
            }
        }
    }
    LeftoverSection(stringResource(R.string.review_overdue), review.overdue, today, actions, viewModel)
    Section(stringResource(R.string.review_next_week), DsR.drawable.ic_upcoming, Roozban.colors.info.color) {
        BarChart(review.nextWeek.map { it.second }, review.nextWeek.map { PersianNames.WEEKDAYS_SHORT[PersianWeek.indexOf(it.first.dayOfWeek)] },
            Roozban.colors.info.color, height = 80.dp)
    }
}

@Composable
private fun LeftoverSection(title: String, tasks: List<Task>, today: LocalDate, actions: TaskActions, viewModel: ReviewViewModel) {
    Section(title, DsR.drawable.ic_warning, if (tasks.isEmpty()) Roozban.colors.success.color else Roozban.colors.error.color) {
        if (tasks.isEmpty()) {
            Muted(stringResource(R.string.review_leftover_empty))
            return@Section
        }
        tasks.forEach { t -> LeftoverRow(t, today, actions) }
        if (tasks.size > 1) {
            TextButton(onClick = viewModel::moveAllToTomorrow) { Text(stringResource(R.string.review_all_tomorrow)) }
        }
    }
}

@Composable
private fun LeftoverRow(task: Task, today: LocalDate, actions: TaskActions) {
    var menu by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(task.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
            task.due?.let { Muted(PersianDateFormatter.relativeDay(it.date, today)) }
        }
        TextButton(onClick = { actions.onTomorrow(task) }) { Text(stringResource(R.string.review_tomorrow), color = Roozban.colors.info.color, fontWeight = FontWeight.Bold) }
        Box {
            IconButton(onClick = { menu = true }) { Icon(painterResource(DsR.drawable.ic_more_vert), stringResource(R.string.review_more)) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.review_pick_date)) }, onClick = { menu = false; actions.onPick(task) })
                DropdownMenuItem(text = { Text(stringResource(R.string.review_done)) }, onClick = { menu = false; actions.onDone(task) })
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.review_delete), color = MaterialTheme.colorScheme.error) },
                    onClick = { menu = false; actions.onDelete(task) },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReminderSection(kind: ReviewKind, settings: UserSettings, viewModel: ReviewViewModel) {
    var pickingTime by remember { mutableStateOf(false) }
    Section(stringResource(R.string.review_reminder), DsR.drawable.ic_notifications, MaterialTheme.colorScheme.primary) {
        val time = if (kind == ReviewKind.DAILY) settings.dailyReviewTime else settings.weeklyReviewTime
        if (kind == ReviewKind.WEEKLY) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (0 until 7).map(PersianWeek::dayAt).forEach { day ->
                    FilterChip(
                        selected = settings.weeklyReviewDay == day,
                        onClick = { viewModel.setWeeklyReminder(day, time ?: LocalTime.of(18, 0)) },
                        label = { Text(PersianNames.weekday(day)) },
                    )
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(if (kind == ReviewKind.DAILY) R.string.review_reminder_daily else R.string.review_reminder_weekly),
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { pickingTime = true }) {
                Text(time?.let { PersianDateFormatter.time(it) } ?: stringResource(R.string.review_reminder_off))
            }
            if (time != null) {
                TextButton(onClick = {
                    if (kind == ReviewKind.DAILY) viewModel.setDailyReminder(null) else viewModel.setWeeklyReminder(settings.weeklyReviewDay, null)
                }) { Text(stringResource(R.string.review_reminder_off)) }
            }
        }
        if (pickingTime) {
            TimePickerDialog(
                initial = time ?: if (kind == ReviewKind.DAILY) LocalTime.of(21, 30) else LocalTime.of(18, 0),
                onConfirm = {
                    if (kind == ReviewKind.DAILY) viewModel.setDailyReminder(it) else viewModel.setWeeklyReminder(settings.weeklyReviewDay, it)
                    pickingTime = false
                },
                onDismiss = { pickingTime = false },
            )
        }
    }
}

@Composable
private fun Muted(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
