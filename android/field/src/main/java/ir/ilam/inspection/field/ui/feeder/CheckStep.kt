package ir.ilam.inspection.field.ui.feeder

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ir.ilam.inspection.field.R
import ir.ilam.inspection.field.data.FeederVerdict
import ir.ilam.inspection.field.data.PhaseCheck
import ir.ilam.inspection.ui.common.SectionCard
import ir.ilam.inspection.ui.common.StatusBadge
import ir.ilam.inspection.ui.theme.Spacing
import ir.ilam.inspection.ui.theme.Tavan
import ir.ilam.inspection.ui.theme.Tone
import ir.ilam.inspection.util.PersianNumbers
import java.util.Locale
import kotlin.math.abs

/**
 * The sum check, phase by phase and in total: the main switch, the feeders
 * added up, the difference, and what the manager allows. A mismatch says
 * exactly which phase and by how much.
 */
@Composable
fun CheckStep(verdict: FeederVerdict, tolerance: Pair<Int, Int>) {
    val (badgeText, badgeTone) = when {
        verdict.phases.isEmpty() -> R.string.feeder_check_waiting to Tone.NEUTRAL
        !verdict.possible -> R.string.feeder_check_impossible to Tone.WARNING
        verdict.mismatched -> R.string.feeder_check_mismatch to Tone.DANGER
        else -> R.string.feeder_check_ok to Tone.SUCCESS
    }
    SectionCard(
        title = stringResource(R.string.feeder_check_title),
        subtitle = stringResource(R.string.feeder_check_hint, PersianNumbers.toPersian(tolerance.first),
            PersianNumbers.toPersian(tolerance.second)),
        icon = Icons.Filled.Calculate,
        tone = badgeTone,
        trailing = { StatusBadge(text = stringResource(badgeText), tone = badgeTone) }
    ) {
        Column {
            if (verdict.phases.isNotEmpty()) {
                Header()
                val names = listOf(R.string.feeder_phase_r, R.string.feeder_phase_s, R.string.feeder_phase_t, R.string.feeder_total)
                verdict.phases.forEachIndexed { index, check ->
                    HorizontalDivider()
                    CheckRow(stringResource(names[index]), check, judged = verdict.possible)
                }
            }
            if (verdict.mismatched) {
                verdict.phases.forEachIndexed { index, check ->
                    if (!check.ok) {
                        val name = stringResource(listOf(R.string.feeder_phase_r, R.string.feeder_phase_s,
                            R.string.feeder_phase_t, R.string.feeder_total)[index])
                        Text(
                            stringResource(R.string.feeder_mismatch_line, name, amp(abs(check.difference)),
                                stringResource(if (check.difference > 0) R.string.feeder_more else R.string.feeder_less)),
                            color = Tavan.colors.danger.strong,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = Spacing.sm)
                        )
                    }
                }
            }
            if (!verdict.possible && verdict.phases.isNotEmpty()) {
                Text(stringResource(R.string.feeder_check_impossible_hint), style = MaterialTheme.typography.bodySmall,
                    color = Tavan.colors.warning.strong, modifier = Modifier.padding(top = Spacing.sm))
            }
            verdict.imbalancePct?.let { ImbalanceLine(it) }
        }
    }
}

@Composable
private fun Header() {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        listOf(R.string.feeder_col_phase, R.string.feeder_col_main, R.string.feeder_col_sum, R.string.feeder_col_diff)
            .forEach { Cell(stringResource(it), header = true) }
    }
}

@Composable
private fun CheckRow(name: String, check: PhaseCheck, judged: Boolean) {
    val color = when {
        !judged -> MaterialTheme.colorScheme.onSurface
        check.ok -> Tavan.colors.success.strong
        else -> Tavan.colors.danger.strong
    }
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Cell(name)
        Cell(amp(check.main))
        Cell(amp(check.feeders))
        Text(
            (if (check.difference > 0) "+" else "") + amp(check.difference),
            color = color,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.Cell(text: String, header: Boolean = false) {
    Text(
        text,
        style = if (header) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodyMedium,
        color = if (header) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
        modifier = Modifier.weight(1f)
    )
}

private fun amp(value: Double): String =
    PersianNumbers.toPersian(String.format(Locale.US, if (value % 1.0 == 0.0) "%.0f" else "%.1f", value))
