package ir.ilam.inspection.ui.flow

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
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.ilam.inspection.R
import ir.ilam.inspection.sync.Manage
import ir.ilam.inspection.ui.common.HeaderAction
import ir.ilam.inspection.ui.common.SectionCard
import ir.ilam.inspection.ui.common.StatusBadge
import ir.ilam.inspection.ui.common.TavanTopBar
import ir.ilam.inspection.ui.common.ToneChip
import ir.ilam.inspection.ui.common.ToneProgress
import ir.ilam.inspection.ui.fielditems.ProblemLine
import ir.ilam.inspection.ui.theme.Spacing
import ir.ilam.inspection.ui.theme.Tone
import ir.ilam.inspection.util.PersianNumbers

/**
 * Scores out of 100 for areas, executors, destination units and experts,
 * ranked, with each part of the score shown as a bar and the formula under
 * each table. The manager sees everyone; an executor their own areas.
 */
@Composable
fun ScoresScreen(viewModel: ManageViewModel, onBack: () -> Unit) {
    val state by viewModel.scores.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(Unit) { viewModel.loadScores() }
    Column(modifier = Modifier.fillMaxSize()) {
        TavanTopBar(title = stringResource(R.string.sc_title), subtitle = stringResource(R.string.sc_subtitle), onBack = onBack) {
            HeaderAction(Icons.Filled.Share, stringResource(R.string.sc_excel), { viewModel.shareScores(context) })
        }
        if (state.busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        ProblemLine(state.problem)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = Spacing.screen, vertical = Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            listOf(7 to R.string.sc_week, 30 to R.string.sc_month, 90 to R.string.sc_quarter, 365 to R.string.sc_year).forEach { (days, label) ->
                ToneChip(stringResource(label), state.days == days, Tone.BRAND, onClick = { viewModel.loadScores(days) })
            }
        }
        Column(modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.screen)) {
            Text(stringResource(R.string.sc_explain), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = Spacing.xs))
            state.tables.forEach { ScoreTableCard(it) }
        }
    }
}

@Composable
private fun ScoreTableCard(table: Manage.ScoreTable) {
    SectionCard(title = table.title, icon = Icons.Filled.Insights, tone = Tone.BRAND) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            if (table.rows.isEmpty()) Text(stringResource(R.string.sc_empty), style = MaterialTheme.typography.bodySmall)
            table.rows.forEach { row -> ScoreRow(row, table.parts) }
            Text(
                table.parts.joinToString("، ") { it.label + " (" + PersianNumbers.toPersian(it.weight) + ")" },
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ScoreRow(row: Manage.ScoreRow, parts: List<Manage.ScorePart>) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                (row.rank?.let { PersianNumbers.toPersian(it) + ". " } ?: "") + row.name + (row.unit?.let { " — $it" } ?: ""),
                style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f)
            )
            StatusBadge(row.score?.let { PersianNumbers.toPersian(it) } ?: "—", scoreTone(row.score), solid = row.score != null)
        }
        if (row.area.isNotBlank() && row.area != row.name) {
            Text(row.area, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        parts.forEach { part ->
            val value = row.parts[part.key]
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                Text(part.label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(0.55f), maxLines = 1)
                if (value == null) {
                    Text("—", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(0.45f))
                } else {
                    ToneProgress((value / 100).toFloat().coerceIn(0f, 1f), scoreTone(value.toInt()), modifier = Modifier.weight(0.45f))
                }
            }
        }
    }
}

/** Green from 80, amber from 60, red below; no score is no colour. */
fun scoreTone(score: Int?): Tone = when {
    score == null -> Tone.NEUTRAL
    score >= 80 -> Tone.SUCCESS
    score >= 60 -> Tone.WARNING
    else -> Tone.DANGER
}
