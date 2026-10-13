package ir.ilam.inspection.field.ui.report

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Place
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import ir.ilam.inspection.field.R
import ir.ilam.inspection.field.data.FieldKind
import ir.ilam.inspection.field.data.Priority
import ir.ilam.inspection.field.data.ReportDetails
import ir.ilam.inspection.field.data.ReportKeys
import ir.ilam.inspection.field.data.db.FieldFileEntity
import ir.ilam.inspection.field.data.db.FieldItemEntity
import ir.ilam.inspection.field.ui.form.HubSection
import ir.ilam.inspection.field.ui.form.SectionStatus
import ir.ilam.inspection.field.ui.form.mediaSection
import ir.ilam.inspection.field.ui.form.preview
import ir.ilam.inspection.ui.theme.Tone
import ir.ilam.inspection.util.PersianNumbers

const val PAGE_PLACE = "place"
const val PAGE_DETAILS = "details"
const val PAGE_TEAM = "team"

/** The tiles of a report, in the order the work is done on site. */
@Composable
fun reportSections(kind: FieldKind, item: FieldItemEntity, details: ReportDetails, files: List<FieldFileEntity>): List<HubSection> {
    val empty = stringResource(R.string.hub_empty)
    val hasFix = item.latitude != null && item.longitude != null
    val described = !item.description.isNullOrBlank()
    return listOf(
        HubSection(
            key = PAGE_PLACE,
            title = stringResource(R.string.place_title),
            summary = when {
                !hasFix -> stringResource(R.string.hub_position_missing)
                !described -> stringResource(R.string.missing_description)
                else -> preview(item.address) ?: preview(item.description) ?: empty
            },
            icon = Icons.Filled.Place,
            tone = Tone.INFO,
            status = if (hasFix && described) SectionStatus.DONE else SectionStatus.MISSING
        ),
        detailsSection(kind, details, empty),
        teamSection(item, details, empty),
        mediaSection(files)
    )
}

@Composable
private fun detailsSection(kind: FieldKind, details: ReportDetails, empty: String): HubSection {
    val summary: String
    val filled: Boolean
    when (kind) {
        FieldKind.BURN -> {
            val parts = ReportKeys.EQUIPMENT.mapNotNull { key ->
                val count = details.equipment[key] ?: 0
                if (count <= 0) null else stringResource(ReportLabels.EQUIPMENT.getValue(key)) +
                    (if (count > 1) " ×" + PersianNumbers.toPersian(count) else "")
            } + listOfNotNull(details.otherEquipment.takeIf { it.isNotBlank() })
            summary = parts.joinToString("، ")
            filled = details.hasEquipment
        }
        FieldKind.CRYPTO -> {
            val parts = listOfNotNull(
                details.amperage.takeIf { it.isNotBlank() }?.let { PersianNumbers.toPersian(it) + " A" },
                details.minerCount.takeIf { it.isNotBlank() }?.let { stringResource(R.string.report_miner_count) + " " + PersianNumbers.toPersian(it) },
                details.signs.size.takeIf { it > 0 }?.let { stringResource(R.string.hub_selected_count, PersianNumbers.toPersian(it)) }
            )
            summary = parts.joinToString(" · ")
            filled = parts.isNotEmpty()
        }
        else -> {
            val parts = listOfNotNull(
                details.usage?.let { ReportLabels.USAGES[it] }?.let { stringResource(it) },
                details.violations.firstNotNullOfOrNull { ReportLabels.VIOLATIONS[it] }?.let { stringResource(it) },
                details.consumers.values.sum().takeIf { it > 0 }?.let {
                    stringResource(R.string.report_consumers) + " " + PersianNumbers.toPersian(it)
                }
            )
            summary = parts.joinToString(" · ")
            filled = parts.isNotEmpty()
        }
    }
    val required = kind == FieldKind.BURN
    return HubSection(
        key = PAGE_DETAILS,
        title = stringResource(detailsTitle(kind)),
        summary = preview(summary) ?: empty,
        icon = when (kind) {
            FieldKind.CRYPTO -> Icons.Filled.Memory
            FieldKind.BURN -> Icons.Filled.LocalFireDepartment
            else -> Icons.Filled.Bolt
        },
        tone = if (kind == FieldKind.ILLEGAL) Tone.WARNING else Tone.DANGER,
        status = when {
            filled -> SectionStatus.DONE
            required -> SectionStatus.MISSING
            else -> SectionStatus.OPTIONAL
        }
    )
}

@Composable
private fun teamSection(item: FieldItemEntity, details: ReportDetails, empty: String): HubSection {
    val priority = Priority.of(item.priority)
    val parts = listOfNotNull(
        details.answers.size.takeIf { it > 0 }?.let { stringResource(R.string.hub_answers, PersianNumbers.toPersian(it)) },
        priority?.let { stringResource(R.string.hub_priority, stringResource(priorityLabel(it))) }
    )
    return HubSection(
        key = PAGE_TEAM,
        title = stringResource(R.string.report_team_title),
        summary = parts.joinToString(" · ").ifEmpty { empty },
        icon = Icons.Filled.Groups,
        tone = if (priority == Priority.URGENT) Tone.DANGER else Tone.ACCENT,
        status = if (parts.isEmpty()) SectionStatus.OPTIONAL else SectionStatus.DONE
    )
}
