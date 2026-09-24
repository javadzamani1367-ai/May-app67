package ir.roozban.core.calendar

import java.time.LocalDate

data class Holiday(
    val date: LocalDate,
    val title: String,
    /**
     * Lunar holidays are computed from the Umm al-Qura calendar plus the user's offset; Iran's
     * official dates follow moon sighting and can differ by a day, hence "approximate".
     */
    val approximate: Boolean,
)

/**
 * Official public holidays of Iran.
 *
 * Solar holidays are fixed Jalali dates and exact. Lunar holidays are derived from the Hijri
 * calendar with [hijriOffset] (the user's setting). [overrides] lets a downloadable data file
 * correct or add dates (e.g. once the official calendar for a year is published).
 */
object IranHolidays {

    data class Overrides(
        val added: List<Holiday> = emptyList(),
        /** Epoch days whose computed holidays are removed. */
        val removed: Set<Long> = emptySet(),
    )

    private val SOLAR = listOf(
        Triple(1, 1, "عید نوروز"),
        Triple(1, 2, "عید نوروز"),
        Triple(1, 3, "عید نوروز"),
        Triple(1, 4, "عید نوروز"),
        Triple(1, 12, "روز جمهوری اسلامی"),
        Triple(1, 13, "روز طبیعت"),
        Triple(3, 14, "رحلت امام خمینی"),
        Triple(3, 15, "قیام ۱۵ خرداد"),
        Triple(11, 22, "پیروزی انقلاب اسلامی"),
        Triple(12, 29, "ملی شدن صنعت نفت"),
    )

    /** (Hijri month, day, title). Day -1 = last day of the month. */
    private val LUNAR = listOf(
        Triple(1, 9, "تاسوعای حسینی"),
        Triple(1, 10, "عاشورای حسینی"),
        Triple(2, 20, "اربعین حسینی"),
        Triple(2, 28, "رحلت پیامبر و شهادت امام حسن مجتبی"),
        Triple(2, -1, "شهادت امام رضا"),
        Triple(3, 8, "شهادت امام حسن عسکری"),
        Triple(3, 17, "میلاد پیامبر و امام جعفر صادق"),
        Triple(6, 3, "شهادت حضرت فاطمه"),
        Triple(7, 13, "ولادت امام علی"),
        Triple(7, 27, "مبعث پیامبر"),
        Triple(8, 15, "ولادت حضرت قائم"),
        Triple(9, 21, "شهادت امام علی"),
        Triple(10, 1, "عید فطر"),
        Triple(10, 2, "تعطیل به مناسبت عید فطر"),
        Triple(10, 25, "شهادت امام جعفر صادق"),
        Triple(12, 10, "عید قربان"),
        Triple(12, 18, "عید غدیر خم"),
    )

    private val cache = HashMap<Pair<Int, Int>, List<Holiday>>()

    /** All holidays of a Jalali year, ordered by date. */
    fun forJalaliYear(year: Int, hijriOffset: Int = 0, overrides: Overrides = Overrides()): List<Holiday> {
        val computed = synchronized(cache) { cache.getOrPut(year to hijriOffset) { compute(year, hijriOffset) } }
        val start = JalaliDate.of(year, 1, 1).toLocalDate()
        val end = JalaliDate.of(year, 12, 1).lastDayOfMonth().toLocalDate()
        val added = overrides.added.filter { !it.date.isBefore(start) && !it.date.isAfter(end) }
        return (computed.filter { it.date.toEpochDay() !in overrides.removed } + added).sortedBy { it.date }
    }

    fun on(date: LocalDate, hijriOffset: Int = 0, overrides: Overrides = Overrides()): List<Holiday> =
        forJalaliYear(date.toJalali().year, hijriOffset, overrides).filter { it.date == date }

    private fun compute(year: Int, hijriOffset: Int): List<Holiday> {
        val result = ArrayList<Holiday>()
        for ((m, d, title) in SOLAR) result += Holiday(JalaliDate.of(year, m, d).toLocalDate(), title, approximate = false)

        var date = JalaliDate.of(year, 1, 1).toLocalDate()
        val end = JalaliDate.of(year, 12, 1).lastDayOfMonth().toLocalDate()
        var hijri = HijriDates.from(date, hijriOffset)
        while (!date.isAfter(end)) {
            val next = date.plusDays(1)
            val nextHijri = HijriDates.from(next, hijriOffset)
            if (hijri != null) {
                val isLastDay = nextHijri != null && nextHijri.month != hijri.month
                for ((m, d, title) in LUNAR) {
                    if (hijri.month == m && (hijri.day == d || (d == -1 && isLastDay))) {
                        result += Holiday(date, title, approximate = true)
                    }
                }
            }
            date = next
            hijri = nextHijri
        }
        return result.sortedBy { it.date }
    }
}
