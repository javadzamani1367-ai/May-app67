package ir.ilam.inspection.ui.centers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Paid
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.ilam.inspection.R
import ir.ilam.inspection.ui.common.HeaderAction
import ir.ilam.inspection.ui.common.SectionCard
import ir.ilam.inspection.ui.common.TavanTopBar
import ir.ilam.inspection.ui.common.ToneChip
import ir.ilam.inspection.ui.common.ValueRow
import ir.ilam.inspection.ui.fielditems.ProblemLine
import ir.ilam.inspection.ui.theme.Spacing
import ir.ilam.inspection.ui.theme.Tone
import ir.ilam.inspection.util.PersianNumbers

/**
 * The year's rewards: «گزارش پاداش عوامل اداری» (the bill's percentage, the CEO's
 * share, the staff's split by role) and the finder and enforcement agents that
 * Tavanir pays — each centre with its amounts and each person's total; Excel shared.
 */
@Composable
fun CenterRewardsScreen(viewModel: CentersViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.rewards(state.rewardKind) }
    val staff = state.rewardKind == "staff"
    val report = state.rewards
    Column(modifier = Modifier.fillMaxSize()) {
        TavanTopBar(title = stringResource(R.string.ct_rewards), subtitle = stringResource(R.string.ct_year, PersianNumbers.toPersian(state.year)),
            onBack = onBack) {
            HeaderAction(Icons.Filled.TableChart, stringResource(R.string.ct_excel), onClick = { viewModel.exportRewards(context) })
        }
        if (state.busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        ProblemLine(state.problem)
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.screen)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.padding(vertical = Spacing.sm)) {
                ToneChip(stringResource(R.string.ct_rewards_staff), staff, Tone.BRAND, onClick = { viewModel.rewards("staff") })
                ToneChip(stringResource(R.string.ct_rewards_agents), !staff, Tone.BRAND, onClick = { viewModel.rewards("agents") })
            }
            if (report == null) return@Column
            val labels = if (staff) listOf(R.string.ct_base, R.string.ct_total_reward, R.string.ct_ceo, R.string.ct_staff_share)
            else listOf(R.string.ct_miners, R.string.ct_finder_reward, R.string.ct_police_reward)
            val keys = if (staff) listOf("base", "amount", "ceo", "staff") else listOf("miners", "finder", "police")
            SectionCard(title = stringResource(R.string.ct_totals), icon = Icons.Filled.Paid, tone = Tone.SUCCESS) {
                Column { keys.forEachIndexed { i, k -> ValueRow(stringResource(labels[i]), PersianNumbers.grouped(report.totals[k] ?: 0.0), emphasize = i == 1) } }
            }
            if (report.lines.isEmpty()) Text(stringResource(R.string.ct_rewards_empty), style = MaterialTheme.typography.bodyMedium)
            report.lines.forEach { line ->
                SectionCard(title = PersianNumbers.toPersian(line.title)) {
                    Column {
                        line.amounts.forEachIndexed { i, a -> ValueRow(stringResource(labels[i]), PersianNumbers.grouped(a)) }
                        if (line.note.isNotBlank()) Text(PersianNumbers.toPersian(line.note), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            if (report.people.isNotEmpty()) SectionCard(title = stringResource(R.string.ct_people), icon = Icons.Filled.Groups) {
                Column { report.people.forEach { ValueRow(it.name + " — " + it.group, PersianNumbers.grouped(it.amount)) } }
            }
        }
    }
}
