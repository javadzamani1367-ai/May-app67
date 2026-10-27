package ir.ilam.inspection.ui.centers

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Paid
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.ilam.inspection.R
import ir.ilam.inspection.sync.Centers
import ir.ilam.inspection.ui.common.BottomActionBar
import ir.ilam.inspection.ui.common.HeaderAction
import ir.ilam.inspection.ui.common.PrimaryButton
import ir.ilam.inspection.ui.common.SectionCard
import ir.ilam.inspection.ui.common.TavanTopBar
import ir.ilam.inspection.ui.common.ValueRow
import ir.ilam.inspection.ui.common.rememberLeaveGuard
import ir.ilam.inspection.ui.fielditems.ProblemLine
import ir.ilam.inspection.ui.theme.Spacing
import ir.ilam.inspection.ui.theme.Tavan
import ir.ilam.inspection.ui.theme.Tone
import ir.ilam.inspection.util.PersianDate
import ir.ilam.inspection.util.PersianNumbers

/**
 * One centre, section by section as on the web panel: each cell editable where
 * the user may write it, conflicts settled by a tap, the rewards it earns under
 * the instruction in force on its date, and every change with who made it.
 */
@Composable
fun CenterRowScreen(viewModel: CentersViewModel, onBack: () -> Unit, onOpen: (String) -> Unit, onForm: (String, String) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val detail = state.detail
    val columns = state.page?.columns.orEmpty()
    val writable = state.page?.writable.orEmpty()
    val dirty = state.edits.isNotEmpty() || state.choices.isNotEmpty()
    val leave = rememberLeaveGuard(dirty = dirty, onLeave = onBack)
    var deleting by remember { mutableStateOf(false) }
    val manage = state.page?.manage == true
    Column(modifier = Modifier.fillMaxSize().imePadding()) {
        val v = detail?.row?.values.orEmpty()
        TavanTopBar(title = (v["subscriber"] ?: v["subscription"] ?: "").toString().ifBlank { stringResource(R.string.ct_row) },
            subtitle = detail?.row?.let { stringResource(R.string.ct_year, PersianNumbers.toPersian(it.year)) }, onBack = leave) {
            if (detail != null && !detail.row.locked) {
                HeaderAction(Icons.Filled.ContentCopy, stringResource(R.string.ct_copy), onClick = { viewModel.copyRow(onOpen) })
                if (manage) HeaderAction(Icons.Filled.Delete, stringResource(R.string.ct_delete), onClick = { deleting = true })
            }
        }
        if (state.busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        ProblemLine(state.problem)
        state.message?.let { Text(stringResource(it), color = Tavan.colors.success.strong, modifier = Modifier.padding(horizontal = Spacing.screen)) }
        if (detail == null) return@Column
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.screen)) {
            if (detail.row.locked) Text(stringResource(R.string.ct_locked_hint), color = Tavan.colors.warning.strong)
            if (!detail.row.approved) Text(stringResource(R.string.ct_not_approved), color = Tavan.colors.danger.strong)
            detail.row.warnings.forEach { Text("⚠ " + it.text, color = Tavan.colors.danger.strong, style = MaterialTheme.typography.bodyMedium) }
            columns.filter { it.section.isNotBlank() }.groupBy { it.section }.forEach { (section, cells) ->
                SectionCard(title = section, tone = if (cells.any { it.key in detail.row.conflicts }) Tone.WARNING else Tone.BRAND) {
                    Column {
                        cells.forEach { column ->
                            CenterCell(column, detail, state.edits[column.key], state.choices[column.key],
                                writable = column.key in writable && !detail.row.locked,
                                onEdit = { viewModel.edit(column.key, it) }, onChoose = { viewModel.choose(column.key, it) })
                        }
                    }
                }
            }
            detail.row.hint?.let { hint ->
                Text(stringResource(R.string.ct_unit_stage, hint) + (detail.row.suggest?.let { " — " + stringResource(R.string.ct_suggest, it) } ?: ""),
                    style = MaterialTheme.typography.bodySmall, color = Tavan.colors.info.strong, modifier = Modifier.padding(vertical = Spacing.sm))
            }
            if (manage) RewardsCard(detail) { kind -> onForm(detail.row.id, kind) }
            ChangesCard(detail.changes)
        }
        if (dirty && !detail.row.locked) BottomActionBar {
            PrimaryButton(stringResource(R.string.ct_save), onClick = { viewModel.save() }, icon = Icons.Filled.Save,
                busy = state.busy, modifier = Modifier.fillMaxWidth())
        }
    }
    if (deleting) AlertDialog(
        onDismissRequest = { deleting = false },
        title = { Text(stringResource(R.string.ct_delete)) },
        text = { Text(stringResource(R.string.ct_delete_confirm)) },
        confirmButton = { TextButton(onClick = { deleting = false; viewModel.delete(onBack) }) {
            Text(stringResource(R.string.ct_delete), color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { deleting = false }) { Text(stringResource(R.string.action_cancel)) } }
    )
}

/** The centre's four rewards, each opening its own one-page form. */
@Composable
private fun RewardsCard(detail: Centers.Detail, onForm: (String) -> Unit) {
    SectionCard(title = stringResource(R.string.ct_rewards), icon = Icons.Filled.Paid, tone = Tone.SUCCESS) {
        Column {
            Text(stringResource(R.string.rf_row_hint), style = MaterialTheme.typography.bodySmall)
            detail.rewards.forEach { r ->
                Column(modifier = Modifier.fillMaxWidth().clickable { onForm(r.kind) }.padding(vertical = Spacing.xs)) {
                    ValueRow(r.name, r.amount?.let { PersianNumbers.grouped(it) } ?: "—", emphasize = true)
                    Text(PersianNumbers.toPersian(r.rule) + " · " + if (r.final) stringResource(R.string.rf_final_n, PersianNumbers.toPersian(r.number.orEmpty()))
                        else stringResource(R.string.rf_draft), style = MaterialTheme.typography.bodySmall,
                        color = if (r.final) Tavan.colors.success.strong else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun ChangesCard(changes: List<Centers.Change>) {
    SectionCard(title = stringResource(R.string.ct_changes), icon = Icons.Filled.History, tone = Tone.NEUTRAL) {
        Column {
            if (changes.isEmpty()) Text(stringResource(R.string.ct_no_changes), style = MaterialTheme.typography.bodyMedium)
            changes.take(50).forEach { c ->
                Text(PersianDate.formatWithTime(c.at) + " · " + (c.who ?: "—") + " · " + c.label, style = MaterialTheme.typography.labelLarge)
                Text(PersianNumbers.toPersian((c.old ?: "—") + " ← " + (c.new ?: "—")) + " (" + howLabel(c.how) + ")",
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = Spacing.xs))
            }
        }
    }
}

@Composable
private fun howLabel(how: String): String = stringResource(
    when (how) {
        "edit" -> R.string.ct_how_edit
        "choose:system" -> R.string.ct_how_system
        "choose:file" -> R.string.ct_how_file
        "import" -> R.string.ct_how_import
        "add" -> R.string.ct_how_add
        else -> R.string.ct_how_other
    }
)
