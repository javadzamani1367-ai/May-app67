package ir.roozban.core.timeparser

import ir.roozban.core.recurrence.Frequency
import java.time.DayOfWeek

/**
 * The AST of a time expression. Components are produced by [ComponentMatcher] and combined by
 * [Resolver]. They describe *what was said*, never an absolute date, so the same structure can
 * later be produced by the language model and resolved by the same deterministic code.
 */
internal sealed interface Component {
    /** Token range [from, to). */
    val from: Int
    val to: Int

    /** True for components that can pin a calendar day. */
    val isDateLike: Boolean get() = false
}

internal enum class WeekShift { NEAREST, THIS_WEEK, NEXT_WEEK }

internal enum class TimeUnit { MINUTE, HOUR, DAY, WEEK, MONTH, YEAR }

/** «امروز»، «فردا»، «پس‌فردا»، «امشب». */
internal data class DayOffsetComp(
    override val from: Int,
    override val to: Int,
    val days: Int,
    val impliedPart: PartOfDay? = null,
) : Component {
    override val isDateLike get() = true
}

/** «شنبه»، «سه‌شنبه‌ی بعد»، «جمعه‌ی این هفته». [index]: 0 = Saturday. */
internal data class WeekdayComp(
    override val from: Int,
    override val to: Int,
    val index: Int,
    val shift: WeekShift,
) : Component {
    override val isDateLike get() = true
}

internal enum class Edge { START, END }

/** «آخر هفته»، «هفته‌ی بعد»، «اول هفته‌ی بعد». [shift] null = nearest upcoming. */
internal data class WeekAnchorComp(
    override val from: Int,
    override val to: Int,
    val edge: Edge,
    val shift: Int?,
) : Component {
    override val isDateLike get() = true
}

/**
 * «آخر ماه»، «اول ماه بعد»، «دهم ماه». [day]: 1..31, or -1 for the last day.
 * [shift] null = nearest upcoming occurrence.
 */
internal data class MonthAnchorComp(
    override val from: Int,
    override val to: Int,
    val day: Int,
    val shift: Int?,
) : Component {
    override val isDateLike get() = true
}

/** «آخر سال»، «سال بعد». */
internal data class YearAnchorComp(
    override val from: Int,
    override val to: Int,
    val edge: Edge,
    val shift: Int?,
) : Component {
    override val isDateLike get() = true
}

/** «۱۵ اردیبهشت»، «آخر اسفند ۱۴۰۵»، «1405/7/15». [day] -1 = last day of the month. */
internal data class ExplicitDateComp(
    override val from: Int,
    override val to: Int,
    val year: Int?,
    val month: Int,
    val day: Int,
) : Component {
    override val isDateLike get() = true
}

/** «دو ساعت دیگر»، «سه روز بعد». Minutes for MINUTE/HOUR, whole units otherwise. */
internal data class OffsetComp(
    override val from: Int,
    override val to: Int,
    val amount: Long,
    val unit: TimeUnit,
) : Component {
    val isCalendarUnit get() = unit != TimeUnit.MINUTE && unit != TimeUnit.HOUR
    override val isDateLike get() = isCalendarUnit
}

/**
 * «ساعت ۵»، «۱۷:۳۰»، «یه ربع به ۶». A [weak] clock is a bare number («فردا ۵») that is only
 * kept when its context makes it clearly a time.
 */
internal data class ClockComp(
    override val from: Int,
    override val to: Int,
    val hour: Int,
    val minute: Int,
    val weak: Boolean,
) : Component {
    /** 13..23 or 0 unambiguously specify the half of the day. */
    val is24h get() = hour > 12 || hour == 0
}

internal data class PartOfDayComp(
    override val from: Int,
    override val to: Int,
    val part: PartOfDay,
) : Component

/** «هر روز»، «شنبه‌ها»، «آخر هر ماه». */
internal data class RecurrenceComp(
    override val from: Int,
    override val to: Int,
    val frequency: Frequency,
    val interval: Int = 1,
    val weekdays: Set<DayOfWeek> = emptySet(),
    val monthDay: Int? = null,
    val impliedPart: PartOfDay? = null,
) : Component

/** «۳۰ دقیقه»، «یک ساعت و نیم»: effort estimate, not a point in time. */
internal data class DurationComp(
    override val from: Int,
    override val to: Int,
    val minutes: Long,
) : Component
