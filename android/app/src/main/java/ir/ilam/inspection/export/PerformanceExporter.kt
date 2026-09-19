package ir.ilam.inspection.export

import android.content.Context
import ir.ilam.inspection.R
import ir.ilam.inspection.data.model.UnitPerformance
import ir.ilam.inspection.data.repo.PerformanceReport
import ir.ilam.inspection.data.repo.PerformanceSource
import ir.ilam.inspection.util.FileStore
import ir.ilam.inspection.util.PersianDate
import ir.ilam.inspection.util.PersianNumbers
import java.io.File

/**
 * The manager's report on how the units are performing, in the three formats
 * the office actually passes around: a spreadsheet to sort and filter, a Word
 * file to paste into a letter, and a PDF to send as it stands.
 *
 * All three are built from the same rows and the same headers, so the numbers
 * cannot disagree between two copies of the same report.
 */
class PerformanceExporter(private val context: Context, private val files: FileStore) {

    private val labels = ReportLabels(context)

    fun excel(report: PerformanceReport, fileName: String): File {
        val target = files.newExportFile(XlsxWriter.ensureExtension(fileName))
        val rows = mutableListOf(headers())
        report.rows.forEach { rows.add(cells(it)) }
        rows.add(totals(report))
        return XlsxWriter(text(R.string.performance_title)).write(target, rows)
    }

    fun word(report: PerformanceReport, fileName: String): File {
        val target = files.newExportFile(
            if (fileName.endsWith(".docx", true)) fileName else "$fileName.docx"
        )
        val body = buildString {
            append(WordDocumentXml.title(text(R.string.performance_title)))
            append(WordDocumentXml.paragraph(period(report)))
            append(WordDocumentXml.paragraph(sourceNote(report)))
            append(WordDocumentXml.table(listOf(headers()) + report.rows.map { cells(it) } + listOf(totals(report))))
        }
        return DocxWriter().write(target, WordDocumentXml.document(body))
    }

    /** HTML for the PDF path: the same table, styled for print. */
    fun html(report: PerformanceReport): String = buildString {
        append("<!DOCTYPE html><html lang=\"fa\" dir=\"rtl\"><head><meta charset=\"utf-8\">")
        append(style())
        append("</head><body>")
        append("<h1>${escape(text(R.string.performance_title))}</h1>")
        append("<p class=\"meta\">${escape(period(report))}</p>")
        append("<p class=\"meta\">${escape(sourceNote(report))}</p>")
        append("<table><thead><tr>")
        headers().forEach { append("<th>${escape(it)}</th>") }
        append("</tr></thead><tbody>")
        report.rows.forEach { row ->
            append("<tr>")
            cells(row).forEachIndexed { index, value ->
                val overdue = index == OVERDUE_COLUMN && row.overdue > 0
                append(if (overdue) "<td class=\"warn\">" else "<td>")
                append(escape(value))
                append("</td>")
            }
            append("</tr>")
        }
        append("<tr class=\"total\">")
        totals(report).forEach { append("<td>${escape(it)}</td>") }
        append("</tr></tbody></table>")
        append("</body></html>")
    }

    private fun headers(): List<String> =
        context.resources.getStringArray(R.array.performance_headers).toList()

    private fun cells(row: UnitPerformance): List<String> = listOf(
        labels.dispatchUnit(row.unit.code),
        PersianNumbers.toPersian(row.sent),
        PersianNumbers.toPersian(row.seen),
        PersianNumbers.toPersian(row.answered),
        PersianNumbers.toPersian(row.overdue),
        PersianNumbers.toPersian("%.1f".format(row.answerRate)) + "٪",
        PersianNumbers.toPersian("%.1f".format(row.onTimeRate)) + "٪",
        row.averageAnswerHours?.let { PersianNumbers.toPersian("%.1f".format(it)) } ?: "—"
    )

    /** A totals row, because the first question asked of any such table is the sum. */
    private fun totals(report: PerformanceReport): List<String> {
        val sent = report.rows.sumOf { it.sent }
        val answered = report.rows.sumOf { it.answered }
        val rate = if (sent == 0) 0.0 else (answered.toDouble() / sent) * 100
        return listOf(
            text(R.string.performance_total),
            PersianNumbers.toPersian(sent),
            PersianNumbers.toPersian(report.rows.sumOf { it.seen }),
            PersianNumbers.toPersian(answered),
            PersianNumbers.toPersian(report.rows.sumOf { it.overdue }),
            PersianNumbers.toPersian("%.1f".format(rate)) + "٪",
            "—",
            "—"
        )
    }

    private fun period(report: PerformanceReport): String = context.getString(
        R.string.performance_period,
        PersianDate.format(report.from),
        PersianDate.format(report.to)
    )

    /**
     * The report says where its figures came from. A manager comparing two
     * copies has to be able to tell the complete one from the one a single
     * phone produced with no connection.
     */
    private fun sourceNote(report: PerformanceReport): String = text(
        if (report.source == PerformanceSource.SERVER) {
            R.string.performance_source_server
        } else {
            R.string.performance_source_local
        }
    )

    private fun style(): String = """
        <style>
          @page { size: A4 landscape; margin: 12mm; }
          body { font-family: 'Vazirmatn', sans-serif; direction: rtl; color: #12212f; }
          h1 { font-size: 17pt; text-align: center; margin: 0 0 6px; }
          .meta { font-size: 10pt; color: #5b6b7c; margin: 2px 0; text-align: center; }
          table { width: 100%; border-collapse: collapse; margin-top: 12px; font-size: 11pt; }
          th, td { border: 1px solid #b9c4cf; padding: 7px; text-align: center; }
          th { background: #eef2f6; }
          tr.total td { background: #f6f8fa; font-weight: bold; }
          td.warn { background: #fde4e4; color: #8a1616; font-weight: bold; }
        </style>
    """.trimIndent()

    private fun text(res: Int): String = context.getString(res)

    private fun escape(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")

    private companion object {
        /** Index of the overdue column, which is coloured when it is not zero. */
        const val OVERDUE_COLUMN = 4
    }
}
