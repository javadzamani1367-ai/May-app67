package ir.ilam.inspection.sync

import ir.ilam.inspection.data.db.AttachmentEntity
import ir.ilam.inspection.data.db.AttendeeEntity
import ir.ilam.inspection.data.db.DeviceEntity
import ir.ilam.inspection.data.db.MediaEntity
import ir.ilam.inspection.data.db.ReportEntity
import ir.ilam.inspection.data.model.ReportDetail
import org.json.JSONArray
import org.json.JSONObject

/**
 * The reading half of [SyncPayload]: server or package JSON back into entities.
 *
 * Kept apart from the writing half so neither file grows past reading size, and
 * because only the receiving side needs this — an expert's phone pushes, a
 * manager's phone pulls.
 *
 * `synced_at` is deliberately never read. It means "when this phone last sent
 * this case", so taking another device's value would make the phone believe it
 * had already sent a case it has never touched.
 */
object SyncPayloadReader {

    fun report(json: JSONObject): ReportDetail? {
        val id = json.optString("id").ifBlank { return null }
        val report = ReportEntity(
            id = id,
            trackingCode = json.text("tracking_code"),
            tempCode = json.text("temp_code"),
            reportType = json.optInt("report_type", 3),
            status = json.optInt("status", 0),
            expertCode = json.text("expert_code"),
            reportDate = json.optLong("report_date"),
            visitDate = json.number("visit_date")?.toLong(),
            createdAt = json.optLong("created_at", json.optLong("report_date")),
            updatedAt = json.optLong("updated_at"),
            syncedAt = null,
            county = json.text("county"),
            areaCode = json.text("area_code"),
            district = json.text("district"),
            address = json.text("address"),
            postalCode = json.text("postal_code"),
            latitude = json.number("latitude"),
            longitude = json.number("longitude"),
            gpsAccuracy = json.number("gps_accuracy"),
            fileNumber = json.text("file_number"),
            billNumber = json.text("bill_number"),
            subscriptionNumber = json.text("subscription_number"),
            usageType = json.text("usage_type"),
            ownerName = json.text("owner_name"),
            ownerNationalId = json.text("owner_national_id"),
            ownerPhone = json.text("owner_phone"),
            ownerRelation = json.text("owner_relation"),
            meterAmperage = json.number("meter_amperage"),
            connectionType = json.text("connection_type"),
            sealStatus = json.text("seal_status"),
            measuredAmperage = json.number("measured_amperage"),
            tapPoint = json.number("tap_point")?.toInt(),
            phaseType = json.number("phase_type")?.toInt(),
            amperageR = json.number("amperage_r"),
            amperageS = json.number("amperage_s"),
            amperageT = json.number("amperage_t"),
            voltageR = json.number("voltage_r"),
            voltageS = json.number("voltage_s"),
            voltageT = json.number("voltage_t"),
            totalWatt = json.number("total_watt"),
            tariffType = json.number("tariff_type")?.toInt(),
            meterType = json.number("meter_type")?.toInt(),
            sealExternal = json.number("seal_external")?.toInt(),
            sealExternalSerial = json.text("seal_external_serial"),
            sealInternal = json.number("seal_internal")?.toInt(),
            meterAppearanceOk = json.number("meter_appearance_ok")?.toInt(),
            meterTampered = json.number("meter_tampered")?.toInt(),
            approvalState = json.optInt("approval_state", 0),
            approvalComment = json.text("approval_comment"),
            approvalAt = json.number("approval_at")?.toLong(),
            description = json.text("description"),
            actionsTaken = json.text("actions_taken")
        )

        return ReportDetail(
            report = report,
            devices = json.rows("devices") { row ->
                DeviceEntity(
                    id = row.id() ?: return@rows null,
                    reportId = id,
                    rowNumber = row.optInt("row_number", 0),
                    model = row.text("model"),
                    serialNumber = row.text("serial_number"),
                    powerWatt = row.number("power_watt"),
                    entryMethod = row.optInt("entry_method", 1),
                    note = row.text("note")
                )
            },
            attendees = json.rows("attendees") { row ->
                AttendeeEntity(
                    id = row.id() ?: return@rows null,
                    reportId = id,
                    organization = row.optInt("organization", 2),
                    fullName = row.text("full_name"),
                    position = row.text("position"),
                    orgName = row.text("org_name")
                )
            },
            media = json.rows("media") { row ->
                MediaEntity(
                    id = row.id() ?: return@rows null,
                    reportId = id,
                    type = row.optInt("type", 0),
                    // A media row with no path is not media: the file could
                    // never be found again, and it would count towards the
                    // "at least one photo" gate while showing nothing.
                    filePath = row.text("file_path") ?: return@rows null,
                    caption = row.text("caption"),
                    capturedAt = row.optLong("captured_at"),
                    latitude = row.number("latitude"),
                    longitude = row.number("longitude"),
                    sizeBytes = row.optLong("size_bytes", 0)
                )
            },
            attachments = json.rows("attachments") { row ->
                AttachmentEntity(
                    id = row.id() ?: return@rows null,
                    reportId = id,
                    category = row.optInt("category", 8),
                    title = row.text("title"),
                    filePath = row.text("file_path") ?: return@rows null,
                    mimeType = row.text("mime_type"),
                    addedAt = row.optLong("added_at"),
                    note = row.text("note")
                )
            },
            // Dispatches are not read back. A dispatch is a record of this
            // phone handing files to a unit; the server keeps its own, richer
            // history with the unit's replies, and merging the two here would
            // double every row.
            dispatches = emptyList()
        )
    }

    private fun JSONObject.id(): String? = optString("id").ifBlank { null }

    /** Null rather than the empty string, so a blank stays blank in the database. */
    private fun JSONObject.text(key: String): String? =
        if (isNull(key)) null else optString(key).ifBlank { null }

    /** Null rather than zero: a missing voltage is not a voltage of zero. */
    private fun JSONObject.number(key: String): Double? =
        if (isNull(key) || !has(key)) null else optDouble(key).takeUnless { it.isNaN() }

    private fun <T> JSONObject.rows(key: String, build: (JSONObject) -> T?): List<T> {
        val array: JSONArray = optJSONArray(key) ?: return emptyList()
        return (0 until array.length()).mapNotNull { index ->
            array.optJSONObject(index)?.let(build)
        }
    }
}
