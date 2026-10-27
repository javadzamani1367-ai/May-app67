package ir.ilam.inspection.ui.centers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.ViewColumn
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.ilam.inspection.R
import ir.ilam.inspection.ui.common.BottomActionBar
import ir.ilam.inspection.ui.common.PrimaryButton
import ir.ilam.inspection.ui.common.SecondaryButton
import ir.ilam.inspection.ui.common.SectionCard
import ir.ilam.inspection.ui.common.TavanTopBar
import ir.ilam.inspection.ui.common.ToneChip
import ir.ilam.inspection.ui.common.rememberLeaveGuard
import ir.ilam.inspection.ui.fielditems.ProblemLine
import ir.ilam.inspection.ui.theme.Spacing
import ir.ilam.inspection.ui.theme.Tavan
import ir.ilam.inspection.ui.theme.Tone
import ir.ilam.inspection.util.PersianNumbers

/**
 * Columns, access and year locks, as on the web panel: whether the area
 * executors see their areas' statistics; for every column, whether it is in
 * the default Excel and Word and which units see and fill it in the portal;
 * and each year locked once its statistics have gone to Tavanir.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CenterSettingsScreen(viewModel: CenterAdminViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.loadSettings() }
    val leave = rememberLeaveGuard(dirty = state.settingsDirty, onLeave = onBack)
    val s = state.settings
    Column(modifier = Modifier.fillMaxSize()) {
        TavanTopBar(title = stringResource(R.string.cs_title), subtitle = stringResource(R.string.ct_title), onBack = leave)
        if (state.busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        ProblemLine(state.problem)
        state.message?.let { Text(stringResource(it), color = Tavan.colors.success.strong, modifier = Modifier.padding(horizontal = Spacing.screen)) }
        if (s == null) return@Column
        LazyColumn(modifier = Modifier.weight(1f).padding(horizontal = Spacing.screen)) {
            item {
                SectionCard(title = stringResource(R.string.cs_access), tone = Tone.BRAND) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.cs_executor), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            Switch(checked = s.executor, onCheckedChange = { on -> viewModel.changeSettings { copy(executor = on) } })
                        }
                        Text(stringResource(R.string.cs_units_hint), style = MaterialTheme.typography.bodySmall)
                    }
                }
                SectionCard(title = stringResource(R.string.cs_years), subtitle = stringResource(R.string.cs_years_hint), icon = Icons.Filled.Lock,
                    tone = Tone.WARNING) {
                    Column {
                        s.years.forEach { y ->
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = Spacing.xs)) {
                                Text(stringResource(R.string.cs_year_line, PersianNumbers.toPersian(y.year), PersianNumbers.toPersian(y.rows)),
                                    style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                                SecondaryButton(stringResource(if (y.locked) R.string.cs_unlock else R.string.cs_lock),
                                    onClick = { viewModel.lockYear(y.year, !y.locked) },
                                    icon = if (y.locked) Icons.Filled.LockOpen else Icons.Filled.Lock,
                                    tone = if (y.locked) Tone.NEUTRAL else Tone.WARNING)
                            }
                        }
                    }
                }
                Text(stringResource(R.string.cs_columns), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = Spacing.md))
            }
            items(s.columns, key = { it.key }) { c ->
                SectionCard(title = c.label, subtitle = c.section.ifBlank { null }, icon = Icons.Filled.ViewColumn, tone = Tone.NEUTRAL) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        ToneChip(stringResource(R.string.ct_excel), c.key in s.excel, Tone.SUCCESS,
                            onClick = { viewModel.changeSettings { copy(excel = excel.toggle(c.key)) } })
                        ToneChip(stringResource(R.string.ct_word), c.key in s.word, Tone.INFO,
                            onClick = { viewModel.changeSettings { copy(word = word.toggle(c.key)) } })
                        // Units write only what is typed; calculated columns are the system's.
                        if (c.src != "calc") s.unitNames.forEachIndexed { u, name ->
                            ToneChip(name, c.key in s.units.getOrElse(u) { emptySet() }, Tone.ACCENT, onClick = {
                                viewModel.changeSettings { copy(units = units.mapIndexed { i, set -> if (i == u) set.toggle(c.key) else set }) }
                            })
                        }
                    }
                }
            }
        }
        if (state.settingsDirty) BottomActionBar {
            PrimaryButton(stringResource(R.string.ct_save), onClick = { viewModel.saveSettings() }, icon = Icons.Filled.Save,
                busy = state.busy, modifier = Modifier.fillMaxWidth())
        }
    }
}

private fun Set<String>.toggle(key: String) = if (key in this) this - key else this + key
