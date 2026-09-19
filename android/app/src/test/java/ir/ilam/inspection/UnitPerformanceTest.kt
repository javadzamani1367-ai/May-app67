package ir.ilam.inspection

import ir.ilam.inspection.data.model.DispatchUnit
import ir.ilam.inspection.data.model.UnitPerformance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The manager's performance figures. A division by zero or a missing row here
 * turns into a number in an official report about how a unit is doing, so the
 * awkward cases are the ones worth testing.
 */
class UnitPerformanceTest {

    private fun row(
        unit: DispatchUnit = DispatchUnit.SALES,
        sent: Int = 0,
        seen: Int = 0,
        answered: Int = 0,
        overdue: Int = 0,
        onTime: Int = 0,
        hours: Double? = null
    ) = UnitPerformance(unit, sent, seen, answered, overdue, onTime, hours)

    @Test
    fun `answer rate is a percentage of what was sent`() {
        assertEquals(50.0, row(sent = 10, answered = 5).answerRate, 0.001)
        assertEquals(100.0, row(sent = 3, answered = 3).answerRate, 0.001)
    }

    @Test
    fun `nothing sent reads as zero, not as a crash`() {
        assertEquals(0.0, row(sent = 0, answered = 0).answerRate, 0.001)
        assertEquals(0.0, row(answered = 0, onTime = 0).onTimeRate, 0.001)
    }

    @Test
    fun `on time rate is measured against the answers, not the sends`() {
        // Twelve sent, four answered, three of those in time: 75%, not 25%.
        val performance = row(sent = 12, answered = 4, onTime = 3)
        assertEquals(75.0, performance.onTimeRate, 0.001)
    }

    @Test
    fun `every unit appears, including one nothing was sent to`() {
        val table = UnitPerformance.table(listOf(row(unit = DispatchUnit.LEGAL, sent = 2)))
        assertEquals(DispatchUnit.entries.size, table.size)
        assertEquals(DispatchUnit.entries.map { it }, table.map { it.unit })
        assertEquals(2, table.first { it.unit == DispatchUnit.LEGAL }.sent)
        assertEquals(0, table.first { it.unit == DispatchUnit.SALES }.sent)
    }

    @Test
    fun `an unmeasured average stays unknown rather than becoming zero`() {
        assertNull(row(sent = 5).averageAnswerHours)
        assertNull(UnitPerformance.empty(DispatchUnit.SECURITY).averageAnswerHours)
    }

    @Test
    fun `the table keeps the units in their stored order`() {
        val table = UnitPerformance.table(emptyList())
        assertEquals(
            listOf(0, 1, 2, 3),
            table.map { it.unit.code }
        )
    }
}
