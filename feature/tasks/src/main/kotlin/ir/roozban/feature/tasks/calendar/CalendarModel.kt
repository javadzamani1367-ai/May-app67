package ir.roozban.feature.tasks.calendar

import ir.roozban.core.calendar.HijriDates
import ir.roozban.core.calendar.IranHolidays
import ir.roozban.core.calendar.JalaliDate
import ir.roozban.core.calendar.PersianDigits
import ir.roozban.core.calendar.PersianNames
import ir.roozban.core.calendar.PersianWeek
import ir.roozban.core.calendar.toJalali
import ir.roozban.core.model.Quadrant
import ir.roozban.core.model.Task
import ir.roozban.core.model.TaskDue
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

enum class CalendarMode { DAY, WEEK, MONTH }

/** A day cell / column with everything the calendar shows for it. */
data class CalendarDay(
    val date: LocalDate,
    val jalaliDay: String,
    val gregorianDay: String?,
    val hijriDay: String?,
    val isToday: Boolean,
    val inMonth: Boolean,
    val isHoliday: Boolean,
    val holidayNames: List<String>,
    val allDay: List<CalendarTask>,
    val timed: List<CalendarTask>,
)

data class CalendarTask(
    val id: String,
    val title: String,
    val start: LocalTime?,
    val durationMinutes: Int,
    val quadrant: Quadrant,
    /** Horizontal lane for overlapping timed tasks, and how many lanes share the slot. */
    val lane: Int = 0,
    val lanes: Int = 1,
)

data class CalendarUiState(
    val mode: CalendarMode = CalendarMode.WEEK,
    val anchor: LocalDate,
    val title: String = "",
    val days: List<CalendarDay> = emptyList(),
    val selected: LocalDate = anchor,
)

/** Pure building of calendar days; unit-tested. */
internal object CalendarBuilder {

    const val DEFAULT_DURATION = 30

    fun title(mode: CalendarMode, anchor: LocalDate): String {
        val j = anchor.toJalali()
        return when (mode) {
            CalendarMode.MONTH -> "${PersianNames.jalaliMonth(j.month)} ${PersianDigits.format(j.year)}"
            CalendarMode.WEEK -> {
                val start = PersianWeek.startOfWeek(anchor).toJalali()
                val end = PersianWeek.startOfWeek(anchor).plusDays(6).toJalali()
                if (start.month == end.month) {
                    "${PersianDigits.format(start.day)} تا ${PersianDigits.format(end.day)} ${PersianNames.jalaliMonth(end.month)}"
                } else {
                    "${PersianDigits.format(start.day)} ${PersianNames.jalaliMonth(start.month)} تا ${PersianDigits.format(end.day)} ${PersianNames.jalaliMonth(end.month)}"
                }
            }
            CalendarMode.DAY -> "${PersianNames.weekday(anchor.dayOfWeek)} ${PersianDigits.format(j.day)} ${PersianNames.jalaliMonth(j.month)}"
        }
    }

    /** The dates shown: one day, one Saturday-first week, or the 6-week grid around a Jalali month. */
    fun range(mode: CalendarMode, anchor: LocalDate): List<LocalDate> = when (mode) {
        CalendarMode.DAY -> listOf(anchor)
        CalendarMode.WEEK -> PersianWeek.startOfWeek(anchor).let { s -> (0L..6L).map { s.plusDays(it) } }
        CalendarMode.MONTH -> {
            val first = anchor.toJalali().firstDayOfMonth().toLocalDate()
            val start = PersianWeek.startOfWeek(first)
            (0L until 42L).map { start.plusDays(it) }
        }
    }

    fun build(
        mode: CalendarMode,
        anchor: LocalDate,
        today: LocalDate,
        tasks: List<Task>,
        showGregorian: Boolean,
        showHijri: Boolean,
        hijriOffset: Int,
    ): List<CalendarDay> {
        val month = anchor.toJalali().let { it.year to it.month }
        val byDate = tasks.filter { it.due != null }.groupBy { it.due!!.date }
        return range(mode, anchor).map { date ->
            val j = date.toJalali()
            val holidays = IranHolidays.on(date, hijriOffset)
            val dayTasks = byDate[date].orEmpty()
            CalendarDay(
                date = date,
                jalaliDay = PersianDigits.format(j.day),
                gregorianDay = if (showGregorian) PersianDigits.format(date.dayOfMonth) else null,
                hijriDay = if (showHijri) HijriDates.from(date, hijriOffset)?.let { PersianDigits.format(it.day) } else null,
                isToday = date == today,
                inMonth = mode != CalendarMode.MONTH || (j.year to j.month) == month,
                isHoliday = holidays.isNotEmpty() || date.dayOfWeek == DayOfWeek.FRIDAY,
                holidayNames = holidays.map { it.title },
                allDay = dayTasks.filter { it.due is TaskDue.AllDay }.map { it.toCalendarTask() },
                timed = layoutLanes(dayTasks.filter { it.due is TaskDue.At }.map { it.toCalendarTask() }),
            )
        }
    }

    private fun Task.toCalendarTask() = CalendarTask(
        id = id,
        title = title,
        start = (due as? TaskDue.At)?.time,
        durationMinutes = estimateMinutes ?: DEFAULT_DURATION,
        quadrant = quadrant,
    )

    /**
     * Assigns overlapping timed tasks to side-by-side lanes (greedy, by start time). Tasks in the
     * same overlap cluster share the cluster's lane count so their widths line up.
     */
    fun layoutLanes(tasks: List<CalendarTask>): List<CalendarTask> {
        val sorted = tasks.sortedWith(compareBy({ it.start }, { -it.durationMinutes }))
        val result = ArrayList<CalendarTask>()
        var cluster = ArrayList<CalendarTask>()
        var laneEnds = ArrayList<Int>()
        var clusterEnd = -1

        fun flush() {
            val lanes = laneEnds.size.coerceAtLeast(1)
            cluster.forEach { result += it.copy(lanes = lanes) }
            cluster = ArrayList()
            laneEnds = ArrayList()
        }

        for (t in sorted) {
            val start = t.start!!.toSecondOfDay() / 60
            val end = start + t.durationMinutes
            if (cluster.isNotEmpty() && start >= clusterEnd) flush()
            val lane = laneEnds.indexOfFirst { it <= start }.let { if (it == -1) laneEnds.size else it }
            if (lane == laneEnds.size) laneEnds += end else laneEnds[lane] = end
            cluster += t.copy(lane = lane)
            clusterEnd = maxOf(if (cluster.size == 1) end else clusterEnd, end)
        }
        flush()
        return result
    }

    /** Minutes since midnight → time, snapped to [snap] minutes and kept within the day. */
    fun snapTime(minutes: Float, snap: Int = 15): LocalTime {
        val snapped = (Math.round(minutes / snap) * snap).coerceIn(0, 24 * 60 - snap)
        return LocalTime.of(snapped / 60, snapped % 60)
    }

    fun shift(mode: CalendarMode, anchor: LocalDate, steps: Long): LocalDate = when (mode) {
        CalendarMode.DAY -> anchor.plusDays(steps)
        CalendarMode.WEEK -> anchor.plusWeeks(steps)
        CalendarMode.MONTH -> JalaliDate.from(anchor).plusMonths(steps).toLocalDate()
    }
}
