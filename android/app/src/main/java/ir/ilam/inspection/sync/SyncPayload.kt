package ir.ilam.inspection.sync

import ir.ilam.inspection.data.model.ReportDetail
import org.json.JSONArray
import org.json.JSONObject

/**
 * The wire format shared with the Windows archive. Field names are the database
 * column names on purpose: the receiver can map a row straight in, and any
 * schema change shows up on both sides at once.
 */
object SyncPayload {

    fun report(detail: ReportDetail): JSONObject {
        val r = detail.report
        val json = JSONObject()
        json.put("id", r.id)
        json.put("tracking_code", r.trackingCode)
        json.put("temp_code", r.tempCode)
        json.put("report_type", r.reportType)
        json.put("status", r.status)
        json.put("expert_code", r.expertCode)
        json.put("report_date", r.reportDate)
        json.put("visit_date", r.visitDate)
        json.put("created_at", r.createdAt)
        json.put("updated_at", r.updatedAt)
        json.put("county", r.county)
        json.put("district", r.district)
        json.put("address", r.address)
        json.put("postal_code", r.postalCode)
        json.put("latitude", r.latitude)
        json.put("longitude", r.longitude)
        json.put("gps_accuracy", r.gpsAccuracy)
        json.put("file_number", r.fileNumber)
        json.put("bill_number", r.billNumber)
        json.put("subscription_number", r.subscriptionNumber)
        json.put("usage_type", r.usageType)
        json.put("owner_name", r.ownerName)
        json.put("owner_national_id", r.ownerNationalId)
        json.put("owner_phone", r.ownerPhone)
        json.put("owner_relation", r.ownerRelation)
        json.put("meter_amperage", r.meterAmperage)
        json.put("measured_amperage", r.measuredAmperage)
        json.put("connection_type", r.connectionType)
        json.put("seal_status", r.sealStatus)
        json.put("description", r.description)
        json.put("actions_taken", r.actionsTaken)
        json.put("devices", devices(detail))
        json.put("attendees", attendees(detail))
        json.put("media", media(detail))
        json.put("attachments", attachments(detail))
        json.put("dispatches", dispatches(detail))
        return json
    }

    private fun devices(detail: ReportDetail) = JSONArray().apply {
        detail.devices.forEach { device ->
            put(
                JSONObject()
                    .put("id", device.id)
                    .put("report_id", device.reportId)
                    .put("row_number", device.rowNumber)
                    .put("model", device.model)
                    .put("serial_number", device.serialNumber)
                    .put("power_watt", device.powerWatt)
                    .put("entry_method", device.entryMethod)
                    .put("note", device.note)
            )
        }
    }

    private fun attendees(detail: ReportDetail) = JSONArray().apply {
        detail.attendees.forEach { attendee ->
            put(
                JSONObject()
                    .put("id", attendee.id)
                    .put("report_id", attendee.reportId)
                    .put("organization", attendee.organization)
                    .put("full_name", attendee.fullName)
                    .put("position", attendee.position)
                    .put("org_name", attendee.orgName)
            )
        }
    }

    private fun media(detail: ReportDetail) = JSONArray().apply {
        detail.media.forEach { item ->
            put(
                JSONObject()
                    .put("id", item.id)
                    .put("report_id", item.reportId)
                    .put("type", item.type)
                    .put("file_path", item.filePath)
                    .put("caption", item.caption)
                    .put("captured_at", item.capturedAt)
                    .put("latitude", item.latitude)
                    .put("longitude", item.longitude)
                    .put("size_bytes", item.sizeBytes)
            )
        }
    }

    private fun attachments(detail: ReportDetail) = JSONArray().apply {
        detail.attachments.forEach { attachment ->
            put(
                JSONObject()
                    .put("id", attachment.id)
                    .put("report_id", attachment.reportId)
                    .put("category", attachment.category)
                    .put("title", attachment.title)
                    .put("file_path", attachment.filePath)
                    .put("mime_type", attachment.mimeType)
                    .put("added_at", attachment.addedAt)
                    .put("note", attachment.note)
            )
        }
    }

    private fun dispatches(detail: ReportDetail) = JSONArray().apply {
        detail.dispatches.forEach { dispatch ->
            put(
                JSONObject()
                    .put("id", dispatch.id)
                    .put("report_id", dispatch.reportId)
                    .put("unit", dispatch.unit)
                    .put("included_items", dispatch.includedItems)
                    .put("note", dispatch.note)
                    .put("output_format", dispatch.outputFormat)
                    .put("dispatched_at", dispatch.dispatchedAt)
            )
        }
    }
}
