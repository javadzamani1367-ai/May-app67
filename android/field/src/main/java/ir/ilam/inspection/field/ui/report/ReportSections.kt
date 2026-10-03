package ir.ilam.inspection.field.ui.report

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Memory
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import ir.ilam.inspection.field.R
import ir.ilam.inspection.field.data.Priority
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

/** What was seen at a suspected mining site. All optional; the description is what is required. */
@Composable
fun CryptoDetails(details: ReportDetails, onEdit: ((ReportDetails) -> ReportDetails) -> Unit) {
    SectionCard(title = stringResource(R.string.report_crypto_details), icon = Icons.Filled.Memory, tone = Tone.DANGER) {
        Column {
            NumberField(stringResource(R.string.report_amperage), details.amperage, { v -> onEdit { it.copy(amperage = v) } }, decimal = true)
            NumberField(stringResource(R.string.report_miner_count), details.minerCount, { v -> onEdit { it.copy(minerCount = v) } })
            AppTextField(stringResource(R.string.report_miner_type), details.minerType, { v -> onEdit { it.copy(minerType = v) } })
            SubHeading(stringResource(R.string.report_signs))
            CheckChips(
                keys = ReportKeys.SIGNS,
                selected = details.signs,
                label = { stringResource(ReportLabels.SIGNS.getValue(it)) },
                onToggle = { key -> onEdit { it.copy(signs = it.signs.toggle(key)) } },
                tone = Tone.DANGER
            )
        }
    }
}

/** High-draw appliances, kind of use, and the violation the reporter believes it is. */
@Composable
fun IllegalDetails(details: ReportDetails, onEdit: ((ReportDetails) -> ReportDetails) -> Unit) {
    SectionCard(title = stringResource(R.string.report_illegal_details), icon = Icons.Filled.Bolt, tone = Tone.WARNING) {
        Column {
            SubHeading(stringResource(R.string.report_consumers))
            ReportKeys.CONSUMERS.forEach { key ->
                CountRow(stringResource(ReportLabels.CONSUMERS.getValue(key)), details.consumers[key] ?: 0) { count ->
                    onEdit { it.copy(consumers = it.consumers + (key to count)) }
                }
            }
            AppTextField(stringResource(R.string.report_other_consumer), details.otherConsumer,
                { v -> onEdit { it.copy(otherConsumer = v) } })
            if (details.otherConsumer.isNotBlank()) {
                CountRow(details.otherConsumer, details.consumers[OTHER] ?: 0) { count ->
                    onEdit { it.copy(consumers = it.consumers + (OTHER to count)) }
                }
            }
            ChoiceRow(
                label = stringResource(R.string.report_usage),
                options = ReportKeys.USAGES,
                selected = details.usage,
                optionLabel = { stringResource(ReportLabels.USAGES.getValue(it)) },
                onSelect = { key -> onEdit { it.copy(usage = key) } }
            )
            SubHeading(stringResource(R.string.report_violations))
            CheckChips(
                keys = ReportKeys.VIOLATIONS,
                selected = details.violations,
                label = { stringResource(ReportLabels.VIOLATIONS.getValue(it)) },
                onToggle = { key -> onEdit { it.copy(violations = it.violations.toggle(key)) } },
                tone = Tone.WARNING
            )
            AppTextField(stringResource(R.string.report_meter_or_bill), details.meterOrBill,
                { v -> onEdit { it.copy(meterOrBill = v) } }, ltr = true)
        }
    }
}

/** What the collection team needs to know before setting out, and how urgent the reporter thinks it is. */
@Composable
fun TeamSection(
    details: ReportDetails,
    priority: Priority?,
    onEdit: ((ReportDetails) -> ReportDetails) -> Unit,
    onPriority: (Priority) -> Unit
) {
    SectionCard(
        title = stringResource(R.string.report_team_title),
        subtitle = stringResource(R.string.report_team_hint),
        icon = Icons.Filled.Groups,
        tone = Tone.INFO
    ) {
        Column {
            ReportKeys.TEAM_QUESTIONS.forEach { key ->
                YesNoRow(
                    question = stringResource(ReportLabels.TEAM_QUESTIONS.getValue(key)),
                    answer = details.answers[key],
                    onAnswer = { answer -> onEdit { it.copy(answers = it.answers + (key to answer)) } },
                    yesTone = Tone.INFO,
                    noTone = Tone.NEUTRAL
                )
            }
            ChoiceRow(
                label = stringResource(R.string.report_best_time),
                options = ReportKeys.BEST_TIMES,
                selected = details.bestTime,
                optionLabel = { stringResource(ReportLabels.BEST_TIMES.getValue(it)) },
                onSelect = { key -> onEdit { it.copy(bestTime = key) } }
            )
            NumberField(stringResource(R.string.report_entrances), details.entrances, { v -> onEdit { it.copy(entrances = v) } })
            AppTextField(stringResource(R.string.report_entrance_note), details.entranceNote,
                { v -> onEdit { it.copy(entranceNote = v) } })
            ChoiceRow(
                label = stringResource(R.string.report_priority),
                options = Priority.entries.toList(),
                selected = priority,
                optionLabel = { stringResource(priorityLabel(it)) },
                onSelect = onPriority,
                toneOf = { if (it == Priority.URGENT) Tone.DANGER else Tone.ACCENT }
            )
        }
    }
}

fun priorityLabel(priority: Priority): Int = when (priority) {
    Priority.URGENT -> R.string.priority_urgent
    Priority.NORMAL -> R.string.priority_normal
    Priority.LOW -> R.string.priority_low
}

private fun Set<String>.toggle(key: String): Set<String> = if (key in this) this - key else this + key

private const val OTHER = "other"
