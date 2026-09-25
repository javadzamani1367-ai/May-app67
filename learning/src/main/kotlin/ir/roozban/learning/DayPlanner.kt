package ir.roozban.learning

import java.time.LocalDate
import java.time.LocalTime

/** A timed task already on the day: kept where it is. */
data class FixedBlock(val taskId: String, val title: String, val start: LocalTime, val minutes: Int)

/** An open task without a time that could be placed on the day. */
data class PlanCandidate(
    val taskId: String,
    val title: String,
    /** Corrected estimate. */
    val minutes: Int,
    val important: Boolean,
    val urgent: Boolean,
    /** Its due date, or null for none. */
    val due: LocalDate?,
    /** 0..1 chance it runs late. */
    val risk: Double,
)

enum class PlacementReason { OVERDUE, DUE_TODAY, PRIORITY, PRODUCTIVE_HOURS, FREE_TIME }

data class Placement(val candidate: PlanCandidate, val start: LocalTime, val reason: PlacementReason) {
    val end: LocalTime get() = start.plusMinutes(candidate.minutes.toLong())
}

/** A proposed day: the user approves, edits or rejects each placement; nothing moves silently. */
data class DayPlan(
    val date: LocalDate,
    val fixed: List<FixedBlock>,
    val placements: List<Placement>,
    /** Candidates that did not fit, most important first. */
    val unplaced: List<PlanCandidate>,
)

/**
 * Greedy day planning: candidates are ranked by Eisenhower priority, due date and late risk,
 * then each goes into the free slot that suits it — heavy tasks (45+ min) into the most
 * productive hours, the rest as early as they fit — with a short break after each task.
 */
object DayPlanner {
    private const val HEAVY_MINUTES = 45

    fun plan(
        date: LocalDate,
        dayStart: LocalTime,
        dayEnd: LocalTime,
        /** For today: nothing is placed before this. */
        notBefore: LocalTime?,
        fixed: List<FixedBlock>,
        candidates: List<PlanCandidate>,
        hours: ProductiveHours,
        maxTasks: Int = 12,
    ): DayPlan {
        val start = listOfNotNull(dayStart, notBefore?.let(::roundUp)).max()
        val busy = fixed.map { it.start.toSecondOfDay() / 60 to it.start.toSecondOfDay() / 60 + it.minutes }.toMutableList()
        val placements = mutableListOf<Placement>()
        val unplaced = mutableListOf<PlanCandidate>()
        val ranked = candidates.sortedWith(compareByDescending<PlanCandidate> { score(it, date) }.thenBy { it.due ?: LocalDate.MAX }.thenBy { it.title })
        for (c in ranked) {
            if (placements.size >= maxTasks) {
                unplaced += c
                continue
            }
            val gap = if (c.minutes >= HEAVY_MINUTES) 10 else 5
            val slots = freeStarts(start.toSecondOfDay() / 60, dayEnd.toSecondOfDay() / 60, busy, c.minutes + gap)
            if (slots.isEmpty()) {
                unplaced += c
                continue
            }
            val heavy = c.minutes >= HEAVY_MINUTES
            val chosen = if (heavy && hours.total > 0) {
                slots.maxWith(compareBy<Int> { productivity(hours, date, it, c.minutes) }.thenByDescending { -it })
            } else {
                slots.first()
            }
            busy += chosen to chosen + c.minutes + gap
            placements += Placement(c, LocalTime.ofSecondOfDay(chosen * 60L), reason(c, date, heavy && hours.total > 0))
        }
        return DayPlan(date, fixed.sortedBy { it.start }, placements.sortedBy { it.start }, unplaced)
    }

    /** Higher first: overdue and due today, then the Eisenhower quadrant, then risk. */
    fun score(c: PlanCandidate, date: LocalDate): Double {
        val dueScore = when {
            c.due == null -> 0.0
            c.due < date -> 3.0
            c.due == date -> 2.0
            c.due == date.plusDays(1) -> 1.0
            else -> 0.0
        }
        val quadrant = when {
            c.important && c.urgent -> 4.0
            c.important -> 3.0
            c.urgent -> 2.0
            else -> 1.0
        }
        return dueScore * 2 + quadrant + c.risk * 2
    }

    private fun reason(c: PlanCandidate, date: LocalDate, productive: Boolean) = when {
        c.due != null && c.due < date -> PlacementReason.OVERDUE
        c.due == date -> PlacementReason.DUE_TODAY
        productive -> PlacementReason.PRODUCTIVE_HOURS
        c.important || c.urgent -> PlacementReason.PRIORITY
        else -> PlacementReason.FREE_TIME
    }

    /** Start minutes (on a 5-minute grid) where [length] minutes fit between busy intervals. */
    private fun freeStarts(from: Int, to: Int, busy: List<Pair<Int, Int>>, length: Int): List<Int> {
        val sorted = busy.sortedBy { it.first }
        val out = mutableListOf<Int>()
        var t = from
        while (t + length <= to) {
            val clash = sorted.firstOrNull { (s, e) -> t < e && s < t + length }
            if (clash == null) {
                out += t
                t += 15
            } else {
                t = roundUpMinutes(clash.second)
            }
        }
        return out
    }

    private fun productivity(hours: ProductiveHours, date: LocalDate, startMinute: Int, minutes: Int): Double {
        var sum = 0.0
        var n = 0
        var m = startMinute
        while (m < startMinute + minutes && m < 24 * 60) {
            sum += hours.score(date.dayOfWeek, m / 60)
            n++
            m += 30
        }
        return if (n == 0) 0.0 else sum / n
    }

    private fun roundUpMinutes(m: Int) = (m + 4) / 5 * 5

    private fun roundUp(t: LocalTime): LocalTime {
        val m = roundUpMinutes(t.hour * 60 + t.minute + if (t.second > 0) 1 else 0)
        return if (m >= 24 * 60) LocalTime.MAX else LocalTime.of(m / 60, m % 60)
    }
}
