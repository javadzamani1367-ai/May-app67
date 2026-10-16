package ir.ilam.inspection.field

import ir.ilam.inspection.field.data.Crew
import ir.ilam.inspection.field.data.CrewSlot
import ir.ilam.inspection.field.data.FieldKind
import ir.ilam.inspection.field.data.Pick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CrewTest {

    @Test
    fun everyoneNamedTravelsAndComesBack() {
        val crew = Crew(
            lead = Pick("1", "رضا"), lineman = Pick("2", "علی"), driver = Pick(null, "حسن"),
            vehicle = "۱۲ب۳۴۵", crypto = Pick("3", "مجری"), security = Pick("4", "حراست"), illegal = Pick("5", "غیرمجاز")
        )
        val json = crew.toJson()
        assertEquals("مجری", json.getJSONObject("crypto").getString("name"))
        assertEquals("4", json.getJSONObject("security").getString("id"))
        assertEquals(crew, Crew.fromJson(json))
    }

    @Test
    fun onlyTheLeadAndTheLinemanAreRequired() {
        assertFalse(Crew(lead = Pick("1", "رضا")).complete)
        assertTrue(Crew(lead = Pick("1", "رضا"), lineman = Pick("2", "علی")).complete)
    }

    @Test
    fun eachKindHasItsOwnOptionalPeople() {
        assertEquals(listOf(CrewSlot.CRYPTO, CrewSlot.SECURITY, CrewSlot.DRIVER), CrewSlot.forKind(FieldKind.FEEDER_LINE))
        assertEquals(listOf(CrewSlot.ILLEGAL, CrewSlot.SECURITY, CrewSlot.DRIVER), CrewSlot.forKind(FieldKind.ILLEGAL))
        assertEquals(listOf(CrewSlot.CRYPTO, CrewSlot.SECURITY, CrewSlot.DRIVER), CrewSlot.forKind(FieldKind.THERMAL))
        assertEquals(listOf(CrewSlot.DRIVER), CrewSlot.forKind(FieldKind.FEEDER))
        assertFalse(CrewSlot.hasCrew(FieldKind.BURN))
        assertEquals(Pick("3", "مجری"), CrewSlot.CRYPTO.of(CrewSlot.CRYPTO.set(Crew(), Pick("3", "مجری"))))
    }
}
