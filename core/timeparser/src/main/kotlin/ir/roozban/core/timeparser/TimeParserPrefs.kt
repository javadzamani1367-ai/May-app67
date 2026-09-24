package ir.roozban.core.timeparser

import java.time.DayOfWeek

/** User-adjustable defaults for resolving vague expressions. */
data class TimeParserPrefs(
    val earlyMorningHour: Int = 7,
    val morningHour: Int = 9,
    val noonHour: Int = 12,
    val afternoonHour: Int = 15,
    /** «عصر» */
    val eveningHour: Int = 17,
    /** «غروب» */
    val duskHour: Int = 18,
    val nightHour: Int = 20,
    /**
     * A bare «ساعت ۵» is placed within [dayStartHour, dayEndHour] (inclusive), so it means 17:00,
     * while «ساعت ۸» stays 08:00 (or 20:00 if 08:00 has already passed today).
     */
    val dayStartHour: Int = 7,
    val dayEndHour: Int = 22,
    /** «آخر هفته»: Friday by default (the Iranian weekend). */
    val endOfWeek: DayOfWeek = DayOfWeek.FRIDAY,
) {
    fun hourOf(part: PartOfDay): Int = when (part) {
        PartOfDay.EARLY_MORNING -> earlyMorningHour
        PartOfDay.MORNING -> morningHour
        PartOfDay.NOON -> noonHour
        PartOfDay.AFTERNOON -> afternoonHour
        PartOfDay.EVENING -> eveningHour
        PartOfDay.DUSK -> duskHour
        PartOfDay.NIGHT -> nightHour
        PartOfDay.MIDNIGHT -> 24
    }
}

enum class PartOfDay { EARLY_MORNING, MORNING, NOON, AFTERNOON, EVENING, DUSK, NIGHT, MIDNIGHT }
