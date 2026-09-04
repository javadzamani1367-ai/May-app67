package ir.ilam.inspection.export

import android.content.Context
import ir.ilam.inspection.R
import ir.ilam.inspection.data.model.ReportDetail
import ir.ilam.inspection.util.FileStore
import ir.ilam.inspection.util.PersianDate
import ir.ilam.inspection.util.PersianNumbers
import java.io.File

/**
 * Statistics export as a real `.xlsx`, written as OOXML with inline strings so
 * no shared-string table and no spreadsheet library are needed.
 */
class ExcelExporter(private val context: Context, private val files: FileStore) {

    private val labels = ReportLabels(context)

    fun export(details: List<ReportDetail>, fileName: String, expertName: String = ""): File {
        val target = files.newExportFile(ensureExtension(fileName))
        val headers = context.resources.getStringArray(R.array.excel_headers).toList()
        val rows = mutableListOf(headers)
        details.forEach { rows.add(row(it, expertName)) }

        return OoxmlPackage(target)
            .addXml("[Content_Types].xml", CONTENT_TYPES)
            .addXml("_rels/.rels", OoxmlPackage.rootRels("xl/workbook.xml", DOCUMENT_TYPE))
            .addXml("xl/_rels/workbook.xml.rels", WORKBOOK_RELS)
            .addXml("xl/workbook.xml", workbook())
            .addXml("xl/worksheets/sheet1.xml", sheet(rows))
            .write()
    }

    private fun row(detail: ReportDetail, expertName: String): List<String> {
        val r = detail.report
        val coordinates = if (r.latitude != null && r.longitude != null) {
            PersianNumbers.toPersian("%.6f , %.6f".format(r.latitude, r.longitude))
        } else {
            ""
        }
        return listOf(
            PersianNumbers.toPersian(r.displayCode),
            labels.reportType(r.reportType),
            PersianDate.format(r.reportDate),
            r.visitDate?.let { PersianDate.format(it) }.orEmpty(),
            r.county.orEmpty(),
            r.district.orEmpty(),
            r.address.orEmpty(),
            PersianNumbers.toPersian(r.subscriptionNumber),
            PersianNumbers.toPersian(r.fileNumber),
            PersianNumbers.toPersian(r.billNumber),
            r.ownerName.orEmpty(),
            PersianNumbers.toPersian(r.meterAmperage),
            PersianNumbers.toPersian(r.measuredAmperage),
            PersianNumbers.toPersian(detail.deviceCount),
            PersianNumbers.toPersian(detail.totalPower),
            labels.status(r.status),
            listOfNotNull(
                expertName.ifBlank { null },
                PersianNumbers.toPersian(r.expertCode).ifBlank { null }
            ).joinToString(" - "),
            coordinates
        )
    }

    private fun workbook(): String =
        "<workbook xmlns=\"$SPREADSHEET_NS\" xmlns:r=\"$RELATIONSHIP_NS\"><sheets>" +
            "<sheet name=\"${labels.escape(context.getString(R.string.excel_sheet_name))}\" " +
            "sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>"

    /** Right-to-left sheet so column A sits on the right, as Persian users expect. */
    private fun sheet(rows: List<List<String>>): String = buildString {
        append("<worksheet xmlns=\"$SPREADSHEET_NS\">")
        append("<sheetViews><sheetView rightToLeft=\"1\" workbookViewId=\"0\"/></sheetViews>")
        append("<sheetData>")
        rows.forEachIndexed { rowIndex, cells ->
            append("<row r=\"${rowIndex + 1}\">")
            cells.forEachIndexed { columnIndex, value ->
                val reference = columnName(columnIndex) + (rowIndex + 1)
                append("<c r=\"$reference\" t=\"inlineStr\"><is><t xml:space=\"preserve\">")
                append(labels.escape(value))
                append("</t></is></c>")
            }
            append("</row>")
        }
        append("</sheetData></worksheet>")
    }

    private fun columnName(index: Int): String {
        var remaining = index
        val name = StringBuilder()
        while (true) {
            name.insert(0, ('A' + remaining % 26))
            remaining = remaining / 26 - 1
            if (remaining < 0) break
        }
        return name.toString()
    }

    private fun ensureExtension(fileName: String): String =
        if (fileName.endsWith(".xlsx", ignoreCase = true)) fileName else "$fileName.xlsx"

    private companion object {
        const val SPREADSHEET_NS =
            "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
        const val RELATIONSHIP_NS =
            "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
        const val DOCUMENT_TYPE =
            "http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument"

        val CONTENT_TYPES = """
            <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
              <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
              <Default Extension="xml" ContentType="application/xml"/>
              <Override PartName="/xl/workbook.xml"
                ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
              <Override PartName="/xl/worksheets/sheet1.xml"
                ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
            </Types>
        """.trimIndent()

        val WORKBOOK_RELS = """
            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
              <Relationship Id="rId1"
                Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet"
                Target="worksheets/sheet1.xml"/>
            </Relationships>
        """.trimIndent()
    }
}
