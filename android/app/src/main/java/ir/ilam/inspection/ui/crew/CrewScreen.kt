package ir.ilam.inspection.ui.crew

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TableView
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ir.ilam.inspection.R
import ir.ilam.inspection.container
import ir.ilam.inspection.sync.CrewWork
import ir.ilam.inspection.ui.common.AppCard
import ir.ilam.inspection.ui.common.ContainerViewModelFactory
import ir.ilam.inspection.ui.common.EmptyState
import ir.ilam.inspection.ui.common.HeaderAction
import ir.ilam.inspection.ui.common.KpiTile
import ir.ilam.inspection.ui.common.PrimaryButton
import ir.ilam.inspection.ui.common.SecondaryButton
import ir.ilam.inspection.ui.common.SectionCard
import ir.ilam.inspection.ui.common.StatusBadge
import ir.ilam.inspection.ui.common.TavanTopBar
import ir.ilam.inspection.ui.common.ToneChip
import ir.ilam.inspection.ui.theme.Spacing
import ir.ilam.inspection.ui.theme.Tavan
import ir.ilam.inspection.ui.theme.Tone
import ir.ilam.inspection.util.PersianDate
import ir.ilam.inspection.util.PersianNumbers

/**
 * Manager only: how each crew member worked over a period — visits,
 * inspections, start and end of the day, overtime — from the moments panels
 * and poles were recorded, with a map of the visits and the server's Excel
 * and Word. A tap on a person opens their days.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CrewScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val appContainer = context.container
    val viewModel: CrewViewModel = viewModel(factory = remember { ContainerViewModelFactory(appContainer) { CrewViewModel(it) } })
    val state by viewModel.state.collectAsStateWithLifecycle()
    val member = state.member
    if (member != null && state.report != null) {
        BackHandler { viewModel.select(null) }
        CrewMemberPage(member, state.report!!, state, viewModel, onBack = { viewModel.select(null) })
        return
    }
    val report = state.report
    Scaffold(topBar = {
        TavanTopBar(
            title = stringResource(R.string.cw_title),
            subtitle = report?.let { stringResource(R.string.cw_period, PersianDate.format(it.from), PersianDate.format(it.to)) },
            onBack = onBack
        ) { HeaderAction(Icons.Filled.Refresh, stringResource(R.string.cw_refresh), viewModel::load) }
    }) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screen, vertical = Spacing.sm)
        ) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                CrewPeriod.entries.forEach { period ->
                    ToneChip(
                        text = if (period == CrewPeriod.MONTH) stringResource(R.string.cw_this_month)
                        else stringResource(R.string.cw_days, PersianNumbers.toPersian(period.days)),
                        selected = state.period == period,
                        tone = Tone.ACCENT,
                        onClick = { viewModel.setPeriod(period) }
                    )
                }
            }
            if (state.busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp))
            CrewProblem(state)
            when {
                report == null -> if (!state.busy) EmptyState(stringResource(R.string.cw_no_data), icon = Icons.Filled.Groups)
                report.members.isEmpty() -> EmptyState(stringResource(R.string.cw_empty_hint), icon = Icons.Filled.Groups,
                    title = stringResource(R.string.cw_empty))
                else -> CrewOverview(report, state, viewModel::select) { format -> viewModel.share(context, format, null) }
            }
        }
    }
}

@Composable
internal fun CrewProblem(state: CrewState) {
    val text = when {
        state.offline -> stringResource(R.string.cw_offline)
        else -> state.problem
    } ?: return
    Text(text, color = Tavan.colors.danger.strong, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = Spacing.sm))
}

@Composable
private fun CrewOverview(report: CrewWork.Report, state: CrewState, onOpen: (String) -> Unit, onShare: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.padding(vertical = Spacing.sm)) {
        KpiTile(stringResource(R.string.cw_people), PersianNumbers.toPersian(report.members.size), Icons.Filled.Groups, Tone.BRAND, Modifier.weight(1f))
        KpiTile(stringResource(R.string.cw_visits), PersianNumbers.toPersian(report.visits.size), Icons.Filled.Place, Tone.INFO, Modifier.weight(1f))
        KpiTile(stringResource(R.string.cw_inspections), PersianNumbers.toPersian(report.visits.sumOf { it.inspections }),
            Icons.Filled.TaskAlt, Tone.SUCCESS, Modifier.weight(1f))
    }
    report.members.forEach { m -> MemberCard(m) { onOpen(m.key) } }
    val dots = report.visits.mapNotNull { v ->
        if (v.latitude == null || v.longitude == null) null else CrewDot(v.number.toString(), v.latitude, v.longitude, Tone.BRAND)
    }
    if (dots.isNotEmpty()) {
        SectionCard(title = stringResource(R.string.cw_map), icon = Icons.Filled.Map, tone = Tone.INFO) {
            CrewDotsMap(dots)
        }
    }
    SectionCard(title = stringResource(R.string.cw_files), subtitle = stringResource(R.string.cw_files_hint),
        icon = Icons.Filled.FileDownload, tone = Tone.SUCCESS) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            PrimaryButton(stringResource(R.string.cw_word), onClick = { onShare("docx") }, enabled = !state.busy,
                icon = Icons.Filled.Description, tone = Tone.SUCCESS, modifier = Modifier.weight(1f))
            SecondaryButton(stringResource(R.string.cw_excel), onClick = { onShare("xlsx") }, enabled = !state.busy,
                icon = Icons.Filled.TableView, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun MemberCard(m: CrewWork.Member, onClick: () -> Unit) {
    val t = m.total
    AppCard(modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp), onClick = onClick) {
        Column(modifier = Modifier.padding(Spacing.lg)) {
            Row {
                Column(modifier = Modifier.weight(1f)) {
                    Text(m.name, style = MaterialTheme.typography.titleMedium)
                    Text(m.roles.joinToString("، "), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (t.overtime > 0) StatusBadge(stringResource(R.string.cw_overtime_badge, fa(t.overtime)), Tone.WARNING)
            }
            Text(
                stringResource(R.string.cw_member_line, fa(t.days), fa(t.visits), fa(t.inspections), clock(t.avgStart), clock(t.avgEnd)),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = Spacing.sm)
            )
            m.approved?.let {
                Text(stringResource(R.string.cw_approved_line, fa(it.hours)), style = MaterialTheme.typography.bodySmall,
                    color = Tavan.colors.success.strong)
            }
        }
    }
}

internal fun fa(value: Number): String = PersianNumbers.toPersian(
    if (value is Double && value % 1.0 == 0.0) value.toLong().toString() else value.toString()
)

internal fun clock(minute: Int?): String =
    if (minute == null) "—" else PersianNumbers.toPersian("%02d:%02d".format(minute / 60, minute % 60))
