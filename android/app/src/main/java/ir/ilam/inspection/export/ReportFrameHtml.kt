package ir.ilam.inspection.export

import ir.ilam.inspection.R
import ir.ilam.inspection.data.model.ReportDetail
import ir.ilam.inspection.data.model.TechnicalInput
import ir.ilam.inspection.util.PersianDate
import ir.ilam.inspection.util.PersianNumbers
import ir.ilam.inspection.util.TrackingCode

/**
 * The parts of the printed form around its seven sections: the letterhead,
 * the summary of findings that a reader looks at first, and the signature
 * block that makes it a document rather than a printout.
 *
 * Kept apart from [HtmlReportBuilder] so that file stays about the sections
 * and this one about the frame. Plain tables only: they print the same in
 * every WebView the field phones run, where newer CSS layout does not.
 */
internal class ReportFrameHtml(private val labels: ReportLabels) {

    /** Navy, as the app's header is — a form from this system is recognisably one. */
    val css: String = """
        .letterhead { background: #0B1F3A; color: #fff; padding: 10px 14px; border-radius: 6px; }
        .letterhead h1 { color: #fff; font-size: 15pt; margin: 2px 0 0; text-align: center; }
        .letterhead h2 { color: #B9CDEB; font-size: 10.5pt; margin: 0; text-align: center; font-weight: normal; }
        .strip, .summary { table-layout: fixed; }
        .strip { margin: 8px 0 4px; }
        .strip td { border: 1px solid #C9D3E0; text-align: center; padding: 5px 4px; }
        .strip .k { display: block; font-size: 8.5pt; color: #55647A; }
        .strip .v { display: block; font-size: 11pt; font-weight: bold; color: #0B1F3A; }
        .strip .code .v { direction: ltr; letter-spacing: 0.5px; }
        .summary td { border: 1px solid #C9D3E0; text-align: center; padding: 6px 4px; background: #F4F7FB; }
        .summary .v { display: block; font-size: 14pt; font-weight: bold; color: #0B1F3A; }
        .summary .alert .v { color: #B91C1C; }
        .summary .k { display: block; font-size: 8.5pt; color: #55647A; }
        .signs { margin-top: 22px; page-break-inside: avoid; }
        .signs td { border: 1px solid #C9D3E0; height: 70px; vertical-align: top; text-align: center;
                    font-size: 9.5pt; color: #33415C; width: 33%; }
    """.trimIndent()

    fun letterhead(detail: ReportDetail): String {
        val r = detail.report
        return buildString {
            append("<div class=\"letterhead\"><h2>${text(R.string.form_org)}</h2>")
            append("<h1>${text(R.string.form_title)}</h1></div>")
            append("<table class=\"strip\"><tr>")
            cell("code", text(R.string.form_tracking_code), TrackingCode.forDisplay(r.displayCode))
            cell("", text(R.string.form_report_type), labels.reportType(r.reportType))
            cell("", text(R.string.form_visit_date), r.visitDate?.let { PersianDate.format(it) } ?: "—")
            cell("", text(R.string.form_status), labels.status(r.status))
            append("</tr></table>")
        }
    }

    /**
     * Four figures a reader needs before any section: what was found, how
     * much power it draws, and how much evidence backs it.
     */
    fun summary(detail: ReportDetail, photos: Int): String {
        val measured = TechnicalInput.from(detail.report).power().totalKilowatt
        return buildString {
            append("<h3>${text(R.string.form_summary)}</h3><table class=\"summary\"><tr>")
            cell(if (detail.deviceCount > 0) "alert" else "", text(R.string.case_devices_tile), PersianNumbers.toPersian(detail.deviceCount))
            cell("", text(R.string.case_device_power_tile), labels.text(R.string.unit_watt, PersianNumbers.grouped(detail.totalPower)))
            cell("", text(R.string.case_measured_tile), measured?.let { labels.text(R.string.unit_kilowatt, PersianNumbers.toPersian(it)) } ?: "—")
            cell("", text(R.string.form_photo_count), PersianNumbers.toPersian(photos))
            append("</tr></table>")
        }
    }

    fun signatures(): String =
        "<table class=\"signs\"><tr>" +
            "<td>${text(R.string.form_sign_expert)}</td>" +
            "<td>${text(R.string.form_sign_attendee)}</td>" +
            "<td>${text(R.string.form_sign_stamp)}</td>" +
            "</tr></table>"

    private fun StringBuilder.cell(css: String, key: String, value: String) {
        append("<td class=\"$css\"><span class=\"k\">$key</span><span class=\"v\">${labels.escape(value)}</span></td>")
    }

    private fun text(res: Int): String = labels.escape(labels.text(res))
}
