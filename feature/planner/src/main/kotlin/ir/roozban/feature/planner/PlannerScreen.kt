package ir.roozban.feature.planner

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.roozban.core.calendar.PersianDateFormatter
import ir.roozban.core.calendar.PersianDigits
import ir.roozban.core.calendar.toJalali
import ir.roozban.core.designsystem.R as DsR
import ir.roozban.core.designsystem.components.AppCard
import ir.roozban.core.designsystem.components.EmptyState
import ir.roozban.core.designsystem.components.RoozbanTopBar
import ir.roozban.core.designsystem.components.SectionTitle
import ir.roozban.core.designsystem.components.StatusPill
import ir.roozban.core.designsystem.theme.Role
import ir.roozban.core.designsystem.theme.Roozban
import ir.roozban.core.model.PlanningSettings
import ir.roozban.core.ui.TimePickerDialog
import ir.roozban.learning.FixedBlock
import ir.roozban.learning.Placement
import ir.roozban.learning.PlacementReason
import java.time.LocalTime
import java.util.Locale

/** The proposed day as a timeline: fixed tasks stay, proposals can be unticked, nothing moves until «اعمال». */
@Composable
internal fun PlannerScreen(onOpenMemory: () -> Unit, onBack: () -> Unit, viewModel: PlannerViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val appliedText = state.applied?.let { stringResource(R.string.planner_applied, PersianDigits.format(it.count)) }
    val undoLabel = stringResource(R.string.planner_undo)
    LaunchedEffect(state.applied) {
        if (appliedText != null) {
            val result = snackbar.showSnackbar(appliedText, actionLabel = undoLabel, duration = SnackbarDuration.Long)
            if (result == SnackbarResult.ActionPerformed) viewModel.undoApplied() else viewModel.appliedShown()
        }
    }
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            RoozbanTopBar(
                title = { Text(stringResource(R.string.planner_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(painterResource(DsR.drawable.ic_arrow_back), stringResource(R.string.planner_back)) }
                },
                actions = {
                    IconButton(onClick = onOpenMemory) { Icon(painterResource(DsR.drawable.ic_memory), stringResource(R.string.planner_memory)) }
                },
            )
        },
        bottomBar = {
            val count = state.plan?.placements?.count { it.candidate.taskId in state.selected } ?: 0
            if (count > 0) {
                Button(onClick = viewModel::apply, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(stringResource(R.string.planner_apply, PersianDigits.format(count)))
                }
            }
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                FilterChip(selected = !state.tomorrow, onClick = { viewModel.showTomorrow(false) }, label = { Text(stringResource(R.string.planner_today)) })
                FilterChip(selected = state.tomorrow, onClick = { viewModel.showTomorrow(true) }, label = { Text(stringResource(R.string.planner_tomorrow)) })
                Spacer(Modifier.weight(1f))
                if (state.date != java.time.LocalDate.MIN) {
                    Text(PersianDateFormatter.fullDate(state.date.toJalali()), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            val plan = state.plan
            if (plan != null) {
                if (plan.placements.isEmpty()) {
                    EmptyState(
                        DsR.drawable.ic_schedule,
                        stringResource(R.string.planner_empty_title),
                        stringResource(R.string.planner_empty_body),
                        Roozban.colors.info,
                    )
                } else {
                    Text(stringResource(R.string.planner_intro), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    state.estimateFactor?.let {
                        Text(
                            stringResource(R.string.planner_estimate_note, PersianDigits.toPersian(String.format(Locale.US, "%.1f", it)).replace('.', '٫')),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Timeline(plan.fixed, plan.placements, state.selected, viewModel::toggle)
                if (plan.unplaced.isNotEmpty()) {
                    SectionTitle(stringResource(R.string.planner_unplaced), icon = DsR.drawable.ic_warning, color = Roozban.colors.warning.color)
                    Text(stringResource(R.string.planner_unplaced_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    plan.unplaced.forEach { c ->
                        Text("• " + c.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            PlanningSettingsCard(state.settings, viewModel)
            Spacer(Modifier.height(24.dp))
        }
    }
}

private sealed interface TimelineRow {
    val start: LocalTime
}

private data class FixedRow(val block: FixedBlock) : TimelineRow {
    override val start get() = block.start
}

private data class ProposalRow(val placement: Placement) : TimelineRow {
    override val start get() = placement.start
}

@Composable
private fun Timeline(fixed: List<FixedBlock>, placements: List<Placement>, selected: Set<String>, onToggle: (String) -> Unit) {
    val rows: List<TimelineRow> = (fixed.map(::FixedRow) + placements.map(::ProposalRow)).sortedBy { it.start }
    rows.forEach { row ->
        when (row) {
            is FixedRow -> FixedItem(row.block)
            is ProposalRow -> ProposalItem(row.placement, row.placement.candidate.taskId in selected) { onToggle(row.placement.candidate.taskId) }
        }
    }
}

@Composable
private fun FixedItem(block: FixedBlock) {
    AppCard(modifier = Modifier.fillMaxWidth(), accent = MaterialTheme.colorScheme.outline) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            TimeColumn(block.start, block.start.plusMinutes(block.minutes.toLong()))
            Spacer(Modifier.width(12.dp))
            Text(block.title, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
            StatusPill(stringResource(R.string.planner_fixed), neutralRole())
        }
    }
}

@Composable
private fun ProposalItem(p: Placement, checked: Boolean, onToggle: () -> Unit) {
    val role = reasonRole(p.reason)
    AppCard(modifier = Modifier.fillMaxWidth(), onClick = onToggle, accent = if (checked) role.color else null) {
        Row(Modifier.padding(start = 14.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TimeColumn(p.start, p.end)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(p.candidate.title, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    StatusPill(stringResource(reasonText(p.reason)), role)
                    Text(
                        stringResource(R.string.planner_minutes, PersianDigits.format(p.candidate.minutes)),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (p.candidate.risk >= RISK_SHOWN) {
                    Text(
                        stringResource(R.string.planner_risk, PersianDigits.format((p.candidate.risk * 100).toInt())),
                        style = MaterialTheme.typography.labelSmall,
                        color = Roozban.colors.warning.color,
                    )
                }
            }
            Checkbox(checked = checked, onCheckedChange = { onToggle() })
        }
    }
}

@Composable
private fun TimeColumn(start: LocalTime, end: LocalTime) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(PersianDateFormatter.time(start), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Text(PersianDateFormatter.time(end), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PlanningSettingsCard(s: PlanningSettings, vm: PlannerViewModel) {
    var picking by remember { mutableStateOf<Picker?>(null) }
    SectionTitle(stringResource(R.string.planner_settings), icon = DsR.drawable.ic_settings, modifier = Modifier.padding(top = 8.dp))
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TimeRow(stringResource(R.string.planner_day_start), s.dayStart) { picking = Picker.START }
            TimeRow(stringResource(R.string.planner_day_end), s.dayEnd) { picking = Picker.END }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).clickable(enabled = s.morningTime != null) { picking = Picker.MORNING }) {
                    Text(stringResource(R.string.planner_morning), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        s.morningTime?.let { stringResource(R.string.planner_morning_at, PersianDateFormatter.time(it)) } ?: stringResource(R.string.planner_morning_off),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (s.morningTime != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = s.morningTime != null, onCheckedChange = { on -> vm.setMorning(if (on) LocalTime.of(7, 30) else null) })
            }
            Text(stringResource(R.string.planner_morning_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (s.morningTime != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.planner_auto), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Switch(checked = s.autoPlan, onCheckedChange = vm::setAuto)
                }
                Text(stringResource(R.string.planner_auto_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    picking?.let { which ->
        TimePickerDialog(
            initial = when (which) {
                Picker.START -> s.dayStart
                Picker.END -> s.dayEnd
                Picker.MORNING -> s.morningTime ?: LocalTime.of(7, 30)
            },
            onConfirm = {
                when (which) {
                    Picker.START -> vm.setDayStart(it)
                    Picker.END -> vm.setDayEnd(it)
                    Picker.MORNING -> vm.setMorning(it)
                }
                picking = null
            },
            onDismiss = { picking = null },
        )
    }
}

private enum class Picker { START, END, MORNING }

@Composable
private fun TimeRow(label: String, time: LocalTime, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(PersianDateFormatter.time(time), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    }
}

private fun reasonText(r: PlacementReason) = when (r) {
    PlacementReason.OVERDUE -> R.string.planner_reason_overdue
    PlacementReason.DUE_TODAY -> R.string.planner_reason_due_today
    PlacementReason.PRIORITY -> R.string.planner_reason_priority
    PlacementReason.PRODUCTIVE_HOURS -> R.string.planner_reason_productive
    PlacementReason.FREE_TIME -> R.string.planner_reason_free
}

@Composable
private fun reasonRole(r: PlacementReason): Role = when (r) {
    PlacementReason.OVERDUE -> Roozban.colors.error
    PlacementReason.DUE_TODAY -> Roozban.colors.warning
    PlacementReason.PRIORITY -> Roozban.colors.focus
    PlacementReason.PRODUCTIVE_HOURS -> Roozban.colors.success
    PlacementReason.FREE_TIME -> Roozban.colors.info
}

@Composable
private fun neutralRole() = Role(
    MaterialTheme.colorScheme.outline,
    MaterialTheme.colorScheme.surface,
    MaterialTheme.colorScheme.surfaceVariant,
    MaterialTheme.colorScheme.onSurfaceVariant,
)

private const val RISK_SHOWN = 0.5
