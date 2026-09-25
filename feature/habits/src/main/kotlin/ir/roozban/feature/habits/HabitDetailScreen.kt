package ir.roozban.feature.habits

import androidx.compose.ui.text.font.FontWeight
import ir.roozban.core.designsystem.theme.Role
import ir.roozban.core.designsystem.theme.Roozban
import ir.roozban.core.designsystem.components.IconBadge
import ir.roozban.core.designsystem.components.AppCard
import ir.roozban.core.designsystem.components.RoozbanTopBar
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.roozban.core.calendar.JalaliDate
import ir.roozban.core.calendar.PersianDigits
import ir.roozban.core.calendar.PersianNames
import ir.roozban.core.designsystem.R as DsR
import ir.roozban.core.designsystem.theme.TagColors
import ir.roozban.core.domain.HabitDayStatus
import ir.roozban.core.domain.StreakUnit
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HabitDetailScreen(onBack: () -> Unit, viewModel: HabitDetailViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var menu by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    LaunchedEffect(state.deleted) { if (state.deleted) onBack() }
    val habit = state.habit
    val stats = state.stats

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            RoozbanTopBar(
                title = { Text(habit?.name.orEmpty()) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(painterResource(DsR.drawable.ic_arrow_back), stringResource(R.string.habits_back)) }
                },
                actions = {
                    if (habit != null) {
                        IconButton(onClick = { editing = true }) { Icon(painterResource(DsR.drawable.ic_edit), stringResource(R.string.habits_edit)) }
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(painterResource(DsR.drawable.ic_more_vert), stringResource(R.string.habits_more)) }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(if (habit.archived) R.string.habits_unarchive else R.string.habits_archive)) },
                                    onClick = { menu = false; viewModel.setArchived(!habit.archived) },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.habits_delete), color = MaterialTheme.colorScheme.error) },
                                    onClick = { menu = false; deleting = true },
                                )
                            }
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (habit == null || stats == null) return@Scaffold
        val color = TagColors.color(habit.color)
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp)) {
            Text(scheduleLabel(habit.schedule), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val unit = if (stats.unit == StreakUnit.DAY) R.string.habits_streak_days else R.string.habits_streak_weeks
                StatTile(stringResource(R.string.habits_current), stringResource(unit, PersianDigits.format(stats.current)), Roozban.colors.streak, DsR.drawable.ic_fire, Modifier.weight(1f))
                StatTile(stringResource(R.string.habits_best), stringResource(unit, PersianDigits.format(stats.best)), Roozban.colors.focus, DsR.drawable.ic_event_star, Modifier.weight(1f))
                StatTile(
                    stringResource(R.string.habits_rate),
                    stringResource(R.string.habits_percent, PersianDigits.format((stats.rate30 * 100).roundToInt())),
                    Roozban.colors.success,
                    DsR.drawable.ic_bar_chart,
                    Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(DsR.drawable.ic_snowflake), null, tint = Roozban.colors.info.color, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.habits_freezes, PersianDigits.format(stats.freezes)), style = MaterialTheme.typography.labelLarge)
            }
            Text(stringResource(R.string.habits_freeze_help), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
            MonthCalendar(state.month, state.cells, habit.targetPerDay, color, viewModel::previousMonth, viewModel::nextMonth, viewModel::tap)
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.habits_tap_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Legend(color)
        }
    }

    if (editing && habit != null) {
        HabitEditorSheet(HabitDraft.of(habit), onSave = { viewModel.save(it); editing = false }, onDismiss = { editing = false })
    }
    if (deleting && habit != null) {
        AlertDialog(
            onDismissRequest = { deleting = false },
            text = { Text(stringResource(R.string.habits_delete_confirm, habit.name)) },
            confirmButton = {
                TextButton(onClick = { deleting = false; viewModel.delete() }) {
                    Text(stringResource(R.string.habits_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { deleting = false }) { Text(stringResource(R.string.habits_cancel)) } },
        )
    }
}

@Composable
private fun StatTile(label: String, value: String, role: Role, icon: Int, modifier: Modifier) {
    AppCard(modifier = modifier) {
        Column(Modifier.padding(12.dp)) {
            IconBadge(icon, role, size = 32.dp)
            Spacer(Modifier.height(8.dp))
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = role.color)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun MonthCalendar(
    month: JalaliDate,
    cells: List<HabitDayCell?>,
    target: Int,
    color: Color,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onTap: (java.time.LocalDate) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        // RTL: the previous month is on the right.
        IconButton(onClick = onPrevious) { Icon(painterResource(DsR.drawable.ic_chevron_right), stringResource(R.string.habits_previous_month)) }
        Text(
            PersianNames.jalaliMonth(month.month) + " " + PersianDigits.format(month.year),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        IconButton(onClick = onNext) { Icon(painterResource(DsR.drawable.ic_chevron_left), stringResource(R.string.habits_next_month)) }
    }
    Row(Modifier.fillMaxWidth()) {
        PersianNames.WEEKDAYS_SHORT.forEach {
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    cells.chunked(7).forEach { week ->
        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
            for (i in 0 until 7) {
                val cell = week.getOrNull(i)
                Box(Modifier.weight(1f).aspectRatio(1f), contentAlignment = Alignment.Center) {
                    if (cell != null) {
                        DayDot(
                            cell,
                            target,
                            color,
                            size = 38.dp,
                            label = PersianDigits.format(JalaliDate.from(cell.date).day),
                            onClick = { onTap(cell.date) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Legend(color: Color) {
    val scheme = MaterialTheme.colorScheme
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        LegendItem(color, stringResource(R.string.habits_legend_done))
        LegendItem(Roozban.colors.info.container, stringResource(R.string.habits_legend_frozen))
        LegendItem(Roozban.colors.error.container, stringResource(R.string.habits_legend_missed))
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        DayDot(
            HabitDayCell(java.time.LocalDate.MIN, HabitDayStatus.DONE, 1, isToday = false, isFuture = false),
            1,
            color,
            size = 14.dp,
            label = null,
            onClick = null,
        )
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}
