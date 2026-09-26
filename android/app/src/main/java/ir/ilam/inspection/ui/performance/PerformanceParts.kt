package ir.ilam.inspection.ui.performance

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ir.ilam.inspection.R
import ir.ilam.inspection.data.model.UnitPerformance
import ir.ilam.inspection.ui.common.AppCard
import ir.ilam.inspection.ui.common.InfoTile
import ir.ilam.inspection.ui.common.StatusBadge
import ir.ilam.inspection.ui.common.ToneIcon
import ir.ilam.inspection.ui.common.ToneProgress
import ir.ilam.inspection.ui.common.dispatchUnitLabel
import ir.ilam.inspection.ui.dispatch.icon
import ir.ilam.inspection.ui.theme.Spacing
import ir.ilam.inspection.ui.theme.Tone
import ir.ilam.inspection.util.PersianNumbers
import java.util.Locale

/**
 * One unit's record, as a card rather than a row of an eight-column table:
 * the table did not fit a phone, and the column that fell off the edge was the
 * answers — the one that mattered most. Here the counts are four tiles and the
 * two rates are bars, so a slow unit shows from across the room.
 */
@Composable
fun UnitCard(row: UnitPerformance) {
    val answerTone = rateTone(row.answerRate, row.sent)
    AppCard(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Column(modifier = Modifier.padding(Spacing.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ToneIcon(icon = row.unit.icon(), tone = Tone.BRAND, size = 38.dp)
                Text(
                    dispatchUnitLabel(row.unit),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f).padding(horizontal = Spacing.md)
                )
                if (row.overdue > 0) {
                    StatusBadge(
                        text = stringResource(R.string.dispatch_status_overdue) + " · " + PersianNumbers.toPersian(row.overdue),
                        tone = Tone.DANGER,
                        solid = true
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = Spacing.md)) {
                Figure(R.string.dispatch_status_sent, row.sent, Tone.INFO, Modifier.weight(1f))
                Figure(R.string.dispatch_status_seen, row.seen, Tone.ACCENT, Modifier.weight(1f))
                Figure(R.string.dispatch_status_answered, row.answered, Tone.SUCCESS, Modifier.weight(1f))
            }
            Rate(stringResource(R.string.perf_answer_rate), row.answerRate, answerTone)
            Rate(stringResource(R.string.perf_on_time_rate), row.onTimeRate, rateTone(row.onTimeRate, row.answered))
            Text(
                text = row.averageAnswerHours?.let {
                    stringResource(R.string.perf_avg_hours, PersianNumbers.toPersian(String.format(Locale.US, "%.1f", it)))
                } ?: stringResource(R.string.perf_no_answers),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.sm)
            )
        }
    }
}

@Composable
private fun Figure(label: Int, value: Int, tone: Tone, modifier: Modifier) {
    InfoTile(
        label = stringResource(label),
        value = PersianNumbers.toPersian(value),
        tone = if (value > 0) tone else Tone.NEUTRAL,
        modifier = modifier
    )
}

@Composable
private fun Rate(label: String, percent: Double, tone: Tone) {
    Column(modifier = Modifier.padding(top = Spacing.sm)) {
        Row {
            Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
            Text(
                stringResource(R.string.perf_percent, PersianNumbers.toPersian(String.format(Locale.US, "%.0f", percent))),
                style = MaterialTheme.typography.labelLarge
            )
        }
        ToneProgress(fraction = (percent / 100).toFloat(), tone = tone, modifier = Modifier.padding(top = 4.dp))
    }
}

/** Green when most is answered, amber half way, red below that; grey when there is nothing to judge. */
private fun rateTone(percent: Double, base: Int): Tone = when {
    base == 0 -> Tone.NEUTRAL
    percent >= 75 -> Tone.SUCCESS
    percent >= 40 -> Tone.WARNING
    else -> Tone.DANGER
}
