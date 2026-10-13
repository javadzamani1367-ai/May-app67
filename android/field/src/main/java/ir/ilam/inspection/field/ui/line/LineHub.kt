package ir.ilam.inspection.field.ui.line

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ir.ilam.inspection.field.R
import ir.ilam.inspection.field.data.LineAnalysis
import ir.ilam.inspection.field.ui.form.HubSection
import ir.ilam.inspection.field.ui.form.SectionStatus
import ir.ilam.inspection.field.ui.form.mediaSection
import ir.ilam.inspection.field.ui.form.preview
import ir.ilam.inspection.ui.common.AppCard
import ir.ilam.inspection.ui.theme.Spacing
import ir.ilam.inspection.ui.theme.Tone
import ir.ilam.inspection.util.PersianNumbers

/** The walk's tiles: the feeder, its reading at the panel, the poles, what they show, and photos. */
@Composable
fun lineSections(viewModel: LineViewModel): List<HubSection> {
    val item = viewModel.item ?: return emptyList()
    val payload = viewModel.payload
    val missing = viewModel.missing()
    val verdict = viewModel.verdict()
    val feederName = when (payload.feeder) {
        null -> null
        "other" -> payload.feederName.ifBlank { null }
        else -> stringResource(R.string.line_feeder) + " " + payload.feeder
    }
    val poleProblems = missing.any { it == "poles" || it.startsWith("pole_") }
    return listOf(
        HubSection(
            key = PAGE_FEEDER,
            title = stringResource(R.string.line_feeder_title),
            summary = preview(listOfNotNull(item.plate?.takeIf { it.isNotBlank() }, feederName,
                payload.network?.let { stringResource(networkLabel(it)) }).joinToString(" · "))
                ?: stringResource(R.string.missing_line_plate),
            icon = Icons.Filled.Badge,
            tone = Tone.BRAND,
            status = if (missing.contains(LineViewModel.KEY_PLATE) || missing.contains("feeder")) SectionStatus.MISSING
            else SectionStatus.DONE
        ),
        HubSection(
            key = PAGE_START,
            title = stringResource(R.string.line_start_title),
            summary = if (payload.start.complete()) "R ${fa(payload.start.r)} · S ${fa(payload.start.s)} · T ${fa(payload.start.t)}"
            else stringResource(R.string.line_start_hint),
            icon = Icons.Filled.PowerSettingsNew,
            tone = Tone.ACCENT,
            status = when {
                missing.contains("start_range") -> SectionStatus.MISSING
                payload.start.complete() -> SectionStatus.DONE
                else -> SectionStatus.OPTIONAL
            }
        ),
        HubSection(
            key = PAGE_POLES,
            title = stringResource(R.string.line_poles_title),
            summary = if (payload.poles.isEmpty()) stringResource(R.string.line_poles_need)
            else stringResource(R.string.line_poles_summary, PersianNumbers.toPersian(payload.poles.size),
                PersianNumbers.toPersian(payload.poles.withIndex().count { (i, p) -> LineAnalysis.poleMissing(p, i + 1).isEmpty() })),
            icon = Icons.Filled.Timeline,
            tone = Tone.INFO,
            status = if (poleProblems) SectionStatus.MISSING else SectionStatus.DONE
        ),
        HubSection(
            key = PAGE_ANALYSIS,
            title = stringResource(R.string.line_analysis_title),
            summary = verdict.heaviest?.let {
                stringResource(R.string.line_heaviest, pointName(it.from), pointName(it.to), amps(it.drop))
            } ?: stringResource(R.string.line_analysis_empty),
            icon = Icons.Filled.Insights,
            tone = if (verdict.heavy.isNotEmpty()) Tone.DANGER else Tone.ACCENT,
            status = when {
                verdict.points.size < LineAnalysis.MIN_POLES -> SectionStatus.OPTIONAL
                verdict.rises.isNotEmpty() -> SectionStatus.ATTENTION
                else -> SectionStatus.DONE
            }
        ),
        mediaSection(viewModel.files)
    )
}

/** The chart on the form's own page, once there is something to draw. */
@Composable
fun LineHubHeader(viewModel: LineViewModel) {
    val verdict = viewModel.verdict()
    if (verdict.points.size < 2) return
    AppCard(modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Box(modifier = Modifier.padding(Spacing.md)) {
            LineChart(verdict, height = 150.dp)
        }
    }
}

@Composable
fun lineMissingText(key: String): String {
    val pole = PersianNumbers.toPersian(key.substringAfter(':', ""))
    return when {
        key == LineViewModel.KEY_PLATE -> stringResource(R.string.missing_line_plate)
        key == "feeder" -> stringResource(R.string.missing_line_feeder)
        key == "start_range" -> stringResource(R.string.missing_line_start_range)
        key == "poles" -> stringResource(R.string.missing_line_poles)
        key.startsWith("pole_position:") -> stringResource(R.string.missing_pole_position, pole)
        key.startsWith("pole_values:") -> stringResource(R.string.missing_pole_values, pole)
        key.startsWith("pole_range:") -> stringResource(R.string.missing_pole_range, pole)
        key.startsWith("pole_reason:") -> stringResource(R.string.missing_pole_reason, pole)
        else -> key
    }
}

private fun fa(value: String) = PersianNumbers.toPersian(value)
