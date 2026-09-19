package ir.ilam.inspection

import ir.ilam.inspection.data.db.AttachmentEntity
import ir.ilam.inspection.data.db.AttendeeEntity
import ir.ilam.inspection.data.db.DeviceEntity
import ir.ilam.inspection.data.db.DispatchEntity
import ir.ilam.inspection.data.db.MediaEntity
import ir.ilam.inspection.data.db.ReportEntity
import ir.ilam.inspection.data.model.ReportDetail
import ir.ilam.inspection.sync.SyncPayload
import ir.ilam.inspection.sync.SyncPayloadReader
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The format a case travels in, to the server and to the Windows archive.
 *
 * This is the one place where a mistake is invisible on both sides: a field
 * that fails to survive the trip leaves no error, just a case in the central
 * archive with an empty voltage or a missing owner.
 */
class SyncPayloadTest {

    private val full = ReportDetail(
        report = ReportEntity(
            id = "11111111-1111-1111-1111-111111111111",
            trackingCode = "M-401-050614-482917",
            tempCode = "M-401-050614-T0003",
            reportType = 3,
            status = 1,
            expertCode = "1042",
            reportDate = 1_757_000_000_000,
            visitDate = 1_757_600_000_000,
            createdAt = 1_756_900_000_000,
            updatedAt = 1_757_700_000_000,
            syncedAt = 1_757_650_000_000,
            county = "دره‌شهر",
            district = "ماژین",
            address = "روستای نمونه، کوچه سوم",
            postalCode = "6931234567",
            latitude = 33.4567,
            longitude = 47.1234,
            gpsAccuracy = 4.5,
            fileNumber = "پ-۱۲",
            billNumber = "8812",
            subscriptionNumber = "1234482917",
            usageType = "خانگی",
            ownerName = "نام مالک",
            ownerNationalId = "4569876543",
            ownerPhone = "09181234567",
            ownerRelation = "مالک",
            measuredAmperage = 96.0,
            tapPoint = 0,
            phaseType = 1,
            amperageR = 32.0,
            amperageS = 31.5,
            amperageT = 32.5,
            voltageR = 228.0,
            voltageS = 230.0,
            voltageT = 226.0,
            totalWatt = 21_930.0,
            tariffType = 5,
            meterType = 1,
            sealExternal = 1,
            sealExternalSerial = "SP-99",
            sealInternal = 0,
            meterAppearanceOk = 1,
            meterTampered = 1,
            approvalState = 2,
            approvalComment = "تأیید شد",
            approvalAt = 1_757_690_000_000,
            description = "شرح بازدید",
            actionsTaken = "اقدامات انجام‌شده"
        ),
        devices = listOf(
            DeviceEntity(
                id = "d1", reportId = "11111111-1111-1111-1111-111111111111",
                rowNumber = 1, model = "S19", serialNumber = "SN-1",
                powerWatt = 3250.0, entryMethod = 0, note = "یادداشت"
            )
        ),
        attendees = listOf(
            AttendeeEntity(
                id = "a1", reportId = "11111111-1111-1111-1111-111111111111",
                organization = 1, fullName = "سرگرد نمونه",
                position = "افسر", orgName = null
            )
        ),
        media = listOf(
            MediaEntity(
                id = "m1", reportId = "11111111-1111-1111-1111-111111111111",
                type = 0, filePath = "media/11111111-1111-1111-1111-111111111111/1.jpg",
                caption = "کنتور", capturedAt = 1_757_600_500_000,
                latitude = 33.4, longitude = 47.1, sizeBytes = 204_800
            )
        ),
        attachments = listOf(
            AttachmentEntity(
                id = "t1", reportId = "11111111-1111-1111-1111-111111111111",
                category = 2, title = "صورتجلسه",
                filePath = "attachments/11111111-1111-1111-1111-111111111111/m.pdf",
                mimeType = "application/pdf", addedAt = 1_757_700_000_000, note = null
            )
        )
    )

    private fun roundTrip(detail: ReportDetail): ReportDetail =
        SyncPayloadReader.report(JSONObject(SyncPayload.report(detail).toString()))!!

    @Test
    fun `every case field survives the round trip`() {
        val back = roundTrip(full).report
        // synced_at is the one field that must not travel; compared separately.
        assertEquals(full.report.copy(syncedAt = null), back)
    }

    /**
     * `synced_at` means "when this phone last sent this case". Carrying another
     * device's value would make the receiver believe it had already sent a case
     * it has never touched — and `canDelete` would then allow deleting it.
     */
    @Test
    fun `synced at does not travel`() {
        assertNotNull(full.report.syncedAt)
        assertNull(roundTrip(full).report.syncedAt)
    }

    @Test
    fun `children survive with their identities`() {
        val back = roundTrip(full)
        assertEquals(full.devices, back.devices)
        assertEquals(full.attendees, back.attendees)
        assertEquals(full.media, back.media)
        assertEquals(full.attachments, back.attachments)
    }

    /** An empty case must not come back with zeros where there were nulls. */
    @Test
    fun `absent values stay absent rather than becoming zero`() {
        val bare = ReportDetail(
            report = ReportEntity(
                id = "22222222-2222-2222-2222-222222222222",
                reportType = 6,
                reportDate = 1_757_000_000_000,
                updatedAt = 1_757_000_000_000,
                createdAt = 1_757_000_000_000
            )
        )
        val back = roundTrip(bare).report
        assertNull(back.voltageR)
        assertNull(back.latitude)
        assertNull(back.visitDate)
        assertNull(back.phaseType)
        assertNull(back.ownerName)
        assertNull(back.trackingCode)
        assertEquals(0, back.approvalState)
        assertTrue(roundTrip(bare).media.isEmpty())
    }

    /**
     * A media row with no path is dropped rather than imported. It would count
     * towards the "at least one photograph" gate while showing nothing, so a
     * case could pass the completion check with no evidence in it.
     */
    @Test
    fun `a media row without a path is not imported`() {
        val json = JSONObject(SyncPayload.report(full).toString())
        json.getJSONArray("media").getJSONObject(0).put("file_path", JSONObject.NULL)

        val back = SyncPayloadReader.report(json)!!
        assertTrue(back.media.isEmpty())
        assertEquals(1, back.attachments.size)
    }

    /**
     * Dispatches are written into the payload for the Windows archive but are
     * deliberately not read back. A dispatch records this phone handing files
     * to a unit; the server keeps its own history with the unit's replies, and
     * importing both would show every hand-off twice in a case's history.
     */
    @Test
    fun `dispatches travel out but are not imported back`() {
        val withDispatch = full.copy(
            dispatches = listOf(
                DispatchEntity(
                    id = "s1", reportId = full.report.id, unit = 2,
                    includedItems = "[\"m1\"]", note = null, outputFormat = 0,
                    dispatchedAt = 1_757_700_000_000, channel = 1,
                    deadlineAt = 1_758_000_000_000, status = 2,
                    answeredAt = 1_757_900_000_000, answer = "پاسخ واحد"
                )
            )
        )
        val written = SyncPayload.report(withDispatch)
        assertEquals(1, written.getJSONArray("dispatches").length())
        assertTrue(roundTrip(withDispatch).dispatches.isEmpty())
    }

    @Test
    fun `a payload with no id is refused`() {
        assertNull(SyncPayloadReader.report(JSONObject().put("report_type", 3)))
    }
}
