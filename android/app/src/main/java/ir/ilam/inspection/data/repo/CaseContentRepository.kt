package ir.ilam.inspection.data.repo

import ir.ilam.inspection.data.db.AppDatabase
import ir.ilam.inspection.data.db.AttachmentEntity
import ir.ilam.inspection.data.db.AttendeeEntity
import ir.ilam.inspection.data.db.DeviceEntity
import ir.ilam.inspection.data.db.DispatchEntity
import ir.ilam.inspection.data.db.MediaEntity
import ir.ilam.inspection.data.model.AttachmentCategory
import ir.ilam.inspection.data.model.AttendeeOrg
import ir.ilam.inspection.data.model.DispatchUnit
import ir.ilam.inspection.data.model.EntryMethod
import ir.ilam.inspection.data.model.MediaType
import ir.ilam.inspection.data.model.OutputFormat
import ir.ilam.inspection.util.FileStore
import org.json.JSONArray
import java.io.File

/**
 * Everything that hangs off a case: devices, attendees, media, attachments and
 * the dispatch log. Each write bumps the parent's `updated_at` so an edit to a
 * child is picked up by the incremental sync.
 */
class CaseContentRepository(
    private val db: AppDatabase,
    private val reports: ReportRepository,
    private val files: FileStore
) {

    // ---- devices ----------------------------------------------------------

    suspend fun addDevice(
        reportId: String,
        model: String?,
        serial: String?,
        powerWatt: Double?,
        entryMethod: EntryMethod,
        note: String?
    ): Boolean {
        val cleanSerial = serial?.trim()?.ifBlank { null }
        if (cleanSerial != null && db.deviceDao().countSerial(reportId, cleanSerial) > 0) return false
        db.deviceDao().upsert(
            DeviceEntity(
                reportId = reportId,
                rowNumber = db.deviceDao().maxRow(reportId) + 1,
                model = model?.trim()?.ifBlank { null },
                serialNumber = cleanSerial,
                powerWatt = powerWatt,
                entryMethod = entryMethod.code,
                note = note?.trim()?.ifBlank { null }
            )
        )
        touch(reportId)
        return true
    }

    suspend fun updateDevice(device: DeviceEntity) {
        db.deviceDao().update(device)
        touch(device.reportId)
    }

    suspend fun removeDevice(device: DeviceEntity) {
        db.deviceDao().delete(device)
        touch(device.reportId)
    }

    // ---- attendees --------------------------------------------------------

    suspend fun addAttendee(
        reportId: String,
        org: AttendeeOrg,
        fullName: String?,
        position: String?,
        orgName: String?
    ) {
        db.attendeeDao().upsert(
            AttendeeEntity(
                reportId = reportId,
                organization = org.code,
                fullName = fullName?.trim()?.ifBlank { null },
                position = position?.trim()?.ifBlank { null },
                orgName = orgName?.trim()?.ifBlank { null }
            )
        )
        touch(reportId)
    }

    suspend fun removeAttendee(attendee: AttendeeEntity) {
        db.attendeeDao().delete(attendee)
        touch(attendee.reportId)
    }

    // ---- media ------------------------------------------------------------

    suspend fun addMedia(
        reportId: String,
        file: File,
        type: MediaType,
        capturedAt: Long,
        latitude: Double?,
        longitude: Double?,
        caption: String? = null
    ): MediaEntity {
        val entity = MediaEntity(
            reportId = reportId,
            type = type.code,
            filePath = files.relativize(file),
            caption = caption,
            capturedAt = capturedAt,
            latitude = latitude,
            longitude = longitude,
            sizeBytes = file.length()
        )
        db.mediaDao().upsert(entity)
        touch(reportId)
        return entity
    }

    suspend fun setCaption(media: MediaEntity, caption: String) {
        db.mediaDao().update(media.copy(caption = caption.trim().ifBlank { null }))
        touch(media.reportId)
    }

    suspend fun removeMedia(media: MediaEntity) {
        db.mediaDao().delete(media)
        files.deleteQuietly(media.filePath)
        touch(media.reportId)
    }

    // ---- attachments ------------------------------------------------------

    suspend fun addAttachment(
        reportId: String,
        file: File,
        category: AttachmentCategory,
        title: String?,
        mimeType: String?,
        note: String?
    ): AttachmentEntity {
        val entity = AttachmentEntity(
            reportId = reportId,
            category = category.code,
            title = title?.trim()?.ifBlank { null },
            filePath = files.relativize(file),
            mimeType = mimeType,
            note = note?.trim()?.ifBlank { null }
        )
        db.attachmentDao().upsert(entity)
        touch(reportId)
        return entity
    }

    suspend fun removeAttachment(attachment: AttachmentEntity) {
        db.attachmentDao().delete(attachment)
        files.deleteQuietly(attachment.filePath)
        touch(attachment.reportId)
    }

    // ---- dispatch log -----------------------------------------------------

    suspend fun logDispatch(
        reportId: String,
        unit: DispatchUnit,
        includedItemIds: List<String>,
        note: String?,
        format: OutputFormat
    ) {
        val items = JSONArray().apply { includedItemIds.forEach { put(it) } }
        db.dispatchDao().upsert(
            DispatchEntity(
                reportId = reportId,
                unit = unit.code,
                includedItems = items.toString(),
                note = note?.trim()?.ifBlank { null },
                outputFormat = format.code
            )
        )
        touch(reportId)
    }

    fun includedItems(dispatch: DispatchEntity): List<String> = runCatching {
        val array = JSONArray(dispatch.includedItems)
        (0 until array.length()).map { array.getString(it) }
    }.getOrDefault(emptyList())

    private suspend fun touch(reportId: String) {
        reports.edit(reportId) { it }
    }
}
