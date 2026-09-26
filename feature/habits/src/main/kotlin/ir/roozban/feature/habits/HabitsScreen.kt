package ir.roozban.feature.habits

import ir.roozban.core.designsystem.components.AppMenuButton
import ir.roozban.core.designsystem.components.AssistantAction
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.ui.text.font.FontWeight
import ir.roozban.core.designsystem.theme.Roozban
import ir.roozban.core.designsystem.components.EmptyState
import ir.roozban.core.designsystem.components.roleOf
import ir.roozban.core.designsystem.components.IconBadge
import ir.roozban.core.designsystem.components.AppCard
import ir.roozban.core.designsystem.components.RoozbanTopBar
import ir.roozban.core.designsystem.components.RoozbanFab
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.roozban.core.calendar.JalaliDate
import ir.roozban.core.calendar.PersianDigits
import ir.roozban.core.calendar.PersianNames
import ir.roozban.core.designsystem.R as DsR
import ir.roozban.core.designsystem.theme.TagColors
import ir.roozban.core.domain.Access
import ir.roozban.core.domain.HabitStats
import ir.roozban.core.domain.StreakUnit
import ir.roozban.core.model.Habit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HabitsScreen(
    onOpenHabit: (String) -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: HabitsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<HabitDraft?>(null) }
    var deleting by remember { mutableStateOf<Habit?>(null) }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            RoozbanTopBar(
                title = {
                    Column {
                        Text(stringResource(R.string.habits_title))
                        if (state.dueToday > 0) {
                            Text(
                                stringResource(R.string.habits_today_progress, PersianDigits.format(state.doneToday), PersianDigits.format(state.dueToday)),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = { AppMenuButton() },
                actions = {
                    AssistantAction()
                    IconButton(onClick = onOpenSettings) {
                        Icon(painterResource(DsR.drawable.ic_settings), stringResource(R.string.habits_settings))
                    }
                },
            )
        },
        floatingActionButton = {
            if (state.access == Access.FULL) {
                RoozbanFab(
                    onClick = { editing = HabitDraft(color = state.active.size % TagColors.count) },
                    icon = { Icon(painterResource(DsR.drawable.ic_add), null) },
                    text = { Text(stringResource(R.string.habits_new)) },
                )
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 96.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (state.access != Access.FULL) {
                item(key = "locked") { Text(stringResource(R.string.habits_locked), style = MaterialTheme.typography.bodySmall) }
            }
            items(state.active, key = { it.habit.id }) { card ->
                HabitRow(
                    card,
                    onOpen = { onOpenHabit(card.habit.id) },
                    onTap = { date -> viewModel.tap(card.habit, date) },
                    onEdit = { editing = HabitDraft.of(card.habit) },
                    onArchive = { viewModel.setArchived(card.habit, true) },
                    onDelete = { deleting = card.habit },
                )
            }
            if (state.archived.isNotEmpty()) {
                item(key = "archived-title") {
                    Text(
                        stringResource(R.string.habits_archived),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 16.dp, start = 4.dp),
                    )
                }
                items(state.archived, key = { it.habit.id }) { card ->
                    HabitRow(
                        card,
                        onOpen = { onOpenHabit(card.habit.id) },
                        onTap = null,
                        onEdit = { editing = HabitDraft.of(card.habit) },
                        onArchive = { viewModel.setArchived(card.habit, false) },
                        onDelete = { deleting = card.habit },
                    )
                }
            }
            if (!state.loading && state.active.isEmpty() && state.archived.isEmpty()) {
                item(key = "empty") {
                    EmptyState(DsR.drawable.ic_fire, stringResource(R.string.habits_empty_title), stringResource(R.string.habits_empty_body), Roozban.colors.streak)
                }
            }
        }
    }

    editing?.let { draft ->
        HabitEditorSheet(initial = draft, onSave = { viewModel.save(it); editing = null }, onDismiss = { editing = null })
    }
    deleting?.let { habit ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            text = { Text(stringResource(R.string.habits_delete_confirm, habit.name)) },
            confirmButton = {
                TextButton(onClick = { viewModel.delete(habit); deleting = null }) {
                    Text(stringResource(R.string.habits_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.habits_cancel)) } },
        )
    }
}

@Composable
private fun HabitRow(
    card: HabitCard,
    onOpen: () -> Unit,
    onTap: ((java.time.LocalDate) -> Unit)?,
    onEdit: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val color = TagColors.color(card.habit.color)
    val doneToday = card.week.firstOrNull { it.isToday }?.status == ir.roozban.core.domain.HabitDayStatus.DONE
    AppCard(
        onClick = onOpen,
        accent = color,
        container = if (doneToday) Roozban.colors.completed.container.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(start = 12.dp, end = 4.dp, top = 10.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(DsR.drawable.ic_habit, roleOf(color), size = 40.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(card.habit.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(scheduleLabel(card.habit.schedule), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                StreakBadge(card.stats)
                Box {
                    IconButton(onClick = { menu = true }) { Icon(painterResource(DsR.drawable.ic_more_vert), stringResource(R.string.habits_more)) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.habits_edit)) }, onClick = { menu = false; onEdit() })
                        DropdownMenuItem(
                            text = { Text(stringResource(if (card.habit.archived) R.string.habits_unarchive else R.string.habits_archive)) },
                            onClick = { menu = false; onArchive() },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.habits_delete), color = MaterialTheme.colorScheme.error) },
                            onClick = { menu = false; onDelete() },
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth().padding(end = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                card.week.forEachIndexed { i, cell ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(PersianNames.WEEKDAYS_SHORT[i], style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(2.dp))
                        DayDot(
                            cell,
                            card.habit.targetPerDay,
                            color,
                            size = 32.dp,
                            label = PersianDigits.format(JalaliDate.from(cell.date).day),
                            onClick = onTap?.let { tap -> { tap(cell.date) } },
                        )
                    }
                }
            }
        }
    }
}

/** Streak as an amber flame pill (grey when broken), freezes as a small blue snowflake count. */
@Composable
internal fun StreakBadge(stats: HabitStats) {
    val streak = Roozban.colors.streak
    val active = stats.current > 0
    Row(verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier
                .clip(CircleShape)
                .background(if (active) streak.container else MaterialTheme.colorScheme.surfaceContainerHighest)
                .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(painterResource(DsR.drawable.ic_fire), null, tint = if (active) streak.color else MaterialTheme.colorScheme.outline, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(3.dp))
            Text(
                stringResource(
                    if (stats.unit == StreakUnit.DAY) R.string.habits_streak_days else R.string.habits_streak_weeks,
                    PersianDigits.format(stats.current),
                ),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = if (active) streak.onContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (stats.freezes > 0) {
            Spacer(Modifier.width(6.dp))
            Icon(painterResource(DsR.drawable.ic_snowflake), null, tint = Roozban.colors.info.color, modifier = Modifier.size(16.dp))
            Text(PersianDigits.format(stats.freezes), style = MaterialTheme.typography.labelMedium, color = Roozban.colors.info.color)
        }
    }
}
