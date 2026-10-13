package ir.ilam.inspection.field

import ir.ilam.inspection.field.data.ServerInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerInfoTest {

    @Test
    fun resultWithCaseDetails() {
        val info = ServerInfo.fromJson(
            """{"result":{"outcome":"case","outcome_name":"بازدید کارشناس ثبت شد","at":1700000000000,
               "note":"","case":{"tracking_code":"RZ-1404-000012","devices":14,"total_watt":45200.5}}}"""
        )!!
        assertTrue(info.hasResult)
        assertEquals("case", info.outcome)
        assertNull("a blank note is no note", info.resultNote)
        assertEquals(14, info.case!!.devices)
        assertEquals(45200.5, info.case!!.totalWatt!!, 1e-9)
        assertEquals(1700000000000L, info.resultAt)
    }

    @Test
    fun rejectionReasonWithoutResult() {
        val info = ServerInfo.fromJson("""{"note":"تکراری است","note_at":5}""")!!
        assertFalse(info.hasResult)
        assertEquals("تکراری است", info.note)
        assertEquals(5L, info.noteAt)
    }

    @Test
    fun nothingToSayIsNull() {
        assertNull(ServerInfo.fromJson(null))
        assertNull(ServerInfo.fromJson("{}"))
        assertNull(ServerInfo.fromJson("broken"))
    }
}
