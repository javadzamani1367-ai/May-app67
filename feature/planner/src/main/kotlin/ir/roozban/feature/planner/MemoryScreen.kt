package ir.roozban.feature.planner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.roozban.core.calendar.PersianDateFormatter
import ir.roozban.core.calendar.PersianDigits
import ir.roozban.core.calendar.toJalali
import ir.roozban.core.designsystem.R as DsR
import ir.roozban.core.designsystem.components.AppCard
import ir.roozban.core.designsystem.components.RoozbanTopBar
import ir.roozban.core.designsystem.components.SectionTitle
import ir.roozban.core.designsystem.components.StatusPill
import ir.roozban.core.designsystem.theme.Roozban
import ir.roozban.core.domain.LearningSnapshot
import ir.roozban.core.model.FactSource
import ir.roozban.core.model.MemoryFact
import ir.roozban.learning.OverdueModel
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/** «روزبان چه یاد گرفته»: every fact with where it came from, editable, pinnable and deletable. */
@Composable
internal fun MemoryScreen(onBack: () -> Unit, viewModel: MemoryViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val undo by viewModel.undo.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val undoLabel = stringResource(R.string.memory_undo)
    var editing by remember { mutableStateOf<MemoryFact?>(null) }
    var confirmClear by remember { mutableStateOf(false) }

    LaunchedEffect(undo) {
        val u = undo ?: return@LaunchedEffect
        val result = snackbar.showSnackbar(context.getString(u.message), actionLabel = undoLabel, duration = SnackbarDuration.Long)
        if (result == SnackbarResult.ActionPerformed) viewModel.undoLast() else viewModel.undoShown()
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            RoozbanTopBar(
                title = { Text(stringResource(R.string.memory_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(painterResource(DsR.drawable.ic_arrow_back), stringResource(R.string.planner_back)) }
                },
                actions = {
                    if (state.facts.isNotEmpty() || state.snapshot != null) {
                        IconButton(onClick = { confirmClear = true }) {
                            Icon(painterResource(DsR.drawable.ic_delete), stringResource(R.string.memory_clear_all))
                        }
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(stringResource(R.string.memory_intro), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item { LearningCard(state, viewModel) }
            item { StatsCard(state.snapshot) }
            item { SectionTitle(stringResource(R.string.memory_facts), icon = DsR.drawable.ic_memory, modifier = Modifier.padding(top = 8.dp)) }
            item { AddFact(viewModel::add) }
            if (state.facts.isEmpty()) {
                item {
                    Text(stringResource(R.string.memory_empty), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(state.facts, key = { it.id }) { fact ->
                FactCard(fact, onEdit = { editing = fact }, onPin = { viewModel.togglePin(fact) }, onDelete = { viewModel.delete(fact) })
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    editing?.let { fact ->
        var text by rememberSaveable(fact.id) { mutableStateOf(fact.text) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(stringResource(R.string.memory_edit)) },
            text = { OutlinedTextField(value = text, onValueChange = { text = it.take(200) }, modifier = Modifier.fillMaxWidth()) },
            confirmButton = {
                TextButton(onClick = { viewModel.edit(fact, text); editing = null }, enabled = text.isNotBlank()) {
                    Text(stringResource(R.string.memory_save))
                }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text(stringResource(R.string.memory_cancel)) } },
        )
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.memory_clear_title)) },
            text = { Text(stringResource(R.string.memory_clear_body)) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearAll(); confirmClear = false }) {
                    Text(stringResource(R.string.memory_clear_all), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.memory_cancel)) } },
        )
    }
}

@Composable
private fun LearningCard(state: MemoryUiState, vm: MemoryViewModel) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.memory_learning), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Switch(checked = state.learningEnabled, onCheckedChange = vm::setLearning)
            }
            Text(stringResource(R.string.memory_learning_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val last = state.snapshot?.computedAt?.takeIf { it != Instant.EPOCH }
            Text(
                last?.let { stringResource(R.string.memory_last_run, formatInstant(it)) } ?: stringResource(R.string.memory_never_run),
                style = MaterialTheme.typography.bodySmall,
            )
            if (state.learningEnabled) {
                FilledTonalButton(onClick = vm::runNow, enabled = !state.running) {
                    if (state.running) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.size(8.dp))
                        Text(stringResource(R.string.memory_running))
                    } else {
                        Text(stringResource(R.string.memory_run_now))
                    }
                }
            }
        }
    }
}

