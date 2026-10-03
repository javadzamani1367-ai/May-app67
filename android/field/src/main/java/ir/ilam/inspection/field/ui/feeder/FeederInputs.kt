package ir.ilam.inspection.field.ui.feeder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ElectricalServices
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import ir.ilam.inspection.field.R
import ir.ilam.inspection.field.data.FeederCheck
import ir.ilam.inspection.field.data.FeederReading
import ir.ilam.inspection.field.data.FeederState
import ir.ilam.inspection.field.data.PhaseReading
import ir.ilam.inspection.ui.common.AppTextField
import ir.ilam.inspection.ui.common.ChoiceRow
import ir.ilam.inspection.ui.common.NumberField
import ir.ilam.inspection.ui.common.SectionCard
import ir.ilam.inspection.ui.theme.Spacing
import ir.ilam.inspection.ui.theme.Tavan
import ir.ilam.inspection.ui.theme.Tone
import ir.ilam.inspection.util.PersianNumbers
import java.util.Locale

/** Three amperes, R S T, side by side; a value no panel carries is flagged where it is typed. */
@Composable
fun PhaseInputs(reading: PhaseReading, onChange: (PhaseReading) -> Unit) {
    val tooHigh = stringResource(R.string.feeder_too_high, PersianNumbers.toPersian(FeederCheck.MAX_AMPERE.toLong()))
    fun error(value: String) = if ((value.toDoubleOrNull() ?: 0.0) > FeederCheck.MAX_AMPERE) tooHigh else null
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        NumberField(stringResource(R.string.feeder_phase_r), reading.r, { onChange(reading.copy(r = it)) },
            modifier = Modifier.weight(1f), decimal = true, error = error(reading.r))
        NumberField(stringResource(R.string.feeder_phase_s), reading.s, { onChange(reading.copy(s = it)) },
            modifier = Modifier.weight(1f), decimal = true, error = error(reading.s))
        NumberField(stringResource(R.string.feeder_phase_t), reading.t, { onChange(reading.copy(t = it)) },
            modifier = Modifier.weight(1f), decimal = true, error = error(reading.t), imeAction = ImeAction.Done)
    }
}

@Composable
fun MainSwitchStep(reading: PhaseReading, onChange: (PhaseReading) -> Unit) {
    SectionCard(
        title = stringResource(R.string.feeder_main_title),
        subtitle = stringResource(R.string.feeder_main_hint),
        icon = Icons.Filled.ElectricalServices,
        tone = Tone.ACCENT
    ) {
        Column {
            PhaseInputs(reading, onChange)
            FeederCheck.imbalancePct(reading.values())?.let { ImbalanceLine(it) }
        }
    }
}

/**
 * One feeder: first what state it is in — measured, not there or switched
 * off, or not measured (and why) — then its readings. Choosing a state is
 * required, so a feeder left blank on purpose is never mistaken for a
 * forgotten one.
 */
@Composable
fun FeederStepCard(title: Int, hint: Int, reading: FeederReading, onChange: (FeederReading) -> Unit) {
    SectionCard(
        title = stringResource(title),
        subtitle = stringResource(hint),
        icon = Icons.Filled.ElectricalServices,
        tone = Tone.ACCENT
    ) {
        Column {
            ChoiceRow(
                label = stringResource(R.string.feeder_state),
                options = FeederState.entries.toList(),
                selected = reading.state,
                optionLabel = { stringResource(stateLabel(it)) },
                onSelect = { onChange(reading.copy(state = it)) },
                toneOf = { if (it == FeederState.NOT_MEASURED) Tone.WARNING else Tone.ACCENT }
            )
            when (reading.state) {
                FeederState.MEASURED -> {
                    PhaseInputs(reading.phases) { onChange(reading.copy(phases = it)) }
                    FeederCheck.imbalancePct(reading.phases.values())?.let { ImbalanceLine(it) }
                }
                FeederState.NOT_MEASURED -> AppTextField(
                    stringResource(R.string.feeder_reason), reading.reason, { onChange(reading.copy(reason = it)) },
                    error = if (reading.reason.isBlank()) stringResource(R.string.feeder_reason_required) else null
                )
                FeederState.ABSENT, null -> Unit
            }
        }
    }
}

/** Imbalance between phases: information, never a reason to refuse saving. */
@Composable
fun ImbalanceLine(percent: Double) {
    val high = percent > HIGH_IMBALANCE
    Text(
        stringResource(R.string.feeder_imbalance, PersianNumbers.toPersian(String.format(Locale.US, "%.0f", percent))),
        style = MaterialTheme.typography.bodySmall,
        color = if (high) Tavan.colors.warning.strong else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = Spacing.xs)
    )
}

fun stateLabel(state: FeederState): Int = when (state) {
    FeederState.MEASURED -> R.string.feeder_state_measured
    FeederState.ABSENT -> R.string.feeder_state_absent
    FeederState.NOT_MEASURED -> R.string.feeder_state_not_measured
}

private const val HIGH_IMBALANCE = 20.0
