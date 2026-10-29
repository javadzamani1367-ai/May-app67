package ir.minerfinder

import ir.minerfinder.core.signature.Signature
import ir.minerfinder.core.signature.SignatureFeatures
import ir.minerfinder.signature.SignatureJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SignatureJsonTest {
    private val signature = Signature(
        "a1", "S19 farm", "Antminer S19", "next to the meter", 1_700_000_000_000, 612.5,
        listOf(612.5, 1225.0, 1837.5), DoubleArray(SignatureFeatures.BANDS) { it * 0.25 },
    )

    @Test
    fun roundTrip() {
        val back = SignatureJson.read(SignatureJson.write(listOf(signature))).single()
        assertEquals(signature.id, back.id)
        assertEquals(signature.model, back.model)
        assertEquals(612.5, back.fundamentalHz!!, 1e-9)
        assertEquals(signature.harmonicsHz, back.harmonicsHz)
        assertEquals(signature.bands.toList(), back.bands.toList())
    }

    @Test
    fun badEntriesAreSkippedAndForeignFilesRejected() {
        val text = """{"format":"minerfinder-signatures","version":1,"signatures":[{"id":"x","bands":[1,2,3]}]}"""
        assertEquals(0, SignatureJson.read(text).size)
        val noF0 = signature.copy(fundamentalHz = null)
        assertNull(SignatureJson.read(SignatureJson.write(listOf(noF0))).single().fundamentalHz)
        val rejected = runCatching { SignatureJson.read("""{"format":"other"}""") }
        assertEquals(IllegalArgumentException::class, rejected.exceptionOrNull()!!::class)
    }

    @Test
    fun entityRoundTrip() {
        val back = SignatureJson.fromEntity(SignatureJson.toEntity(signature))
        assertEquals(signature.bands.toList(), back.bands.toList())
        assertEquals(signature.harmonicsHz, back.harmonicsHz)
    }
}
