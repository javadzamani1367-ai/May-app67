package ir.ilam.inspection.ui.visit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ir.ilam.inspection.R
import ir.ilam.inspection.data.model.PhaseType
import ir.ilam.inspection.data.model.TapPoint
import ir.ilam.inspection.data.model.TechnicalInput
import ir.ilam.inspection.ui.common.BarRow
import ir.ilam.inspection.ui.common.ChoiceRow
import ir.ilam.inspection.ui.common.StatusBadge
import ir.ilam.inspection.ui.common.NumberField
import ir.ilam.inspection.ui.common.phaseTypeLabel
import ir.ilam.inspection.ui.common.tapPointLabel
import ir.ilam.inspection.ui.theme.Tavan
import ir.ilam.inspection.ui.theme.Tone
import ir.ilam.inspection.util.PersianNumbers

/** The phase names as the report prints them, latin so a meter matches. */
private val PHASE_NAMES = listOf("R", "S", "T")

/**
 * Where the supply is taken from, how many phases it has, and what each phase
 * reads. The power is never typed in — it is computed from the readings, so a
 * report cannot disagree with its own numbers.
 */
@Composable
fun PhaseMeasurementCard(input: TechnicalInput, onChange: (TechnicalInput) -> Unit) {
    Column {
        ChoiceRow(
            label = stringResource(R.string.field_tap_point),
            options = TapPoint.entries.toList(),
            selected = input.tapPoint,
            optionLabel = { tapPointLabel(it) },
            onSelect = { onChange(input.copy(tapPoint = it)) },
            // Before the meter means the load never passed through it.
            toneOf = { if (it == TapPoint.BEFORE_METER) Tone.WARNING else Tone.ACCENT }
        )
        ChoiceRow(
            label = stringResource(R.string.field_phase_type),
            options = PhaseType.entries.toList(),
            selected = input.phaseType,
            optionLabel = { phaseTypeLabel(it) },
            onSelect = { onChange(input.copy(phaseType = it)) }
        )

        val phases = input.phaseType?.phases ?: 0
        repeat(phases) { index ->
            val single = input.phaseType == PhaseType.SINGLE
            val name = PHASE_NAMES.getOrElse(index) { "" }
            if (!single) {
                StatusBadge(
                    text = stringResource(R.string.phase_label, name),
                    tone = Tone.BRAND,
                    modifier = Modifier.padding(top = 10.dp)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                NumberField(
                    label = if (single) {
                        stringResource(R.string.field_amperage)
                    } else {
                        stringResource(R.string.field_amperage_phase, name)
                    },
                    value = input.amperage.getOrElse(index) { "" },
                    onValueChange = { onChange(input.withAmperage(index, it)) },
                    modifier = Modifier.weight(1f),
                    decimal = true
                )
                NumberField(
                    label = if (single) {
                        stringResource(R.string.field_voltage)
                    } else {
                        stringResource(R.string.field_voltage_phase, name)
                    },
                    value = input.voltage.getOrElse(index) { "" },
                    onValueChange = { onChange(input.withVoltage(index, it)) },
                    modifier = Modifier.weight(1f),
                    decimal = true
                )
            }
        }

        if (phases > 0) {
            PowerSummary(input)
        }
    }
}

/**
 * The computed load, as the one figure this step exists to produce: the total
 * large, in kilowatts, and each phase's share under it.
 */
@Composable
private fun PowerSummary(input: TechnicalInput) {
    val result = input.power()
    val accent = Tavan.colors.accent
    Surface(
        color = accent.container,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Bolt, contentDescription = null, tint = accent.strong)
                Text(
                    stringResource(R.string.power_panel_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = accent.onContainer,
                    modifier = Modifier.padding(start = 6.dp)
                )
            }
            if (!result.hasReading) {
                Text(
                    text = stringResource(R.string.power_not_computed),
                    style = MaterialTheme.typography.bodySmall,
                    color = accent.onContainer,
                    modifier = Modifier.padding(top = 6.dp)
                )
                return@Column
            }
            result.totalKilowatt?.let {
                Text(
                    text = stringResource(R.string.unit_kilowatt, PersianNumbers.toPersian(it)),
                    style = MaterialTheme.typography.displaySmall,
                    color = accent.onContainer,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            if (input.phaseType == PhaseType.THREE) {
                val max = result.phases.mapNotNull { it.watt }.maxOrNull()?.takeIf { it > 0 } ?: 1.0
                result.phases.forEachIndexed { index, phase ->
                    val watt = phase.watt ?: return@forEachIndexed
                    BarRow(
                        label = stringResource(R.string.field_power_phase, PHASE_NAMES.getOrElse(index) { "" }),
                        value = stringResource(R.string.unit_watt, PersianNumbers.toPersian(watt)),
                        fraction = (watt / max).toFloat(),
                        color = accent.strong
                    )
                }
            }
            result.totalAmperage?.let {
                SummaryLine(
                    label = stringResource(R.string.field_measured_amperage),
                    value = stringResource(R.string.unit_ampere, PersianNumbers.toPersian(it))
                )
            }
            result.totalWatt?.let {
                SummaryLine(
                    label = stringResource(R.string.field_power_total),
                    value = stringResource(R.string.unit_watt, PersianNumbers.toPersian(it))
                )
            }
            Text(
                stringResource(R.string.power_panel_hint),
                style = MaterialTheme.typography.labelSmall,
                color = accent.onContainer,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}

@Composable
private fun SummaryLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(text = value, style = MaterialTheme.typography.labelLarge)
    }
}
