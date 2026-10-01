package ir.ilam.inspection.field

import ir.ilam.inspection.field.data.FieldKind
import ir.ilam.inspection.field.data.FileRole
import ir.ilam.inspection.field.data.Permission
import ir.ilam.inspection.field.data.SyncState
import ir.ilam.inspection.field.data.db.FieldFileEntity
import ir.ilam.inspection.field.data.db.FieldItemEntity
import ir.ilam.inspection.field.sync.ChunkPlan
import ir.ilam.inspection.field.sync.FieldWire
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FieldSyncLogicTest {

    @Test
    fun chunksCoverTheFileExactlyOnce() {
        val size = 1_300_000L
        var received = 0L
        val ranges = mutableListOf<LongRange>()
        while (true) {
            val range = ChunkPlan.next(size, received) ?: break
            ranges += range
            received = range.last + 1
        }
        assertEquals(listOf(0L, 524_288L, 1_048_576L), ranges.map { it.first })
        assertEquals(size - 1, ranges.last().last)
        assertEquals(size, ranges.sumOf { it.last - it.first + 1 })
    }

    @Test
    fun resumesFromWhereTheServerGotTo() {
        assertEquals(700_000L..1_224_287L, ChunkPlan.next(2_000_000L, 700_000L))
        assertNull("nothing left", ChunkPlan.next(10, 10))
        assertNull("server claims more than the file", ChunkPlan.next(10, 11))
        assertNull("empty file", ChunkPlan.next(0, 0))
        assertEquals(0L..9L, ChunkPlan.next(10, 0))
    }

    @Test
    fun itemCarriesWhatTheServerValidates() {
        val item = FieldItemEntity(
            id = "11111111-2222-3333-4444-555555555555",
            kind = FieldKind.CRYPTO.code,
            createdAt = 1_759_000_000_000,
            updatedAt = 1_759_000_100_000,
            syncState = SyncState.PENDING.code,
            latitude = 33.6374,
            longitude = 46.4227,
            description = "صدای مداوم فن",
            payload = """{"signs":["fan_noise"]}"""
        )
        val json = FieldWire.item(item, "ABCD1234")
        assertEquals(item.id, json.getString("id"))
        assertEquals(1, json.getInt("kind"))
        assertEquals(1_759_000_000_000, json.getLong("created_at"))
        assertEquals("ABCD1234", json.getString("device_code"))
        assertEquals("fan_noise", json.getJSONObject("payload").getJSONArray("signs").getString(0))
        assertFalse("absent values are left out, not sent as null", json.has("plate"))
    }

    @Test
    fun filesCarryHashAndAssignment() {
        val file = FieldFileEntity(
            id = "11111111-2222-3333-4444-666666666666",
            itemId = "x",
            role = FileRole.THERMAL,
            mime = "image/jpeg",
            size = 144_917,
            sha256 = "ec81e9ebdd4037d6358ff944195e2953d8d9ceb2090b06f4af0649745d68d041",
            path = "field/x/y.jpg",
            assetType = 0,
            plate = "7781",
            locationUncertain = true
        )
        val json = FieldWire.files(listOf(file)).getJSONObject(0)
        assertEquals(file.sha256, json.getString("sha256"))
        assertEquals(0, json.getInt("asset_type"))
        assertEquals("7781", json.getString("plate"))
        assertEquals(1, json.getInt("location_uncertain"))
        assertFalse("the local path never leaves the phone", json.has("path"))
    }

    @Test
    fun permissionsAreBits() {
        assertTrue(Permission.has(3, Permission.INSPECT))
        assertTrue(Permission.has(3, Permission.REPORT))
        assertFalse(Permission.has(2, Permission.INSPECT))
        assertEquals(Permission.INSPECT, FieldKind.THERMAL.permission)
        assertEquals(Permission.REPORT, FieldKind.ILLEGAL.permission)
    }
}
