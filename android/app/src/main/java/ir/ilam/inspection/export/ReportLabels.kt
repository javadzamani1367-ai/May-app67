package ir.ilam.inspection.export

import android.content.Context
import ir.ilam.inspection.R
import ir.ilam.inspection.data.model.AttachmentCategory
import ir.ilam.inspection.data.model.AttendeeOrg
import ir.ilam.inspection.data.model.DispatchUnit
import ir.ilam.inspection.data.model.EntryMethod
import ir.ilam.inspection.data.model.MeterType
import ir.ilam.inspection.data.model.PhaseType
import ir.ilam.inspection.data.model.TapPoint
import ir.ilam.inspection.data.model.TariffType
import ir.ilam.inspection.data.model.YesNo
import ir.ilam.inspection.data.model.ReportStatus
import ir.ilam.inspection.data.model.ReportType
import ir.ilam.inspection.util.CountyLabel

/**
 * Names for coded values outside Compose — the exporters need the same words
 * the screens show, and they all come from resources.
 */
class ReportLabels(private val context: Context) {

    fun text(res: Int): String = context.getString(res)

    fun text(res: Int, argument: String): String = context.getString(res, argument)

    /** «ایلام — ناحیه ۴۰۱», for the report form and the spreadsheet. */
    fun countyWithArea(county: String?, areaCode: String?): String? =
        CountyLabel.of(county, areaCode) { name, area ->
            context.getString(R.string.county_with_code, name, area)
        }

    fun tapPoint(code: Int?): String? = TapPoint.of(code)?.let {
        context.getString(
            when (it) {
                TapPoint.BEFORE_METER -> R.string.tap_before_meter
                TapPoint.AFTER_METER -> R.string.tap_after_meter
            }
        )
    }

    fun phaseType(code: Int?): String? = PhaseType.of(code)?.let {
        context.getString(
            when (it) {
                PhaseType.SINGLE -> R.string.phase_single
                PhaseType.THREE -> R.string.phase_three
            }
        )
    }

    fun tariffType(code: Int?): String? = TariffType.of(code)?.let {
        context.resources.getStringArray(R.array.tariff_types).getOrNull(it.code)
    }

    fun meterType(code: Int?): String? = MeterType.of(code)?.let {
        context.getString(
            when (it) {
                MeterType.MECHANICAL -> R.string.meter_mechanical
                MeterType.DIGITAL -> R.string.meter_digital
            }
        )
    }

    /** An unanswered question stays blank; it is not turned into a "no". */
    fun yesNo(code: Int?): String? = YesNo.of(code)?.let {
        context.getString(if (it) R.string.answer_yes else R.string.answer_no)
    }

    fun reportType(code: Int): String = context.getString(
        when (ReportType.of(code)) {
            ReportType.SORAGH -> R.string.report_type_soragh
            ReportType.SYSTEM_121 -> R.string.report_type_121
            ReportType.PUBLIC -> R.string.report_type_public
            ReportType.COLLEAGUE -> R.string.report_type_colleague
            ReportType.TAVANIR -> R.string.report_type_tavanir
            ReportType.FIELD -> R.string.report_type_field
        }
    )

    fun status(code: Int): String = context.getString(
        when (ReportStatus.of(code)) {
            ReportStatus.PENDING -> R.string.status_pending
            ReportStatus.VISITED -> R.string.status_visited
            ReportStatus.ARCHIVED -> R.string.status_archived
        }
    )

    fun entryMethod(code: Int): String = context.getString(
        if (EntryMethod.of(code) == EntryMethod.BARCODE) {
            R.string.device_entry_barcode
        } else {
            R.string.device_entry_manual
        }
    )

    fun organization(code: Int): String = context.getString(
        when (AttendeeOrg.of(code)) {
            AttendeeOrg.POWER_COMPANY -> R.string.org_power_company
            AttendeeOrg.SECURITY_POLICE -> R.string.org_security_police
            AttendeeOrg.OTHER -> R.string.org_other
        }
    )

    fun attachmentCategory(code: Int): String = context.resources
        .getStringArray(R.array.attachment_categories)
        .getOrElse(AttachmentCategory.of(code).code) { "" }

    fun dispatchUnit(code: Int): String = context.resources
        .getStringArray(R.array.dispatch_units)
        .getOrElse(DispatchUnit.of(code).code) { "" }

    /** HTML and XML share the same five escapes. */
    fun escape(value: String?): String = value.orEmpty()
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
}
