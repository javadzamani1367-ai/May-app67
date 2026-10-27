package ir.ilam.inspection.ui.centers

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.ilam.inspection.R
import ir.ilam.inspection.ui.common.BottomActionBar
import ir.ilam.inspection.ui.common.HeaderAction
import ir.ilam.inspection.ui.common.PrimaryButton
import ir.ilam.inspection.ui.common.SecondaryButton
import ir.ilam.inspection.ui.common.SectionCard
import ir.ilam.inspection.ui.common.StatusBadge
import ir.ilam.inspection.ui.common.TavanTopBar
import ir.ilam.inspection.ui.common.ValueRow
import ir.ilam.inspection.ui.common.rememberLeaveGuard
import ir.ilam.inspection.ui.fielditems.ProblemLine
import ir.ilam.inspection.ui.theme.Spacing
import ir.ilam.inspection.ui.theme.Tavan
import ir.ilam.inspection.ui.theme.Tone
import ir.ilam.inspection.util.PersianNumbers

/**
 * One centre's reward form: the instruction it cites and this centre's
 * calculation, then every person with a tick, name from the list, post,
 * organisation, action and — for the administrative staff — percentage.
 * Saved, made final with a number, or opened again; Excel and Word of the
 * one-page form from the top bar.
 */
@Composable
fun RewardFormScreen(viewModel: RewardFormViewModel, row: String, kind: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(row, kind) { viewModel.open(row, kind) }
    val form = state.form
    val leave = rememberLeaveGuard(dirty = state.dirty, onLeave = onBack)
    var confirm by remember { mutableStateOf<String?>(null) }
    Column(modifier = Modifier.fillMaxSize().imePadding()) {
        TavanTopBar(title = form?.name ?: stringResource(R.string.ct_rewards), subtitle = form?.title?.let { PersianNumbers.toPersian(it) },
            onBack = leave) {
            if (form != null) {
                HeaderAction(Icons.Filled.TableChart, stringResource(R.string.ct_excel), onClick = { viewModel.export(context, "xlsx") })
                HeaderAction(Icons.Filled.Description, stringResource(R.string.ct_word), onClick = { viewModel.export(context, "docx") })
            }
        }
        if (state.busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        ProblemLine(state.problem)
        state.message?.let { Text(stringResource(it), color = Tavan.colors.success.strong, modifier = Modifier.padding(horizontal = Spacing.screen)) }
        if (form == null) return@Column
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.screen)) {
            SectionCard(title = stringResource(R.string.rf_basis), icon = Icons.Filled.Verified, tone = if (form.final) Tone.SUCCESS else Tone.BRAND,
                trailing = {
                    StatusBadge(if (form.final) stringResource(R.string.rf_final_n, PersianNumbers.toPersian(form.number.orEmpty()))
                        else stringResource(R.string.rf_draft), if (form.final) Tone.SUCCESS else Tone.WARNING)
                }) {
                Column {
                    form.paragraphs.forEach { Text(PersianNumbers.toPersian(it), style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = Spacing.xs)) }
                    form.calc?.let { ValueRow(stringResource(R.string.rf_amount), PersianNumbers.grouped(it.amount), emphasize = true) }
                    form.calc?.payers?.forEach { (payer, sum) -> ValueRow(payer, PersianNumbers.grouped(sum)) }
                }
            }
            if (form.kind == "finder") {
                SectionCard(title = stringResource(R.string.rf_finder_name), tone = Tone.NEUTRAL) {
                    Column {
                        ValueRow(stringResource(R.string.rf_name), form.finderName ?: "—")
                        Text(stringResource(R.string.rf_finder_hint), style = MaterialTheme.typography.bodySmall)
                    }
                }
            } else {
                Text(stringResource(when (form.kind) {
                    "collection" -> R.string.rf_hint_collection
                    "enforcement" -> R.string.rf_hint_enforcement
                    else -> R.string.rf_hint_staff
                }), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = Spacing.sm))
                state.people.forEachIndexed { i, person ->
                    RewardPersonCard(person, form, editable = form.editable, onEdit = { viewModel.edit(i, it) },
                        onRemove = { viewModel.remove(i) })
                }
                if (form.kind == "staff") {
                    val sum = state.people.filter { it.on && !it.isCeo }.sumOf { it.pct ?: 0.0 }
                    Text(stringResource(R.string.rf_pct_sum, PersianNumbers.toPersian(PersianNumbers.plain(sum))),
                        color = if (kotlin.math.abs(sum - 100) > 0.01) Tavan.colors.warning.strong else Tavan.colors.success.strong,
                        style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(vertical = Spacing.sm))
                }
                if (form.editable) SecondaryButton(stringResource(R.string.rf_add), onClick = { viewModel.add() }, icon = Icons.Filled.Add,
                    modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.sm))
            }
        }
        BottomActionBar {
            when {
                form.final -> SecondaryButton(stringResource(R.string.rf_unlock), onClick = { confirm = "unlock" }, icon = Icons.Filled.LockOpen,
                    tone = Tone.WARNING, modifier = Modifier.fillMaxWidth())
                else -> {
                    if (form.editable && state.dirty) SecondaryButton(stringResource(R.string.ct_save), onClick = { viewModel.save() },
                        icon = Icons.Filled.Save, modifier = Modifier.weight(1f))
                    PrimaryButton(stringResource(R.string.rf_final), onClick = { confirm = "final" }, icon = Icons.Filled.Verified,
                        tone = Tone.SUCCESS, busy = state.busy, modifier = Modifier.weight(1f))
                }
            }
        }
    }
    confirm?.let { action ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(stringResource(if (action == "final") R.string.rf_final else R.string.rf_unlock)) },
            text = { Text(stringResource(if (action == "final") R.string.rf_final_confirm else R.string.rf_unlock_confirm)) },
            confirmButton = { TextButton(onClick = {
                confirm = null
                if (action == "final") viewModel.finalize() else viewModel.unlock()
            }) { Text(stringResource(if (action == "final") R.string.rf_final else R.string.rf_unlock)) } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text(stringResource(R.string.action_cancel)) } }
        )
    }
}
