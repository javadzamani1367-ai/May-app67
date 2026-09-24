package ir.roozban.core.calendar

import java.time.DayOfWeek
import java.time.LocalDate

/** The Iranian week runs from Saturday (index 0) to Friday (index 6). */
object PersianWeek {
    /** Days in Iranian order. */
    val DAYS: List<DayOfWeek> = listOf(
        DayOfWeek.SATURDAY, DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.TUESDAY,
        DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY,
    )

    fun indexOf(day: DayOfWeek): Int = (day.value + 1) % 7

    fun dayAt(index: Int): DayOfWeek = DAYS[Math.floorMod(index, 7)]

    fun startOfWeek(date: LocalDate): LocalDate = date.minusDays(indexOf(date.dayOfWeek).toLong())

    fun startOfWeek(date: JalaliDate): JalaliDate = date.minusDays(date.persianDayOfWeek.toLong())

    /** The date of [day] within the week that contains [date]. */
    fun dayInWeek(date: LocalDate, day: DayOfWeek): LocalDate =
        startOfWeek(date).plusDays(indexOf(day).toLong())
}
