package ir.ilam.inspection.ui.centers

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Merge
import androidx.compose.material.icons.filled.NewLabel
import androidx.compose.material.icons.filled.TableView
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.ilam.inspection.R
import ir.ilam.inspection.ui.common.BottomActionBar
import ir.ilam.inspection.ui.common.KpiTile
import ir.ilam.inspection.ui.common.PrimaryButton
import ir.ilam.inspection.ui.common.SecondaryButton
import ir.ilam.inspection.ui.common.SectionCard
import ir.ilam.inspection.ui.common.TavanTopBar
import ir.ilam.inspection.ui.common.YesNoRow
import ir.ilam.inspection.ui.fielditems.ProblemLine
import ir.ilam.inspection.ui.theme.Spacing
import ir.ilam.inspection.ui.theme.Tavan
import ir.ilam.inspection.ui.theme.Tone
import ir.ilam.inspection.util.PersianNumbers

private const val XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

/**
 * An Excel file from the phone into the statistics: pick it, see which sheets,
 * columns and rows were recognised and which cells will conflict, untick a sheet
 * if it repeats another, then import. Conflicts are settled afterwards on each row.
 */
@Composable
fun CenterImportScreen(viewModel: CentersViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.import(context, it) }
    }
    val plan = state.plan
    Column(modifier = Modifier.fillMaxSize()) {
        TavanTopBar(title = stringResource(R.string.ct_import), subtitle = stringResource(R.string.ct_import_hint), onBack = onBack)
        if (state.busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        ProblemLine(state.problem)
        state.message?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(Spacing.sm)) }
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.screen)) {
            Text(stringResource(R.string.ct_import_explain, PersianNumbers.toPersian(state.year)), style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = Spacing.sm))
            SecondaryButton(stringResource(R.string.ct_choose_file), onClick = { picker.launch(arrayOf(XLSX, "application/octet-stream")) },
                icon = Icons.Filled.FileOpen, modifier = Modifier.fillMaxWidth())
            state.applied?.let {
                Text(stringResource(R.string.ct_applied, PersianNumbers.toPersian(it.updated), PersianNumbers.toPersian(it.created),
                    PersianNumbers.toPersian(it.supplement)), color = Tavan.colors.success.strong, modifier = Modifier.padding(vertical = Spacing.md))
            }
            if (plan != null) {
                val s = plan.summary
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.padding(top = Spacing.md)) {
                    KpiTile(stringResource(R.string.ct_plan_match), PersianNumbers.toPersian(s["match"] ?: 0), Icons.Filled.Merge, Tone.BRAND, Modifier.weight(1f))
                    KpiTile(stringResource(R.string.ct_plan_new), PersianNumbers.toPersian(s["new"] ?: 0), Icons.Filled.NewLabel, Tone.INFO, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.padding(vertical = Spacing.sm)) {
                    KpiTile(stringResource(R.string.ct_conflicts), PersianNumbers.toPersian(s["conflicts"] ?: 0), Icons.Filled.Warning,
                        Tone.WARNING, Modifier.weight(1f))
                    KpiTile(stringResource(R.string.ct_plan_supplement), PersianNumbers.toPersian(s["supplement"] ?: 0), Icons.Filled.TableView,
                        Tone.NEUTRAL, Modifier.weight(1f))
                }
                if ((s["repeat"] ?: 0) + (s["locked"] ?: 0) > 0) Text(stringResource(R.string.ct_plan_skipped,
                    PersianNumbers.toPersian(s["repeat"] ?: 0), PersianNumbers.toPersian(s["locked"] ?: 0)), style = MaterialTheme.typography.bodySmall)
                SectionCard(title = stringResource(R.string.ct_sheets)) {
                    Column {
                        plan.sheets.filter { it.kind != "none" }.forEach { sheet ->
                            YesNoRow(stringResource(if (sheet.kind == "supplement") R.string.ct_sheet_supp else R.string.ct_sheet_main,
                                sheet.name, PersianNumbers.toPersian(sheet.columns), PersianNumbers.toPersian(sheet.rows)),
                                sheet.name in state.sheets, { want -> if (want != (sheet.name in state.sheets)) viewModel.toggleSheet(sheet.name) })
                            if (sheet.missing.isNotEmpty() && sheet.kind == "main") Text(stringResource(R.string.ct_sheet_missing,
                                sheet.missing.joinToString(" / ")), style = MaterialTheme.typography.bodySmall)
                            if (sheet.unknown.isNotEmpty()) Text(stringResource(R.string.ct_sheet_unknown, sheet.unknown.joinToString(" / ")),
                                style = MaterialTheme.typography.bodySmall, color = Tavan.colors.warning.strong)
                        }
                    }
                }
                SectionCard(title = stringResource(R.string.ct_plan_rows)) {
                    Column {
                        plan.rows.take(150).forEach { r ->
                            Text(PersianNumbers.toPersian(r.sheet + " / " + r.line) + " — " + (r.subscriber ?: r.subscription ?: "") + " — " +
                                statusLabel(r.status), style = MaterialTheme.typography.bodyMedium)
                            r.conflicts.forEach { Text("⚠ " + PersianNumbers.toPersian(it), style = MaterialTheme.typography.bodySmall,
                                color = Tavan.colors.warning.strong) }
                        }
                    }
                }
            }
        }
        if (plan != null) BottomActionBar {
            PrimaryButton(stringResource(R.string.ct_apply), onClick = { viewModel.apply() }, busy = state.busy, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun statusLabel(status: String): String = stringResource(
    when (status) {
        "match" -> R.string.ct_plan_match
        "new" -> R.string.ct_plan_new
        "repeat" -> R.string.ct_plan_repeat
        else -> R.string.ct_locked
    }
)
