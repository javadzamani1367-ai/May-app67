package ir.ilam.inspection.export

import android.content.Context
import android.graphics.BitmapFactory
import ir.ilam.inspection.R
import ir.ilam.inspection.data.db.AttachmentEntity
import ir.ilam.inspection.data.db.MediaEntity
import ir.ilam.inspection.data.model.ReportDetail
import ir.ilam.inspection.util.FileStore
import ir.ilam.inspection.util.PersianDate
import ir.ilam.inspection.util.PersianNumbers
import ir.ilam.inspection.util.TrackingCode
import java.io.File

private const val EMU_PER_INCH = 914_400L
private const val TEXT_WIDTH_INCHES = 6.3

/**
 * Builds a `.docx` directly as OOXML. The same seven sections as the PDF, so a
 * unit that wants an editable file gets exactly the same report.
 */
class WordExporter(private val context: Context, private val files: FileStore) {

    private val labels = ReportLabels(context)

    fun export(
        detail: ReportDetail,
        fileName: String,
        expertName: String = "",
        selectedMediaIds: Set<String>? = null,
        selectedAttachmentIds: Set<String>? = null,
        dispatchNote: String? = null
    ): File {
        val photos = detail.photos.filter { selectedMediaIds == null || it.id in selectedMediaIds }
        val attachments = detail.attachments
            .filter { selectedAttachmentIds == null || it.id in selectedAttachmentIds }
        val target = files.newExportFile(ensureExtension(fileName))
        val relationships = StringBuilder()
        val body = StringBuilder()

        body.append(WordDocumentXml.paragraph(label(R.string.form_org), centered = true))
        body.append(WordDocumentXml.title(label(R.string.form_title)))
        body.append(
            WordDocumentXml.paragraph(
                TrackingCode.forDisplay(detail.report.displayCode),
                bold = true,
                centered = true
            )
        )
        body.append(section(R.string.form_section_1, caseRows(detail, expertName)))
        body.append(section(R.string.form_section_2, locationRows(detail)))
        body.append(section(R.string.form_section_3, ownerRows(detail)))
        body.append(section(R.string.form_section_4, technicalRows(detail)))
        body.append(WordDocumentXml.heading(label(R.string.form_section_5)))
        body.append(WordDocumentXml.table(deviceRows(detail)))
        body.append(WordDocumentXml.heading(label(R.string.form_section_6)))
        body.append(WordDocumentXml.table(attendeeRows(detail)))
        body.append(WordDocumentXml.heading(label(R.string.form_section_7)))
        body.append(WordDocumentXml.paragraph(escape(detail.report.description)))
        body.append(WordDocumentXml.heading(label(R.string.field_actions_taken)))
        body.append(WordDocumentXml.paragraph(escape(detail.report.actionsTaken)))
        body.append(attachmentList(attachments))
        if (!dispatchNote.isNullOrBlank()) {
            body.append(WordDocumentXml.heading(label(R.string.dispatch_note)))
            body.append(WordDocumentXml.paragraph(escape(dispatchNote)))
        }
        body.append(WordDocumentXml.paragraph(label(R.string.form_signature)))

        val pack = OoxmlPackage(target)
        if (photos.isNotEmpty()) {
            body.append(WordDocumentXml.heading(label(R.string.form_media_appendix)))
            photos.forEachIndexed { index, photo ->
                val source = files.resolve(photo.filePath)
                if (!source.exists()) return@forEachIndexed
                val relationshipId = "rIdImage$index"
                val partName = "media/photo$index.jpg"
                pack.addFile("word/$partName", source)
                relationships.append(
                    "<Relationship Id=\"$relationshipId\" " +
                        "Type=\"${WordDocumentXml.IMAGE_RELATIONSHIP_TYPE}\" Target=\"$partName\"/>"
                )
                val (width, height) = imageExtent(source)
                body.append(WordDocumentXml.image(relationshipId, index + 1, width, height))
                body.append(WordDocumentXml.paragraph(caption(detail, photo)))
            }
        }

        return pack
            .addXml("[Content_Types].xml", WordDocumentXml.contentTypes)
            .addXml(
                "_rels/.rels",
                OoxmlPackage.rootRels("word/document.xml", WordDocumentXml.DOCUMENT_RELATIONSHIP_TYPE)
            )
            .addXml(
                "word/_rels/document.xml.rels",
                "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
                    relationships + "</Relationships>"
            )
            .addXml("word/document.xml", WordDocumentXml.document(body.toString()))
            .write()
    }

    private fun section(titleRes: Int, rows: List<Pair<String, String?>>): String =
        WordDocumentXml.heading(label(titleRes)) +
            WordDocumentXml.table(rows.map { listOf(it.first, escape(it.second).ifBlank { "—" }) }, false)

