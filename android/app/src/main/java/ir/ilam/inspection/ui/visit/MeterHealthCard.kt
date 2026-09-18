package ir.ilam.inspection.ui.visit

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import ir.ilam.inspection.R
import ir.ilam.inspection.data.model.MeterType
import ir.ilam.inspection.data.model.TariffType
import ir.ilam.inspection.data.model.TechnicalInput
import ir.ilam.inspection.ui.common.AppTextField
import ir.ilam.inspection.ui.common.ChoiceRow
import ir.ilam.inspection.ui.common.YesNoRow
import ir.ilam.inspection.ui.common.meterTypeLabel
import ir.ilam.inspection.ui.common.tariffTypeLabel

/**
 * The tariff, the kind of meter, and the four questions that decide whether
 * the meter itself was interfered with. The seal serial only appears once an
 * external seal has been confirmed — there is nothing to record otherwise.
 */
@Composable
fun MeterHealthCard(input: TechnicalInput, onChange: (TechnicalInput) -> Unit) {
    Column {
        ChoiceRow(
            label = stringResource(R.string.field_tariff_type),
            options = TariffType.entries.toList(),
            selected = input.tariffType,
            optionLabel = { tariffTypeLabel(it) },
            onSelect = { onChange(input.copy(tariffType = it)) }
        )
        ChoiceRow(
            label = stringResource(R.string.field_meter_type),
            options = MeterType.entries.toList(),
            selected = input.meterType,
            optionLabel = { meterTypeLabel(it) },
            onSelect = { onChange(input.copy(meterType = it)) }
        )
        YesNoRow(
            question = stringResource(R.string.question_seal_external),
            answer = input.sealExternal,
            onAnswer = { onChange(input.copy(sealExternal = it)) }
        )
        if (input.sealExternal == true) {
            AppTextField(
                label = stringResource(R.string.field_seal_external_serial),
                value = input.sealExternalSerial,
                onValueChange = { onChange(input.copy(sealExternalSerial = it)) }
            )
        }
        YesNoRow(
            question = stringResource(R.string.question_seal_internal),
            answer = input.sealInternal,
            onAnswer = { onChange(input.copy(sealInternal = it)) }
        )
        YesNoRow(
            question = stringResource(R.string.question_appearance_ok),
            answer = input.appearanceOk,
            onAnswer = { onChange(input.copy(appearanceOk = it)) }
        )
        YesNoRow(
            question = stringResource(R.string.question_tampered),
            answer = input.tampered,
            onAnswer = { onChange(input.copy(tampered = it)) }
        )
    }
}
