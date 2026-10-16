package ir.ilam.inspection.ui.crew

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MoreTime
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.TableView
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ir.ilam.inspection.R
import ir.ilam.inspection.sync.CrewWork
import ir.ilam.inspection.ui.common.AppCard
import ir.ilam.inspection.ui.common.AppTextField
import ir.ilam.inspection.ui.common.BarRow
import ir.ilam.inspection.ui.common.PrimaryButton
import ir.ilam.inspection.ui.common.SecondaryButton
import ir.ilam.inspection.ui.common.SectionCard
import ir.ilam.inspection.ui.common.StatusBadge
import ir.ilam.inspection.ui.common.TavanTopBar
import ir.ilam.inspection.ui.common.ValueRow
import ir.ilam.inspection.ui.theme.Spacing
import ir.ilam.inspection.ui.theme.Tavan
import ir.ilam.inspection.ui.theme.Tone
import ir.ilam.inspection.util.PersianDate

/** One crew member over the period: figures, parts of the day, approved overtime, map, and day by day. */
@Composable
fun CrewMemberPage(member: CrewWork.Member, report: CrewWork.Report, state: CrewState, viewModel: CrewViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val t = member.total
    Scaffold(topBar = {
        TavanTopBar(title = member.name, subtitle = member.roles.joinToString("، "), onBack = onBack)
    }) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screen, vertical = Spacing.sm)
        ) {
            if (state.busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp))
            CrewProblem(state)
            SectionCard(title = stringResource(R.string.cw_summary), icon = Icons.Filled.Person, tone = Tone.BRAND) {
                Column {
                    ValueRow(stringResource(R.string.cw_days_worked),
                        fa(t.days) + if (t.holidays > 0) " " + stringResource(R.string.cw_holidays_suffix, fa(t.holidays)) else "")
                    ValueRow(stringResource(R.string.cw_visits_inspections), stringResource(R.string.cw_vi, fa(t.visits), fa(t.inspections)))
                    t.types.filterValues { it > 0 }.forEach { (k, n) -> ValueRow(report.typeNames[k] ?: k, fa(n)) }
                    ValueRow(stringResource(R.string.cw_subscribers), fa(t.subscribers))
                    ValueRow(stringResource(R.string.cw_start_end), stringResource(R.string.cw_range, clock(t.avgStart), clock(t.avgEnd)))
                    ValueRow(stringResource(R.string.cw_overtime_computed), stringResource(R.string.cw_hours, fa(t.overtime)) +
                        " · " + stringResource(R.string.cw_overtime_inspections, fa(t.overtimeInspections)))
                    ValueRow(stringResource(R.string.cw_outside), if (report.geo) fa(t.outside) else "—")
                    ValueRow(stringResource(R.string.cw_km), stringResource(R.string.cw_km_value, fa(t.km)))
                }
            }
            SectionCard(title = stringResource(R.string.cw_buckets), icon = Icons.Filled.AccessTime, tone = Tone.ACCENT) {
                val max = (t.buckets.values.maxOrNull() ?: 0).coerceAtLeast(1)
                Column {
                    t.buckets.forEach { (k, n) ->
                        BarRow(report.bucketNames[k] ?: k, fa(n), n.toFloat() / max, Tavan.colors.accent.strong)
                    }
                }
            }
            ApproveCard(member, state.busy) { hours, note -> viewModel.saveOvertime(member.key, hours, note) }
            val dots = member.days.flatMap { d -> d.points }.mapIndexedNotNull { i, p ->
                if (p.latitude == null || p.longitude == null) null else CrewDot("$i", p.latitude, p.longitude, crewTone(p.type))
            }
            if (dots.isNotEmpty()) {
                SectionCard(title = stringResource(R.string.cw_map), icon = Icons.Filled.Map, tone = Tone.INFO) { CrewDotsMap(dots) }
            }
            SectionCard(title = stringResource(R.string.cw_daily), icon = Icons.Filled.CalendarMonth, tone = Tone.BRAND) {
                Column { member.days.forEach { DayRow(it) } }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.padding(vertical = Spacing.md)) {
                PrimaryButton(stringResource(R.string.cw_word_member), onClick = { viewModel.share(context, "docx", member.key) },
                    enabled = !state.busy, icon = Icons.Filled.Description, tone = Tone.SUCCESS, modifier = Modifier.weight(1f))
                SecondaryButton(stringResource(R.string.cw_excel), onClick = { viewModel.share(context, "xlsx", member.key) },
                    enabled = !state.busy, icon = Icons.Filled.TableView, modifier = Modifier.weight(1f))
            }
        }
    }
}

/** The overtime the manager approves for this person over this period. */
@Composable
private fun ApproveCard(member: CrewWork.Member, busy: Boolean, onSave: (String, String) -> Unit) {
    var hours by rememberSaveable(member.key, member.approved) { mutableStateOf(member.approved?.let { fa(it.hours) }.orEmpty()) }
    var note by rememberSaveable(member.key, member.approved) { mutableStateOf(member.approved?.note.orEmpty()) }
    SectionCard(title = stringResource(R.string.cw_approve_title), subtitle = stringResource(R.string.cw_approve_hint),
        icon = Icons.Filled.MoreTime, tone = Tone.WARNING) {
        Column {
            AppTextField(stringResource(R.string.cw_approve_hours), hours, { hours = it }, ltr = true)
            AppTextField(stringResource(R.string.cw_approve_note), note, { note = it })
            PrimaryButton(stringResource(R.string.cw_approve_save), onClick = { onSave(hours, note) }, enabled = !busy,
                icon = Icons.Filled.MoreTime, modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm))
        }
    }
}

@Composable
private fun DayRow(d: CrewWork.Day) {
    AppCard(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Row {
                Text(PersianDate.format(d.first), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                d.holiday?.let { StatusBadge(it, Tone.WARNING) }
            }
            Text(
                stringResource(R.string.cw_day_line, clock(minuteOf(d.first)), clock(minuteOf(d.last)), fa(d.visits), fa(d.inspections)),
                style = MaterialTheme.typography.bodyMedium
            )
            val extra = buildList {
                if (d.overtime > 0) add(stringResource(R.string.cw_day_overtime, fa(d.overtime)))
                if (d.outside > 0) add(stringResource(R.string.cw_day_outside, fa(d.outside)))
                if (d.places.isNotEmpty()) add(d.places.joinToString("، "))
            }
            if (extra.isNotEmpty()) {
                Text(extra.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Minute of the day of an instant, in the phone's (Iran) time. */
private fun minuteOf(millis: Long): Int {
    val c = java.util.Calendar.getInstance()
    c.timeInMillis = millis
    return c.get(java.util.Calendar.HOUR_OF_DAY) * 60 + c.get(java.util.Calendar.MINUTE)
}
