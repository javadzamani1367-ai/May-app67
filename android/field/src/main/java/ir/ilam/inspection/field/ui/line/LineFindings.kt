package ir.ilam.inspection.field.ui.line

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ir.ilam.inspection.field.R
import ir.ilam.inspection.ui.common.phaseColors
import ir.ilam.inspection.field.data.LinePoint
import ir.ilam.inspection.field.data.LineSegment
import ir.ilam.inspection.field.data.LineVerdict
import ir.ilam.inspection.field.data.SegmentKind
import ir.ilam.inspection.ui.common.EmptyState
import ir.ilam.inspection.ui.common.SectionCard
import ir.ilam.inspection.ui.common.StatusBadge
import ir.ilam.inspection.ui.theme.Spacing
import ir.ilam.inspection.ui.theme.Tavan
import ir.ilam.inspection.ui.theme.Tone
import ir.ilam.inspection.util.PersianNumbers
import kotlin.math.abs

/** An ampere figure: one decimal at most, Persian digits. */
fun amps(value: Double): String = PersianNumbers.toPersian(Math.round(value * 10) / 10.0)

@Composable
fun pointName(point: LinePoint): String =
    if (point.pole == 0) stringResource(R.string.line_panel_short)
    else stringResource(R.string.line_pole_short, PersianNumbers.toPersian(point.pole))

/** The chart, then what it means: the heaviest stretch, the current left at the end, and every stretch in turn. */
@Composable
fun LineFindings(verdict: LineVerdict) {
    SectionCard(title = stringResource(R.string.line_analysis_title), icon = Icons.Filled.Insights, tone = Tone.ACCENT) {
        if (verdict.points.size < 2) {
            EmptyState(message = stringResource(R.string.line_analysis_empty), icon = Icons.Filled.Insights,
                modifier = Modifier.height(180.dp))
            return@SectionCard
        }
        Column {
            LineChart(verdict)
            verdict.heaviest?.let {
                Headline(stringResource(R.string.line_heaviest, pointName(it.from), pointName(it.to), amps(it.drop)), Tone.DANGER)
            }
            verdict.tail?.let { Headline(stringResource(R.string.line_tail, amps(it)), Tone.INFO) }
            Text(
                stringResource(R.string.line_heavy_hint, amps(verdict.heavyA)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.sm)
            )
            if (verdict.rises.isNotEmpty()) {
                Text(
                    stringResource(R.string.line_rise_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = Tavan.colors.warning.strong,
                    modifier = Modifier.padding(top = Spacing.xs)
                )
            }
        }
    }
    SectionCard(title = stringResource(R.string.line_poles_title), icon = Icons.Filled.QueryStats, tone = Tone.INFO) {
        Column {
            verdict.segments.forEachIndexed { index, segment ->
                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SegmentRow(segment, verdict.kindOf(segment))
            }
        }
    }
}

@Composable
private fun Headline(text: String, tone: Tone) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = Tavan.colors.of(tone).strong,
        modifier = Modifier.padding(top = Spacing.sm)
    )
}

/** One stretch: from where to where, what it lost (or gained), per phase, and how far apart. */
@Composable
private fun SegmentRow(segment: LineSegment, kind: SegmentKind) {
    val colors = phaseColors()
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(
                stringResource(R.string.line_segment, pointName(segment.from), pointName(segment.to)),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f)
            )
            when (kind) {
                SegmentKind.HEAVY -> StatusBadge(stringResource(R.string.line_heavy), Tone.DANGER)
                SegmentKind.RISE -> StatusBadge(stringResource(R.string.line_rise_badge), Tone.WARNING)
                SegmentKind.NORMAL -> Unit
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md), modifier = Modifier.padding(top = 4.dp)) {
            Text(
                stringResource(if (segment.drop >= 0) R.string.line_drop else R.string.line_rise) + " " + amps(abs(segment.drop)),
                style = MaterialTheme.typography.labelLarge,
                color = when (kind) {
                    SegmentKind.HEAVY -> Tavan.colors.danger.strong
                    SegmentKind.RISE -> Tavan.colors.warning.strong
                    SegmentKind.NORMAL -> MaterialTheme.colorScheme.onSurface
                }
            )
            listOf("R", "S", "T").forEachIndexed { i, name ->
                Text("$name ${amps(segment.drops[i])}", style = MaterialTheme.typography.labelMedium, color = colors[i])
            }
            segment.metres?.let {
                Text(
                    stringResource(R.string.line_metres, PersianNumbers.toPersian(it.toLong())),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
