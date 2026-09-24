package ir.roozban.feature.focus

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.roozban.core.calendar.PersianDigits
import ir.roozban.core.designsystem.R as DsR
import ir.roozban.core.domain.Access
import ir.roozban.core.model.FocusPhase
import ir.roozban.core.model.FocusSettings
import ir.roozban.core.model.FocusState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusScreen(taskId: String?, onBack: () -> Unit, viewModel: FocusViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var picking by remember { mutableStateOf(false) }
    LaunchedEffect(taskId) { viewModel.preselect(taskId) }
    LifecycleResumeEffect(Unit) {
        viewModel.refreshSilenceAccess()
        onPauseOrDispose { }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.focus_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(painterResource(DsR.drawable.ic_arrow_back), stringResource(R.string.focus_back)) }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(8.dp))
            PhaseHeader(state)
            Spacer(Modifier.height(16.dp))
            TimerRing(state)
            Spacer(Modifier.height(16.dp))
            TaskChip(state.taskTitle, enabled = state.timer !is FocusState.Running || state.phase == FocusPhase.WORK) { picking = true }
            Spacer(Modifier.height(20.dp))
            Controls(state, viewModel)
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(R.string.focus_today, PersianDigits.format(state.todaySessions), PersianDigits.format(state.todayMinutes)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (state.access != Access.FULL) {
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.focus_locked), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(24.dp))
            SettingsCard(state, viewModel::updateSettings)
            Spacer(Modifier.height(24.dp))
        }
    }

    if (picking) {
        ModalBottomSheet(onDismissRequest = { picking = false }) {
            Text(
                stringResource(R.string.focus_pick_task),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            LazyColumn(Modifier.fillMaxWidth()) {
                item {
                    TaskOptionRow(stringResource(R.string.focus_no_task), selected = state.taskId == null) {
                        viewModel.selectTask(null)
                        picking = false
                    }
                }
                items(state.tasks, key = { it.id }) { option ->
                    TaskOptionRow(option.title, selected = option.id == state.taskId) {
                        viewModel.selectTask(option.id)
                        picking = false
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun PhaseHeader(state: FocusUiState) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(shape = CircleShape, color = phaseContainer(state.phase)) {
            Text(
                phaseName(state.phase),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
        Text(
            stringResource(R.string.focus_cycle, PersianDigits.format(state.cycle), PersianDigits.format(state.settings.cyclesBeforeLongBreak)),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TimerRing(state: FocusUiState) {
    val progress by animateFloatAsState(state.progress, label = "focus-progress")
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val color = if (state.phase == FocusPhase.WORK) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
    Box(Modifier.size(260.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 14.dp.toPx()
            val inset = stroke / 2
            val arcSize = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke)
            val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)
            drawArc(track, 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
            drawArc(color, -90f, 360f * progress, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                formatCountdown(state.remainingMillis),
                fontSize = 56.sp,
                fontWeight = FontWeight.Light,
                style = MaterialTheme.typography.displayLarge,
            )
            if (state.timer is FocusState.Paused) {
                Text(stringResource(R.string.focus_paused), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun TaskChip(title: String?, enabled: Boolean, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth(0.85f)) {
        Icon(painterResource(DsR.drawable.ic_checklist), null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(title ?: stringResource(R.string.focus_pick_task), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun Controls(state: FocusUiState, viewModel: FocusViewModel) {
    val big = Modifier.size(72.dp)
    Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
        val active = state.timer != FocusState.Idle
        FilledTonalIconButton(onClick = viewModel::stop, enabled = active, modifier = Modifier.size(52.dp)) {
            Icon(painterResource(DsR.drawable.ic_stop), stringResource(R.string.focus_stop))
        }
        when (state.timer) {
            is FocusState.Running -> FilledIconButton(onClick = viewModel::pause, modifier = big) {
                Icon(painterResource(DsR.drawable.ic_pause), stringResource(R.string.focus_pause), Modifier.size(36.dp))
            }
            is FocusState.Paused -> FilledIconButton(onClick = viewModel::resume, modifier = big) {
                Icon(painterResource(DsR.drawable.ic_play), stringResource(R.string.focus_resume), Modifier.size(36.dp))
            }
            else -> FilledIconButton(
                onClick = viewModel::start,
                enabled = state.access == Access.FULL,
                modifier = big,
                colors = IconButtonDefaults.filledIconButtonColors(),
            ) {
                Icon(painterResource(DsR.drawable.ic_play), stringResource(R.string.focus_start), Modifier.size(36.dp))
            }
        }
        FilledTonalIconButton(onClick = viewModel::skip, enabled = active, modifier = Modifier.size(52.dp)) {
            Icon(painterResource(DsR.drawable.ic_skip_next), stringResource(R.string.focus_skip))
        }
    }
}

@Composable
private fun SettingsCard(state: FocusUiState, update: ((FocusSettings) -> FocusSettings) -> Unit) {
    val s = state.settings
    val context = LocalContext.current
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 8.dp)) {
            Text(
                stringResource(R.string.focus_settings),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            Stepper(stringResource(R.string.focus_work_minutes), stringResource(R.string.focus_minutes_value, PersianDigits.format(s.workMinutes)),
                onMinus = { update { it.copy(workMinutes = it.workMinutes - 5) } }, onPlus = { update { it.copy(workMinutes = it.workMinutes + 5) } })
            Stepper(stringResource(R.string.focus_short_minutes), stringResource(R.string.focus_minutes_value, PersianDigits.format(s.shortBreakMinutes)),
                onMinus = { update { it.copy(shortBreakMinutes = it.shortBreakMinutes - 1) } }, onPlus = { update { it.copy(shortBreakMinutes = it.shortBreakMinutes + 1) } })
            Stepper(stringResource(R.string.focus_long_minutes), stringResource(R.string.focus_minutes_value, PersianDigits.format(s.longBreakMinutes)),
                onMinus = { update { it.copy(longBreakMinutes = it.longBreakMinutes - 5) } }, onPlus = { update { it.copy(longBreakMinutes = it.longBreakMinutes + 5) } })
            Stepper(stringResource(R.string.focus_cycles), stringResource(R.string.focus_cycles_value, PersianDigits.format(s.cyclesBeforeLongBreak)),
                onMinus = { update { it.copy(cyclesBeforeLongBreak = it.cyclesBeforeLongBreak - 1) } }, onPlus = { update { it.copy(cyclesBeforeLongBreak = it.cyclesBeforeLongBreak + 1) } })
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SwitchRow(stringResource(R.string.focus_auto_breaks), null, s.autoStartBreaks) { v -> update { it.copy(autoStartBreaks = v) } }
            SwitchRow(stringResource(R.string.focus_auto_work), null, s.autoStartWork) { v -> update { it.copy(autoStartWork = v) } }
            SwitchRow(stringResource(R.string.focus_silence), stringResource(R.string.focus_silence_desc), s.silence) { v -> update { it.copy(silence = v) } }
            if (s.silence && !state.canSilence) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(DsR.drawable.ic_dnd), null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.focus_silence_access), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    TextButton(onClick = {
                        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }) { Text(stringResource(R.string.focus_silence_grant)) }
                }
            }
        }
    }
}

@Composable
private fun Stepper(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        IconButton(onClick = onMinus) { Text("−", style = MaterialTheme.typography.titleLarge) }
        Text(value, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, modifier = Modifier.width(80.dp))
        IconButton(onClick = onPlus) { Text("+", style = MaterialTheme.typography.titleLarge) }
    }
}

@Composable
private fun SwitchRow(label: String, description: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label)
            if (description != null) {
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun TaskOptionRow(title: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            modifier = Modifier.weight(1f),
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
        if (selected) Icon(painterResource(DsR.drawable.ic_check), null, tint = MaterialTheme.colorScheme.primary)
    }
}

@Composable
internal fun phaseName(phase: FocusPhase): String = stringResource(
    when (phase) {
        FocusPhase.WORK -> R.string.focus_phase_work
        FocusPhase.SHORT_BREAK -> R.string.focus_phase_short_break
        FocusPhase.LONG_BREAK -> R.string.focus_phase_long_break
    },
)

@Composable
private fun phaseContainer(phase: FocusPhase) =
    if (phase == FocusPhase.WORK) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.tertiaryContainer

/** Shown above the bottom navigation while a timer is active. */
@Composable
fun FocusMiniBar(onOpen: () -> Unit, viewModel: FocusBarViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    if (!state.visible) return
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(DsR.drawable.ic_timer), null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text(phaseName(state.phase), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            if (state.remainingMillis > 0) Text(formatCountdown(state.remainingMillis), style = MaterialTheme.typography.titleMedium)
            IconButton(onClick = viewModel::toggle) {
                Icon(
                    painterResource(if (state.running) DsR.drawable.ic_pause else DsR.drawable.ic_play),
                    stringResource(if (state.running) R.string.focus_pause else R.string.focus_resume),
                )
            }
        }
    }
}
