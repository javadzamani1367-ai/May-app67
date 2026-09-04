package ir.ilam.inspection.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import ir.ilam.inspection.R
import ir.ilam.inspection.data.model.AttachmentCategory
import ir.ilam.inspection.data.model.AttendeeOrg
import ir.ilam.inspection.data.model.DispatchUnit
import ir.ilam.inspection.data.model.EntryMethod
import ir.ilam.inspection.data.model.OutputFormat
import ir.ilam.inspection.data.model.ReportStatus
import ir.ilam.inspection.data.model.ReportType

/** All user visible names for coded values, resolved from resources. */

@Composable
fun reportTypeLabel(type: ReportType): String = stringResource(
    when (type) {
        ReportType.SORAGH -> R.string.report_type_soragh
        ReportType.SYSTEM_121 -> R.string.report_type_121
        ReportType.PUBLIC -> R.string.report_type_public
        ReportType.COLLEAGUE -> R.string.report_type_colleague
        ReportType.TAVANIR -> R.string.report_type_tavanir
        ReportType.FIELD -> R.string.report_type_field
    }
)

@Composable
fun reportTypeLabel(code: Int): String = reportTypeLabel(ReportType.of(code))

@Composable
fun statusLabel(status: ReportStatus): String = stringResource(
    when (status) {
        ReportStatus.PENDING -> R.string.status_pending
        ReportStatus.VISITED -> R.string.status_visited
        ReportStatus.ARCHIVED -> R.string.status_archived
    }
)

@Composable
fun statusLabel(code: Int): String = statusLabel(ReportStatus.of(code))

@Composable
fun attendeeOrgLabel(org: AttendeeOrg): String = stringResource(
    when (org) {
        AttendeeOrg.POWER_COMPANY -> R.string.org_power_company
        AttendeeOrg.SECURITY_POLICE -> R.string.org_security_police
        AttendeeOrg.OTHER -> R.string.org_other
    }
)

@Composable
fun attachmentCategoryLabel(category: AttachmentCategory): String =
    stringArrayResource(R.array.attachment_categories).getOrElse(category.code) { "" }

@Composable
fun dispatchUnitLabel(unit: DispatchUnit): String =
    stringArrayResource(R.array.dispatch_units).getOrElse(unit.code) { "" }

@Composable
fun entryMethodLabel(method: EntryMethod): String = stringResource(
    when (method) {
        EntryMethod.BARCODE -> R.string.device_entry_barcode
        EntryMethod.MANUAL -> R.string.device_entry_manual
    }
)

@Composable
fun outputFormatLabel(format: OutputFormat): String = stringResource(
    when (format) {
        OutputFormat.PDF -> R.string.format_pdf
        OutputFormat.WORD -> R.string.format_word
    }
)