    private fun caseRows(detail: ReportDetail, expertName: String): List<Pair<String, String?>> {
        val report = detail.report
        return listOf(
            label(R.string.form_tracking_code) to TrackingCode.forDisplay(report.displayCode),
            label(R.string.form_report_type) to labels.reportType(report.reportType),
            label(R.string.form_report_date) to PersianDate.format(report.reportDate),
            label(R.string.form_visit_date) to report.visitDate?.let { PersianDate.format(it) },
            label(R.string.form_expert) to listOfNotNull(
                expertName.ifBlank { null },
                PersianNumbers.toPersian(report.expertCode).ifBlank { null }
            ).joinToString(" - "),
            label(R.string.form_status) to labels.status(report.status)
        )
    }

    private fun locationRows(detail: ReportDetail): List<Pair<String, String?>> {
        val r = detail.report
        val coordinates = if (r.latitude != null && r.longitude != null) {
            PersianNumbers.toPersian("%.6f , %.6f".format(r.latitude, r.longitude))
        } else {
            null
        }
        return listOf(
            label(R.string.field_county) to r.county,
            label(R.string.field_district) to r.district,
            label(R.string.field_address) to r.address,
            label(R.string.field_postal_code) to PersianNumbers.toPersian(r.postalCode),
            label(R.string.field_file_number) to PersianNumbers.toPersian(r.fileNumber),
            label(R.string.field_bill_number) to PersianNumbers.toPersian(r.billNumber),
            label(R.string.field_subscription_number) to PersianNumbers.toPersian(r.subscriptionNumber),
            label(R.string.field_usage_type) to r.usageType,
            label(R.string.field_coordinates) to coordinates
        )
    }

    private fun ownerRows(detail: ReportDetail): List<Pair<String, String?>> {
        val r = detail.report
        return listOf(
            label(R.string.field_owner_name) to r.ownerName,
            label(R.string.field_owner_national_id) to PersianNumbers.toPersian(r.ownerNationalId),
            label(R.string.field_owner_phone) to PersianNumbers.toPersian(r.ownerPhone),
            label(R.string.field_owner_relation) to r.ownerRelation
        )
    }

    private fun technicalRows(detail: ReportDetail): List<Pair<String, String?>> {
        val r = detail.report
        return listOf(
            label(R.string.field_meter_amperage) to PersianNumbers.toPersian(r.meterAmperage),
            label(R.string.field_measured_amperage) to PersianNumbers.toPersian(r.measuredAmperage),
            label(R.string.field_connection_type) to r.connectionType,
            label(R.string.field_seal_status) to r.sealStatus
        )
    }

    private fun deviceRows(detail: ReportDetail): List<List<String>> {
        val header = listOf(
            label(R.string.form_row),
            label(R.string.device_model),
            label(R.string.device_serial),
            label(R.string.device_power),
            label(R.string.form_entry_method)
        )
        val rows = detail.devices.map { device ->
            listOf(
                PersianNumbers.toPersian(device.rowNumber),
                escape(device.model),
                PersianNumbers.toPersian(device.serialNumber),
                PersianNumbers.toPersian(device.powerWatt),
                labels.entryMethod(device.entryMethod)
            )
        }
        return listOf(header) + rows
    }

    private fun attendeeRows(detail: ReportDetail): List<List<String>> {
        val header = listOf(
            label(R.string.form_row),
            label(R.string.attendee_name),
            label(R.string.attendee_position),
            label(R.string.attendee_org)
        )
        val rows = detail.attendees.mapIndexed { index, attendee ->
            listOf(
                PersianNumbers.toPersian(index + 1),
                escape(attendee.fullName),
                escape(attendee.position),
                escape(attendee.orgName ?: labels.organization(attendee.organization))
            )
        }
        return listOf(header) + rows
    }

    private fun attachmentList(attachments: List<AttachmentEntity>): String {
        if (attachments.isEmpty()) return ""
        return WordDocumentXml.heading(label(R.string.form_attachments)) +
            attachments.joinToString("") {
                WordDocumentXml.paragraph(
                    "- " + labels.attachmentCategory(it.category) + " " + escape(it.title)
                )
            }
    }

    private fun caption(detail: ReportDetail, photo: MediaEntity): String = listOfNotNull(
        photo.caption,
        PersianDate.formatWithTime(photo.capturedAt),
        if (photo.latitude != null && photo.longitude != null) {
            PersianNumbers.toPersian("%.5f , %.5f".format(photo.latitude, photo.longitude))
        } else {
            null
        },
        TrackingCode.forDisplay(detail.report.displayCode)
    ).joinToString(" | ").let(::escape)

    /** Scales the photo to the text column, keeping its aspect ratio. */
    private fun imageExtent(source: File): Pair<Long, Long> {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(source.absolutePath, bounds)
        val width = (TEXT_WIDTH_INCHES * EMU_PER_INCH).toLong()
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return width to width
        val ratio = bounds.outHeight.toDouble() / bounds.outWidth.toDouble()
        return width to (width * ratio).toLong()
    }

    private fun label(res: Int): String = escape(labels.text(res))

    private fun escape(value: String?): String = labels.escape(value)

    private fun ensureExtension(fileName: String): String =
        if (fileName.endsWith(".docx", ignoreCase = true)) fileName else "$fileName.docx"
}
