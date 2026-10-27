package ir.ilam.inspection.ui.centers

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.ilam.inspection.R
import ir.ilam.inspection.sync.Centers
import ir.ilam.inspection.ui.common.HeaderAction
import ir.ilam.inspection.ui.common.SectionCard
import ir.ilam.inspection.ui.common.TavanTopBar
import ir.ilam.inspection.ui.common.ToneChip
import ir.ilam.inspection.ui.common.ValueRow
import ir.ilam.inspection.ui.fielditems.ProblemLine
import ir.ilam.inspection.ui.theme.Spacing
import ir.ilam.inspection.ui.theme.Tone
import ir.ilam.inspection.util.PersianNumbers

private val KINDS = listOf("finder" to R.string.ct_rewards_finder, "collection" to R.string.ct_rewards_collection,
    "enforcement" to R.string.ct_rewards_enforcement, "staff" to R.string.ct_rewards_staff)

/**
 * The year's four reward reports — finder, the company's collection crew,
 * enforcement agents (police and IRGC), administrative staff — each as the
 * server builds it from the centres' reward forms: the instructions it was
 * computed by, every centre, and each person's total. Formal Excel and Word from
 * the top bar, saved on the phone or sent.
 */
@Composable
fun CenterRewardsScreen(viewModel: CentersViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.rewards(state.rewardKind) }
    val report = state.rewards
    Column(modifier = Modifier.fillMaxSize()) {
        TavanTopBar(title = stringResource(R.string.ct_rewards), subtitle = stringResource(R.string.ct_year, PersianNumbers.toPersian(state.year)),
            onBack = onBack) {
            HeaderAction(Icons.Filled.TableChart, stringResource(R.string.ct_excel), onClick = { viewModel.exportRewards(context, "xlsx") })
            HeaderAction(Icons.Filled.Description, stringResource(R.string.ct_word), onClick = { viewModel.exportRewards(context, "docx") })
        }
        if (state.busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        ProblemLine(state.problem)
        state.message?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(Spacing.sm)) }
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.screen)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                modifier = Modifier.horizontalScroll(rememberScrollState()).padding(vertical = Spacing.sm)) {
                KINDS.forEach { (kind, label) ->
                    ToneChip(stringResource(label), state.rewardKind == kind, Tone.BRAND, onClick = { viewModel.rewards(kind) })
                }
            }
            if (report == null) return@Column
            Text(report.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(PersianNumbers.toPersian(report.subtitle), style = MaterialTheme.typography.bodySmall)
            report.paragraphs.forEach {
                Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = Spacing.xs))
            }
            report.tables.forEach { TableCards(it) }
        }
    }
}

/** A report table on a phone: one card per row, each cell beside its header; the total row last. */
@Composable
private fun TableCards(table: Centers.Table) {
    table.caption?.let { Text(it, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = Spacing.md)) }
    if (table.rows.isEmpty()) {
        Text(stringResource(R.string.ct_rewards_empty), style = MaterialTheme.typography.bodyMedium)
        return
    }
    table.rows.forEach { row ->
        // The centres' table (no caption) is titled by the subscriber; the people tables by the name.
        val title = if (table.caption == null) row.getOrNull(2) else row.firstOrNull()
        SectionCard(title = title?.takeIf { it.isNotBlank() } ?: row.firstOrNull().orEmpty()) {
            Column { table.header.forEachIndexed { i, h -> ValueRow(h, row.getOrNull(i).orEmpty()) } }
        }
    }
    table.total?.let { total ->
        SectionCard(title = stringResource(R.string.ct_totals), tone = Tone.SUCCESS) {
            Column {
                table.header.forEachIndexed { i, h ->
                    val v = total.getOrNull(i).orEmpty()
                    if (v.isNotBlank() && i > 0) ValueRow(h, v, emphasize = true)
                }
            }
        }
    }
}
