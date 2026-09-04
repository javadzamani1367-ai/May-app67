package ir.ilam.inspection.export

import android.content.Context
import ir.ilam.inspection.R
import ir.ilam.inspection.data.model.ReportDetail
import ir.ilam.inspection.util.AppFonts
import ir.ilam.inspection.util.FileStore
import ir.ilam.inspection.util.PersianDate
import ir.ilam.inspection.util.PersianNumbers
import ir.ilam.inspection.util.TrackingCode

/**
 * Builds the visit report as HTML. WebView then prints it to PDF, which is why
 * the font is embedded as base64: right-to-left shaping, Persian digits and
 * Persian line breaking come out correct with no external library and no
 * dependency on a font being installed on the device.
 */
class HtmlReportBuilder(private val context: Context, private val files: FileStore) {

    private val labels = ReportLabels(context)

    /** [selectedMediaIds] null means every photo; used by selective dispatch. */
    fun build(
        detail: ReportDetail,
        expertName: String = "",
        selectedMediaIds: Set<String>? = null,
        selectedAttachmentIds: Set<String>? = null,
        dispatchNote: String? = null
    ): String {
        val report = detail.report
        val photos = detail.photos.filter { selectedMediaIds == null || it.id in selectedMediaIds }
        val attachments = detail.attachments
            .filter { selectedAttachmentIds == null || it.id in selectedAttachmentIds }

        return buildString {
            append("<!DOCTYPE html><html lang=\"fa\" dir=\"rtl\"><head><meta charset=\"utf-8\">")
            append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">")
            append(style())
            append("</head><body>")
            append(header(detail))
            append(section(R.string.form_section_1, rows(caseRows(detail, expertName))))
            append(section(R.string.form_section_2, rows(locationRows(detail))))
            append(section(R.string.form_section_3, rows(ownerRows(detail))))
            append(section(R.string.form_section_4, rows(technicalRows(detail))))
            append(section(R.string.form_section_5, deviceTable(detail)))
            append(section(R.string.form_section_6, attendeeTable(detail)))
            append(section(R.string.form_section_7, narrative(detail, attachments, dispatchNote)))
            if (photos.isNotEmpty()) append(photoAppendix(detail, photos))
            append("</body></html>")
        }
    }

    private fun style(): String {
        val font = AppFonts.base64(context)
        val face = if (font != null) {
            "@font-face{font-family:'Vazirmatn';src:url(data:font/ttf;base64,$font) format('truetype');}"
        } else {
            ""
        }
        return """
            <style>
            $face
            @page { size: A4; margin: 14mm 12mm; }
            body { font-family: 'Vazirmatn', 'Tahoma', sans-serif; direction: rtl; text-align: right;
                   color: #17202a; font-size: 11pt; line-height: 1.9; }
            h1 { font-size: 15pt; margin: 0; text-align: center; }
            h2 { font-size: 12pt; margin: 0 0 4px; text-align: center; color: #444; }
            h3 { font-size: 12pt; background: #eef3f5; border-right: 4px solid #00695c;
                 padding: 4px 8px; margin: 14px 0 6px; }
            table { width: 100%; border-collapse: collapse; }
            td, th { border: 1px solid #b9c2c7; padding: 4px 6px; vertical-align: top; }
            th { background: #f2f5f7; font-weight: bold; }
            td.label { background: #f7f9fa; width: 24%; color: #333; }
            .header { border-bottom: 2px solid #00695c; padding-bottom: 6px; margin-bottom: 10px; }
            .code { text-align: center; font-size: 14pt; font-weight: bold; letter-spacing: 1px; }
            .narrative { min-height: 60px; white-space: pre-wrap; }
            .sign { margin-top: 26px; text-align: left; }
            .photo { page-break-inside: avoid; margin-bottom: 12px; }
            .photo img { width: 100%; max-height: 105mm; object-fit: contain; border: 1px solid #b9c2c7; }
            .photo .caption { font-size: 9pt; color: #333; padding-top: 2px; }
            .appendix { page-break-before: always; }
            </style>
        """.trimIndent()
    }

    private fun header(detail: ReportDetail): String {
        val code = TrackingCode.forDisplay(detail.report.displayCode)
        return """
            <div class="header">
              <h2>${text(R.string.form_org)}</h2>
              <h1>${text(R.string.form_title)}</h1>
              <div class="code">${escape(code)}</div>
            </div>
        """.trimIndent()
    }

    private fun caseRows(detail: ReportDetail, expertName: String): List<Pair<String, String?>> {
        val report = detail.report
        val expert = listOfNotNull(
            expertName.ifBlank { null },
            report.expertCode?.let { PersianNumbers.toPersian(it) }
        ).joinToString(" - ")
        return listOf(
            text(R.string.form_tracking_code) to TrackingCode.forDisplay(report.displayCode),
            text(R.string.form_report_type) to reportTypeName(report.reportType),
            text(R.string.form_report_date) to PersianDate.format(report.reportDate),
            text(R.string.form_visit_date) to report.visitDate?.let { PersianDate.format(it) },
            text(R.string.form_expert) to expert,
            text(R.string.form_status) to statusName(report.status)
        )
    }

    private fun locationRows(detail: ReportDetail): List<Pair<String, String?>> {
        val r = detail.report
        val coordinates = if (r.latitude != null && r.longitude != null) {
            PersianNumbers.toPersian("%.6f , %.6f".format(r.latitude, r.longitude)) +
                (r.gpsAccuracy?.let { " (± " + PersianNumbers.toPersian(it) + ")" } ?: "")
        } else {
            null
        }
        return listOf(
            text(R.string.field_county) to r.county,
            text(R.string.field_district) to r.district,
            text(R.string.field_address) to r.address,
            text(R.string.field_postal_code) to PersianNumbers.toPersian(r.postalCode),
            text(R.string.field_file_number) to PersianNumbers.toPersian(r.fileNumber),
            text(R.string.field_bill_number) to PersianNumbers.toPersian(r.billNumber),
            text(R.string.field_subscription_number) to PersianNumbers.toPersian(r.subscriptionNumber),
            text(R.string.field_usage_type) to r.usageType,
            text(R.string.field_coordinates) to coordinates
        )
    }

    private fun ownerRows(detail: ReportDetail): List<Pair<String, String?>> {
        val r = detail.report
        return listOf(
            text(R.string.field_owner_name) to r.ownerName,
            text(R.string.field_owner_national_id) to PersianNumbers.toPersian(r.ownerNationalId),
            text(R.string.field_owner_phone) to PersianNumbers.toPersian(r.ownerPhone),
            text(R.string.field_owner_relation) to r.ownerRelation
        )
    }

    private fun technicalRows(detail: ReportDetail): List<Pair<String, String?>> {
        val r = detail.report
        return listOf(
            text(R.string.field_meter_amperage) to PersianNumbers.toPersian(r.meterAmperage),
            text(R.string.field_measured_amperage) to PersianNumbers.toPersian(r.measuredAmperage),
            text(R.string.field_connection_type) to r.connectionType,
            text(R.string.field_seal_status) to r.sealStatus
        )
    }

    private fun deviceTable(detail: ReportDetail): String {
        if (detail.devices.isEmpty()) return "<p>${text(R.string.devices_empty)}</p>"
        return buildString {
            append("<table><tr>")
            append("<th>${text(R.string.form_row)}</th>")
            append("<th>${text(R.string.device_model)}</th>")
            append("<th>${text(R.string.device_serial)}</th>")
            append("<th>${text(R.string.device_power)}</th>")
            append("<th>${text(R.string.form_entry_method)}</th>")
            append("</tr>")
            detail.devices.forEach { device ->
                append("<tr>")
                append("<td>${escape(PersianNumbers.toPersian(device.rowNumber))}</td>")
                append("<td>${escape(device.model)}</td>")
                append("<td>${escape(PersianNumbers.toPersian(device.serialNumber))}</td>")
                append("<td>${escape(PersianNumbers.toPersian(device.powerWatt))}</td>")
                append("<td>${escape(entryMethodName(device.entryMethod))}</td>")
                append("</tr>")
            }
            append("<tr><td colspan=\"3\">${text(R.string.stats_total_power)}</td>")
            append("<td colspan=\"2\">${escape(PersianNumbers.grouped(detail.totalPower))}</td></tr>")
            append("</table>")
        }
    }

    private fun attendeeTable(detail: ReportDetail): String {
        if (detail.attendees.isEmpty()) return "<p>${text(R.string.attendees_empty)}</p>"
        return buildString {
            append("<table><tr>")
            append("<th>${text(R.string.form_row)}</th>")
            append("<th>${text(R.string.attendee_name)}</th>")
            append("<th>${text(R.string.attendee_position)}</th>")
            append("<th>${text(R.string.attendee_org)}</th>")
            append("</tr>")
            detail.attendees.forEachIndexed { index, attendee ->
                append("<tr>")
                append("<td>${escape(PersianNumbers.toPersian(index + 1))}</td>")
                append("<td>${escape(attendee.fullName)}</td>")
                append("<td>${escape(attendee.position)}</td>")
                append("<td>${escape(attendee.orgName ?: organizationName(attendee.organization))}</td>")
                append("</tr>")
            }
            append("</table>")
        }
    }

    private fun narrative(
        detail: ReportDetail,
        attachments: List<ir.ilam.inspection.data.db.AttachmentEntity>,
        dispatchNote: String?
    ): String = buildString {
        append("<p class=\"narrative\">${escape(detail.report.description)}</p>")
        append("<h3>${text(R.string.field_actions_taken)}</h3>")
        append("<p class=\"narrative\">${escape(detail.report.actionsTaken)}</p>")
        if (attachments.isNotEmpty()) {
            append("<h3>${text(R.string.form_attachments)}</h3><ul>")
            attachments.forEach { attachment ->
                val category = attachmentCategoryName(attachment.category)
                append("<li>${escape(category)} - ${escape(attachment.title)}</li>")
            }
            append("</ul>")
        }
        if (!dispatchNote.isNullOrBlank()) {
            append("<h3>${text(R.string.dispatch_note)}</h3>")
            append("<p class=\"narrative\">${escape(dispatchNote)}</p>")
        }
        append("<div class=\"sign\">${text(R.string.form_signature)}</div>")
    }

    private fun photoAppendix(
        detail: ReportDetail,
        photos: List<ir.ilam.inspection.data.db.MediaEntity>
    ): String = buildString {
        append("<div class=\"appendix\"><h3>${text(R.string.form_media_appendix)}</h3>")
        photos.forEach { photo ->
            val uri = "file://" + files.resolve(photo.filePath).absolutePath
            val position = if (photo.latitude != null && photo.longitude != null) {
                PersianNumbers.toPersian("%.5f , %.5f".format(photo.latitude, photo.longitude))
            } else {
                ""
            }
            append("<div class=\"photo\"><img src=\"${escape(uri)}\">")
            append("<div class=\"caption\">")
            append(escape(photo.caption))
            append(" ")
            append(escape(PersianDate.formatWithTime(photo.capturedAt)))
            append(" ")
            append(escape(position))
            append(" ")
            append(escape(TrackingCode.forDisplay(detail.report.displayCode)))
            append("</div></div>")
        }
        append("</div>")
    }

    private fun section(titleRes: Int, body: String): String =
        "<h3>${text(titleRes)}</h3>$body"

    private fun rows(values: List<Pair<String, String?>>): String = buildString {
        append("<table>")
        values.forEach { (label, value) ->
            append("<tr><td class=\"label\">${escape(label)}</td><td>")
            append(escape(value).ifBlank { "—" })
            append("</td></tr>")
        }
        append("</table>")
    }

    private fun text(res: Int): String = escape(labels.text(res))

    private fun reportTypeName(code: Int): String = labels.reportType(code)

    private fun statusName(code: Int): String = labels.status(code)

    private fun entryMethodName(code: Int): String = labels.entryMethod(code)

    private fun organizationName(code: Int): String = labels.organization(code)

    private fun attachmentCategoryName(code: Int): String = labels.attachmentCategory(code)

    private fun escape(value: String?): String = labels.escape(value)
}
