package ir.ilam.inspection.export

import android.content.Context
import ir.ilam.inspection.R
import ir.ilam.inspection.data.model.ReportDetail
import ir.ilam.inspection.util.FileStore
import ir.ilam.inspection.util.PersianDate
import ir.ilam.inspection.util.PersianNumbers
import java.io.File

/**
 * The case list as a spreadsheet: one row per visit, with the columns the
 * distribution company's own reporting asks for. The file itself is written by
 * [XlsxWriter], which the unit performance report shares.
 */
class ExcelExporter(private val context: Context, private val files: FileStore) {

    private val labels = ReportLabels(context)

    fun export(details: List<ReportDetail>, fileName: String, expertName: String = ""): File {
        val target = files.newExportFile(XlsxWriter.ensureExtension(fileName))
        val headers = context.resources.getStringArray(R.array.excel_headers).toList()
        val rows = mutableListOf(headers)
        details.forEach { rows.add(row(it, expertName)) }

        return XlsxWriter(context.getString(R.string.excel_sheet_name)).write(target, rows)
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
            labels.countyWithArea(r.county, r.areaCode).orEmpty(),
            r.district.orEmpty(),
            r.address.orEmpty(),
            PersianNumbers.toPersian(r.subscriptionNumber),
            PersianNumbers.toPersian(r.fileNumber),
            PersianNumbers.toPersian(r.billNumber),
            r.ownerName.orEmpty(),
            labels.phaseType(r.phaseType).orEmpty(),
            PersianNumbers.toPersian(r.measuredAmperage),
            PersianNumbers.toPersian(r.totalWatt),
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
}