@Composable
private fun StatsCard(s: LearningSnapshot?) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.memory_stats), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (s == null || (s.estimateSamples == 0 && s.hours.total <= 0 && s.lateSamples == 0)) {
                Text(stringResource(R.string.memory_stat_none), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                return@Column
            }
            if (s.estimateSamples > 0) {
                Stat(stringResource(R.string.memory_stat_estimate, decimal(s.factors.global.value), PersianDigits.format(s.estimateSamples)))
            }
            s.hours.bestWindow(2)?.let { (start, _) ->
                Stat(stringResource(R.string.memory_stat_hours, PersianDigits.format(start % 24), PersianDigits.format((start + 2) % 24)))
            }
            s.overallLate?.let {
                Stat(stringResource(R.string.memory_stat_late, PersianDigits.format((it * 100).toInt()), PersianDigits.format(s.lateSamples)))
            }
            Stat(
                stringResource(
                    R.string.memory_stat_model,
                    stringResource(if (s.overdue is OverdueModel.Logistic) R.string.memory_model_logistic else R.string.memory_model_prior),
                ),
            )
        }
    }
}

@Composable
private fun Stat(text: String) {
    Text("• $text", style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun AddFact(onAdd: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it.take(200) },
            placeholder = { Text(stringResource(R.string.memory_add_hint)) },
            modifier = Modifier.weight(1f),
            singleLine = true,
        )
        TextButton(onClick = { onAdd(text); text = "" }, enabled = text.isNotBlank()) { Text(stringResource(R.string.memory_add)) }
    }
}

@Composable
private fun FactCard(fact: MemoryFact, onEdit: () -> Unit, onPin: () -> Unit, onDelete: () -> Unit) {
    val role = when (fact.source) {
        FactSource.USER -> Roozban.colors.focus
        FactSource.INFERRED -> Roozban.colors.success
        FactSource.SETTINGS -> Roozban.colors.info
    }
    AppCard(modifier = Modifier.fillMaxWidth(), accent = if (fact.pinned) MaterialTheme.colorScheme.primary else null) {
        Column(Modifier.padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 4.dp)) {
            Text(fact.text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(end = 10.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                StatusPill(
                    stringResource(
                        when (fact.source) {
                            FactSource.USER -> R.string.memory_source_user
                            FactSource.INFERRED -> R.string.memory_source_inferred
                            FactSource.SETTINGS -> R.string.memory_source_settings
                        },
                    ),
                    role,
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    buildString {
                        append(formatInstant(fact.updatedAt))
                        if (fact.source == FactSource.INFERRED) {
                            append(" · ")
                            append(PersianDigits.format((fact.confidence * 100).toInt()))
                            append("٪")
                        }
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onPin) {
                    Icon(
                        painterResource(DsR.drawable.ic_flag),
                        stringResource(if (fact.pinned) R.string.memory_unpin else R.string.memory_pin),
                        tint = if (fact.pinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    )
                }
                IconButton(onClick = onEdit) { Icon(painterResource(DsR.drawable.ic_edit), stringResource(R.string.memory_edit)) }
                IconButton(onClick = onDelete) { Icon(painterResource(DsR.drawable.ic_delete), stringResource(R.string.memory_delete)) }
            }
        }
    }
}

private fun formatInstant(at: Instant): String {
    val t = at.atZone(ZoneId.systemDefault())
    return PersianDateFormatter.dayMonth(t.toLocalDate().toJalali()) + "، " + PersianDateFormatter.time(t.toLocalTime())
}

private fun decimal(v: Double) = PersianDigits.toPersian(String.format(Locale.US, "%.1f", v)).replace('.', '٫')
