package ir.roozban.learning

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.random.Random

class LearningTest {
    private val now = Instant.parse("2026-09-24T12:00:00Z")
    private val zone = ZoneId.of("Asia/Tehran")

    private fun sample(cat: String?, est: Int, actual: Double, daysAgo: Long = 1) =
        EstimateSample(cat, est, actual, now.minusSeconds(daysAgo * 86400))

    @Test
    fun `estimate factors follow the data and shrink small categories`() {
        val samples = List(20) { sample("work", 30, 45.0) } + List(2) { sample("home", 30, 90.0) } + List(10) { sample(null, 60, 60.0) }
        val f = EstimateCorrection.fit(samples, now)
        assertThat(f.factorFor("work")).isWithin(0.1).of(1.45)
        // Two samples at 3× are pulled strongly toward the overall factor.
        assertThat(f.factorFor("home")).isLessThan(2.0)
        assertThat(f.factorFor("home")).isGreaterThan(f.global.value)
        assertThat(f.factorFor("unknown")).isEqualTo(f.global.value)
        assertThat(f.correct(40, "work")).isIn(com.google.common.collect.Range.closed(54, 62))
    }

    @Test
    fun `estimates stay neutral without data and ignore absurd timers`() {
        assertThat(EstimateCorrection.fit(emptyList(), now)).isEqualTo(EstimateFactors.NONE)
        val f = EstimateCorrection.fit(List(10) { sample("x", 10, 10.0 * 50) }, now)
        assertThat(f.factorFor("x")).isAtMost(4.0) // clamped
    }

    @Test
    fun `old samples weigh less`() {
        val f = EstimateCorrection.fit(List(10) { sample("a", 30, 60.0, daysAgo = 200) } + List(10) { sample("a", 30, 30.0, daysAgo = 1) }, now)
        assertThat(f.factorFor("a")).isLessThan(1.1)
    }

    @Test
    fun `productive hours find the busy window`() {
        // Completions around 9–11 Tehran time on weekdays, a few in the evening.
        val events = (1..40).map { d ->
            val day = LocalDate.of(2026, 9, 1).plusDays((d % 20).toLong())
            WorkEvent(day.atTime(9 + d % 2, 30).atZone(zone).toInstant())
        } + (1..5).map { WorkEvent(LocalDate.of(2026, 9, 10).atTime(20, 0).atZone(zone).toInstant()) } +
            WorkEvent(LocalDate.of(2026, 9, 12).atTime(9, 0).atZone(zone).toInstant(), LocalDate.of(2026, 9, 12).atTime(11, 0).atZone(zone).toInstant())
        val h = ProductiveHours.fit(events, now, zone)
        assertThat(h.bestWindow(2)!!.first).isEqualTo(9)
        assertThat(h.byHour().sum()).isWithin(1e-9).of(1.0)
        assertThat(h.score(DayOfWeek.SATURDAY, 3)).isEqualTo(0.0)
        assertThat(ProductiveHours.EMPTY.score(DayOfWeek.MONDAY, 10)).isEqualTo(0.5)
        assertThat(ProductiveHours.EMPTY.bestWindow()).isNull()
    }

    private fun features(important: Boolean, lead: Long, rate: Double = 0.2) = TaskFeatures(
        important = important, urgent = false, estimateMinutes = 30, leadDays = lead, dueDay = DayOfWeek.MONDAY,
        hasTime = false, recurring = false, hasProject = true, categoryLateRate = rate,
    )

    @Test
    fun `few samples use the Beta prior`() {
        val m = OverdueRisk.fit(List(10) { LateSample(features(false, 2), late = it < 5) })
        assertThat(m).isInstanceOf(OverdueModel.Prior::class.java)
        assertThat(m.probability(features(true, 1))).isWithin(1e-9).of((5 + 2.0) / (10 + 8.0))
    }

    @Test
    fun `logistic regression learns what makes tasks late`() {
        val r = Random(3)
        // Unimportant tasks with a long lead run late; important short-lead ones do not.
        val samples = List(200) {
            val important = r.nextBoolean()
            val lead = r.nextLong(0, 30)
            val pLate = if (!important && lead > 10) 0.8 else 0.1
            LateSample(features(important, lead), r.nextDouble() < pLate)
        }
        val m = OverdueRisk.fit(samples)
        assertThat(m).isInstanceOf(OverdueModel.Logistic::class.java)
        assertThat(m.probability(features(false, 25))).isGreaterThan(0.4)
        assertThat(m.probability(features(true, 1))).isLessThan(0.2)
        assertThat(m.probability(features(false, 25)) - m.probability(features(true, 1))).isGreaterThan(0.3)
        // Deterministic: same data, same model.
        assertThat(OverdueRisk.fit(samples).probability(features(false, 25))).isEqualTo(m.probability(features(false, 25)))
    }

