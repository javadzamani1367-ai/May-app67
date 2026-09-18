package ir.ilam.inspection.ui.visit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
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
import ir.ilam.inspection.ui.common.ChoiceRow
import ir.ilam.inspection.ui.common.NumberField
import ir.ilam.inspection.ui.common.phaseTypeLabel
import ir.ilam.inspection.ui.common.tapPointLabel
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
            onSelect = { onChange(input.copy(tapPoint = it)) }
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
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            PowerSummary(input)
        }
    }
}

/** The computed load: each phase, then the totals the report carries. */
@Composable
private fun PowerSummary(input: TechnicalInput) {
    val result = input.power()
    if (!result.hasReading) {
        Text(
            text = stringResource(R.string.power_not_computed),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }
    Column {
        if (input.phaseType == PhaseType.THREE) {
            result.phases.forEachIndexed { index, phase ->
                val watt = phase.watt ?: return@forEachIndexed
                SummaryLine(
                    label = stringResource(
                        R.string.field_power_phase,
                        PHASE_NAMES.getOrElse(index) { "" }
                    ),
                    value = stringResource(R.string.unit_watt, PersianNumbers.toPersian(watt))
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
        result.totalKilowatt?.let {
            SummaryLine(
                label = stringResource(R.string.field_power_kilowatt),
                value = stringResource(R.string.unit_kilowatt, PersianNumbers.toPersian(it))
            )
        }
    }
}

@Composable
private fun SummaryLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}
