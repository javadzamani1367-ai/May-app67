package ir.minerfinder.core

import ir.minerfinder.core.engine.DetectionEngine
import ir.minerfinder.core.sensor.SourceReading
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineTest {
    private val engine = DetectionEngine(staleAfterMs = 5_000)

    @Test
    fun weightsAndConfidenceShapeTheMean() {
        val readings = listOf(
            SourceReading("audio", 90f, 1f, 1_000),
            SourceReading("mag", 10f, 0.5f, 1_000),
        )
        val d = engine.fuse(readings, mapOf("audio" to 2f, "mag" to 1f), 1_000)
        // (2·1·90 + 1·0.5·10) / (2 + 0.5) = 74
        assertEquals(74f, d.probability, 1e-3f)
        assertEquals(2.5f / 3f, d.confidence, 1e-4f)
        assertEquals("audio", d.contributions.first().sourceId)
    }

    @Test
    fun staleAndDisabledSourcesDropOut() {
        val readings = listOf(
            SourceReading("audio", 80f, 1f, 10_000),
            SourceReading("old", 0f, 1f, 1_000),
            SourceReading("off", 0f, 1f, 10_000),
        )
        val d = engine.fuse(readings, mapOf("off" to 0f), 10_000)
        assertEquals(80f, d.probability, 1e-3f)
        assertEquals(1, d.contributions.size)
    }

    @Test
    fun nothingFreshMeansZero() {
        val d = engine.fuse(emptyList(), emptyMap(), 0)
        assertEquals(0f, d.probability)
        assertTrue(d.contributions.isEmpty())
    }
}