    @Test
    fun `category late rates are smoothed`() {
        val rates = OverdueRisk.categoryRates(List(20) { "a" to (it < 15) } + List(20) { "b" to (it < 2) } + listOf("c" to true))
        assertThat(rates.getValue("a")).isGreaterThan(rates.getValue("b"))
        assertThat(rates.getValue("c")).isLessThan(0.7) // one sample is not proof
    }

    private val date = LocalDate.of(2026, 9, 26)

    private fun cand(id: String, minutes: Int = 30, important: Boolean = false, urgent: Boolean = false, due: LocalDate? = null, risk: Double = 0.2) =
        PlanCandidate(id, id, minutes, important, urgent, due, risk)

    @Test
    fun `plan keeps fixed blocks, orders by priority and leaves breaks`() {
        val fixed = listOf(FixedBlock("m", "meeting", LocalTime.of(10, 0), 60))
        val plan = DayPlanner.plan(
            date, LocalTime.of(9, 0), LocalTime.of(13, 0), null, fixed,
            listOf(cand("low"), cand("urgent-important", important = true, urgent = true), cand("overdue", due = date.minusDays(2))),
            ProductiveHours.EMPTY,
        )
        val order = plan.placements.sortedBy { it.start }.map { it.candidate.taskId }
        assertThat(order.first()).isEqualTo("overdue")
        assertThat(plan.placements.first { it.candidate.taskId == "overdue" }.reason).isEqualTo(PlacementReason.OVERDUE)
        // Nothing overlaps the meeting, and every task has a break after it.
        val intervals = plan.placements.map { it.start to it.end } + (LocalTime.of(10, 0) to LocalTime.of(11, 0))
        intervals.forEach { a -> intervals.filter { it !== a }.forEach { b -> assertThat(a.first < b.second && b.first < a.second).isFalse() } }
        val sorted = plan.placements.sortedBy { it.start }
        sorted.zipWithNext().forEach { (a, b) -> assertThat(b.start >= a.end.plusMinutes(5)).isTrue() }
        assertThat(plan.unplaced).isEmpty()
    }

    @Test
    fun `heavy tasks go to productive hours, today starts after now`() {
        val events = (1..30).map { WorkEvent(date.minusDays(7).atTime(16, 0).atZone(zone).toInstant()) }
        val hours = ProductiveHours.fit(events, date.atStartOfDay(zone).toInstant(), zone)
        val plan = DayPlanner.plan(date, LocalTime.of(8, 0), LocalTime.of(22, 0), LocalTime.of(9, 7), emptyList(), listOf(cand("deep", minutes = 90), cand("quick", minutes = 15)), hours)
        assertThat(plan.placements.first { it.candidate.taskId == "deep" }.start.hour).isEqualTo(16)
        assertThat(plan.placements.first { it.candidate.taskId == "quick" }.start).isEqualTo(LocalTime.of(9, 10))
    }

    @Test
    fun `what does not fit is reported`() {
        val plan = DayPlanner.plan(date, LocalTime.of(9, 0), LocalTime.of(10, 0), null, emptyList(), listOf(cand("a", 40), cand("b", 40)), ProductiveHours.EMPTY)
        assertThat(plan.placements).hasSize(1)
        assertThat(plan.unplaced).hasSize(1)
    }

    @Test
    fun `insights appear only with enough data`() {
        val none = Insights.from(EstimateFactors.NONE, ProductiveHours.EMPTY, emptyMap(), null, emptyMap())
        assertThat(none).isEmpty()
        val f = EstimateCorrection.fit(List(20) { sample("p1", 30, 50.0) }, now)
        val hours = ProductiveHours.fit(List(40) { WorkEvent(LocalDate.of(2026, 9, 20).atTime(9, 30).atZone(zone).toInstant()) }, now, zone)
        val list = Insights.from(f, hours, mapOf("p1" to 0.6), 0.25, mapOf("p1" to "گزارش‌ها"))
        assertThat(list.map { it.key }).containsAtLeast("hours", "estimate", "late:p1")
        assertThat(list.first { it.key == "hours" }.text).contains("۹")
        assertThat(list.first { it.key == "estimate" }.text).contains("برابر تخمینت")
        assertThat(list.first { it.key == "late:p1" }.text).contains("گزارش‌ها")
    }
}
