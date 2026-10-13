package ir.ilam.inspection.field.ui.report

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import ir.ilam.inspection.field.R
import ir.ilam.inspection.field.data.ReportDetails
import ir.ilam.inspection.field.data.ReportKeys
import ir.ilam.inspection.field.ui.form.CheckChips
import ir.ilam.inspection.field.ui.form.CountRow
import ir.ilam.inspection.field.ui.form.SubHeading
import ir.ilam.inspection.ui.common.AppTextField
import ir.ilam.inspection.ui.common.ChoiceRow
import ir.ilam.inspection.ui.common.NumberField
import ir.ilam.inspection.ui.common.SectionCard
import ir.ilam.inspection.ui.common.YesNoRow
import ir.ilam.inspection.ui.theme.Tone

/**
 * What burnt, grouped the way it stands in the network — the panel and its
 * switches, the transformer, the line down to the meter — so the user finds
 * it where they would look for it. A tap marks one; its count follows below.
 */
@Composable
fun BurnEquipment(details: ReportDetails, onEdit: ((ReportDetails) -> ReportDetails) -> Unit) {
    val selected = details.equipment.filterValues { it > 0 }.keys
    fun toggle(key: String) = onEdit {
        val count = if ((it.equipment[key] ?: 0) > 0) 0 else 1
        it.copy(equipment = it.equipment + (key to count))
    }
    SectionCard(
        title = stringResource(R.string.burn_equipment_title),
        subtitle = stringResource(R.string.burn_equipment_hint),
        icon = Icons.Filled.LocalFireDepartment,
        tone = Tone.DANGER
    ) {
        Column {
            listOf(
                R.string.burn_group_panel to ReportKeys.EQUIPMENT_PANEL,
                R.string.burn_group_transformer to ReportKeys.EQUIPMENT_TRANSFORMER,
                R.string.burn_group_line to ReportKeys.EQUIPMENT_LINE
            ).forEach { (group, keys) ->
                SubHeading(stringResource(group))
                CheckChips(
                    keys = keys,
                    selected = selected,
                    label = { stringResource(ReportLabels.EQUIPMENT.getValue(it)) },
                    onToggle = ::toggle,
                    tone = Tone.DANGER
                )
            }
            AppTextField(stringResource(R.string.burn_other_equipment), details.otherEquipment,
                { v -> onEdit { it.copy(otherEquipment = v) } })
            if (selected.isNotEmpty()) {
                SubHeading(stringResource(R.string.burn_counts))
                ReportKeys.EQUIPMENT.filter { it in selected }.forEach { key ->
                    CountRow(stringResource(ReportLabels.EQUIPMENT.getValue(key)), details.equipment[key] ?: 0) { count ->
                        onEdit { it.copy(equipment = it.equipment + (key to count)) }
                    }
                }
            }
        }
    }
}

/** When it burnt, on which phases, whether customers are without power, and the likely cause. */
@Composable
fun BurnCircumstances(details: ReportDetails, onEdit: ((ReportDetails) -> ReportDetails) -> Unit) {
    SectionCard(title = stringResource(R.string.burn_details_title), icon = Icons.Filled.WarningAmber, tone = Tone.WARNING) {
        Column {
            SubHeading(stringResource(R.string.burn_phases))
            CheckChips(
                keys = ReportKeys.PHASES,
                selected = details.phases,
                label = { stringResource(ReportLabels.PHASES.getValue(it)) },
                onToggle = { key -> onEdit { it.copy(phases = it.phases.toggled(key)) } },
                tone = Tone.WARNING
            )
            ChoiceRow(
                label = stringResource(R.string.burn_occurred),
                options = ReportKeys.OCCURRED,
                selected = details.occurred,
                optionLabel = { stringResource(ReportLabels.OCCURRED.getValue(it)) },
                onSelect = { key -> onEdit { it.copy(occurred = key) } }
            )
            YesNoRow(
                question = stringResource(R.string.burn_outage),
                answer = details.outage,
                onAnswer = { answer -> onEdit { it.copy(outage = answer) } },
                yesTone = Tone.DANGER,
                noTone = Tone.NEUTRAL
            )
            SubHeading(stringResource(R.string.burn_causes))
            CheckChips(
                keys = ReportKeys.CAUSES,
                selected = details.causes,
                label = { stringResource(ReportLabels.CAUSES.getValue(it)) },
                onToggle = { key -> onEdit { it.copy(causes = it.causes.toggled(key)) } },
                tone = Tone.WARNING
            )
            NumberField(stringResource(R.string.burn_amperage), details.amperage, { v -> onEdit { it.copy(amperage = v) } },
                decimal = true)
        }
    }
}

private fun Set<String>.toggled(key: String): Set<String> = if (key in this) this - key else this + key
