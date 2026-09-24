package ir.roozban.core.timeparser

import ir.roozban.core.recurrence.Frequency
import ir.roozban.core.calendar.PersianWeek
import java.time.DayOfWeek

/**
 * Scans the token list left to right and emits every [Component] it recognizes.
 * Rules are tried in a fixed priority order at each position; the first match wins and
 * scanning resumes after it. Order matters: e.g. «سه شنبه» must be a weekday before «سه» can be
 * read as a number, and «بعد از ظهر» must be a part of day before «بعد از …» is read as an offset.
 */
internal class ComponentMatcher(private val t: List<Token>) {

    fun matchAll(): List<Component> {
        val out = ArrayList<Component>()
        var i = 0
        while (i < t.size) {
            val c = matchAt(i)
            if (c != null) {
                out += c
                i = c.to
            } else {
                i++
            }
        }
        return out
    }

    private fun matchAt(i: Int): Component? =
        recurrence(i)
            ?: explicitDate(i)
            ?: weekday(i)
            ?: dayOffset(i)
            ?: partOfDay(i)
            ?: anchor(i)
            ?: clockTo(i)
            ?: offsetOrDuration(i)
            ?: clock(i)

    // ---------------------------------------------------------------- helpers

    private fun w(i: Int, vararg words: String): Boolean = i in t.indices && t[i].isWord(*words)

    private fun skipEzafe(i: Int): Int = if (w(i, "ی")) i + 1 else i

    private data class Num(val value: Int, val ordinal: Boolean, val end: Int)

    /** A cardinal or ordinal number written in digits or words, up to 69. */
    private fun number(i: Int): Num? {
        if (i !in t.indices) return null
        val tok = t[i]
        if (tok.type == TokenType.NUMBER) {
            val v = tok.intValue ?: return null
            return if (w(i + 1, *Lexicon.ORDINAL_SUFFIXES)) Num(v, true, i + 2) else Num(v, false, i + 1)
        }
        if (tok.type != TokenType.WORD) return null
        Lexicon.ORDINALS[tok.text]?.let { return Num(it, true, i + 1) }
        val c = Lexicon.CARDINALS[tok.text] ?: return null
        if (c >= 20 && c % 10 == 0 && w(i + 1, "و") && i + 2 < t.size) {
            val unitWord = t[i + 2].text
            Lexicon.CARDINALS[unitWord]?.takeIf { it in 1..9 }?.let { u ->
                return if (w(i + 3, *Lexicon.ORDINAL_SUFFIXES)) Num(c + u, true, i + 4) else Num(c + u, false, i + 3)
            }
            Lexicon.ORDINALS[unitWord]?.takeIf { it in 1..9 }?.let { u -> return Num(c + u, true, i + 3) }
        }
        return if (w(i + 1, *Lexicon.ORDINAL_SUFFIXES)) Num(c, true, i + 2) else Num(c, false, i + 1)
    }

    private fun cardinal(i: Int): Num? = number(i)?.takeIf { !it.ordinal }

    /** Weekday name at [i] (single token or split «سه شنبه»): index and end. */
    private fun weekdayName(i: Int): Pair<Int, Int>? {
        if (i !in t.indices || t[i].type != TokenType.WORD) return null
        Lexicon.WEEKDAY_PREFIXES[t[i].text]?.let { idx -> if (w(i + 1, "شنبه")) return idx to i + 2 }
        Lexicon.WEEKDAYS[t[i].text]?.let { return it to i + 1 }
        return null
    }

    /** «بعد»/«آینده»/«دیگر» (optionally after an ezafe) → 1; «این»/«همین» before the noun is handled by callers. */
    private fun nextModifier(i: Int): Int? {
        val j = skipEzafe(i)
        return if (w(j, *Lexicon.NEXT)) j + 1 else null
    }

    private fun unitAt(i: Int): TimeUnit? = when {
        w(i, "دقیقه") -> TimeUnit.MINUTE
        w(i, "ساعت") -> TimeUnit.HOUR
        w(i, "روز") -> TimeUnit.DAY
        w(i, "هفته") -> TimeUnit.WEEK
        w(i, "ماه") -> TimeUnit.MONTH
        w(i, "سال") -> TimeUnit.YEAR
        else -> null
    }

    // ---------------------------------------------------------------- recurrence

    private fun recurrence(i: Int): Component? {
        // «روزانه»، «هفتگی»، «ماهانه»، «سالانه»
        when {
            w(i, "روزانه") -> return RecurrenceComp(i, i + 1, Frequency.DAILY)
            w(i, "هفتگی") -> return RecurrenceComp(i, i + 1, Frequency.WEEKLY)
            w(i, "ماهانه", "ماهیانه") -> return RecurrenceComp(i, i + 1, Frequency.MONTHLY)
            w(i, "سالانه", "سالیانه") -> return RecurrenceComp(i, i + 1, Frequency.YEARLY)
        }
        // «یک روز در میان»
        if (w(i, "یک") && w(i + 1, "روز") && w(i + 2, "در") && w(i + 3, "میان")) {
            return RecurrenceComp(i, i + 4, Frequency.DAILY, interval = 2)
        }
        // «روزهای زوج/فرد/کاری»
        val daysEnd = when {
            w(i, "روزهای") -> i + 1
            w(i, "روز") && w(i + 1, "های", "ها") -> i + 2
            else -> -1
        }
        if (daysEnd > 0) weekdaySet(daysEnd)?.let { return RecurrenceComp(i, daysEnd + 1, Frequency.WEEKLY, weekdays = it) }

        // «آخر هر ماه»، «اول هر ماه»، «پانزدهم هر ماه»
        val monthDay = when {
            w(i, "آخر") -> -1 to i + 1
            else -> number(i)?.takeIf { it.ordinal && it.value in 1..31 }?.let { it.value to it.end }
        }
        if (monthDay != null) {
            val j = skipEzafe(monthDay.second)
            if (w(j, "هر") && w(j + 1, "ماه")) {
                return RecurrenceComp(i, j + 2, Frequency.MONTHLY, monthDay = monthDay.first)
            }
        }

        if (!w(i, "هر")) return null
        val j = i + 1
        when {
            w(j, "روز") && w(j + 1, "کاری") -> return RecurrenceComp(i, j + 2, Frequency.WEEKLY, weekdays = WORKDAYS)
            w(j, "روز") -> return RecurrenceComp(i, j + 1, Frequency.DAILY)
            w(j, "هفته") -> return RecurrenceComp(i, j + 1, Frequency.WEEKLY)
            w(j, "ماه") -> return RecurrenceComp(i, j + 1, Frequency.MONTHLY)
            w(j, "سال") -> return RecurrenceComp(i, j + 1, Frequency.YEARLY)
            w(j, "صبح") -> return RecurrenceComp(i, j + 1, Frequency.DAILY, impliedPart = PartOfDay.MORNING)
            w(j, "ظهر") -> return RecurrenceComp(i, j + 1, Frequency.DAILY, impliedPart = PartOfDay.NOON)
            w(j, "عصر") -> return RecurrenceComp(i, j + 1, Frequency.DAILY, impliedPart = PartOfDay.EVENING)
            w(j, "شب") -> return RecurrenceComp(i, j + 1, Frequency.DAILY, impliedPart = PartOfDay.NIGHT)
        }
        // «هر شنبه و دوشنبه»
        weekdayName(j)?.let { (first, end) ->
            val days = mutableSetOf(PersianWeek.dayAt(first))
            var k = skipEzafe(end)
            while (w(k, "و")) {
                val next = weekdayName(k + 1) ?: break
                days += PersianWeek.dayAt(next.first)
                k = skipEzafe(next.second)
            }
            return RecurrenceComp(i, k, Frequency.WEEKLY, weekdays = days)
        }
        // «هر دو روز»، «هر ۳ هفته»
        cardinal(j)?.takeIf { it.value in 1..99 }?.let { n ->
            val freq = when (unitAt(n.end)) {
                TimeUnit.DAY -> Frequency.DAILY
                TimeUnit.WEEK -> Frequency.WEEKLY
                TimeUnit.MONTH -> Frequency.MONTHLY
                TimeUnit.YEAR -> Frequency.YEARLY
                else -> null
            }
            if (freq != null) return RecurrenceComp(i, n.end + 1, freq, interval = n.value)
        }
        return null
    }

    private fun weekdaySet(i: Int): Set<DayOfWeek>? = when {
        w(i, "زوج") -> EVEN_DAYS
        w(i, "فرد") -> ODD_DAYS
        w(i, "کاری") -> WORKDAYS
        else -> null
    }

    // ---------------------------------------------------------------- dates

    private fun explicitDate(i: Int): Component? {
        val tok = t.getOrNull(i) ?: return null
        if (tok.type == TokenType.DATE) {
            val p = tok.text.split('/').map { it.toInt() }
            return when (p.size) {
                3 -> if (p[0] >= 1000) ExplicitDateComp(i, i + 1, p[0], p[1], p[2]).takeIf { valid(it) } else null
                2 -> ExplicitDateComp(i, i + 1, null, p[0], p[1]).takeIf { valid(it) }
                else -> null
            }
        }
        // «آخر اسفند»، «وسط مهر»
        val edge = when {
            w(i, "آخر", "اواخر") -> -1
            w(i, "وسط", "نیمه", "اواسط") -> 15
            else -> null
        }
        if (edge != null) {
            val j = skipEzafe(i + 1)
            val month = t.getOrNull(j)?.let { Lexicon.JALALI_MONTHS[it.text] } ?: return null
            val (year, end) = year(j + 1)
            return ExplicitDateComp(i, end, year, month, edge)
        }
        // «۱۵ اردیبهشت»، «پانزدهم اردیبهشت ۱۴۰۶»، «اول مهر»
        val n = number(i)?.takeIf { it.value in 1..31 } ?: return null
        val j = skipEzafe(n.end)
        val month = t.getOrNull(j)?.let { if (it.type == TokenType.WORD) Lexicon.JALALI_MONTHS[it.text] else null } ?: return null
        val (year, end) = year(j + 1)
        return ExplicitDateComp(i, end, year, month, n.value).takeIf { valid(it) }
    }

    private fun year(i: Int): Pair<Int?, Int> {
        val tok = t.getOrNull(i)
        if (tok != null && tok.type == TokenType.NUMBER) {
            val y = tok.intValue
            if (y != null && y in 1300..1500) return y to i + 1
        }
        return null to i
    }

    private fun valid(d: ExplicitDateComp) = d.month in 1..12 && (d.day == -1 || d.day in 1..31)

    private fun weekday(i: Int): Component? {
        // Optional «این»/«همین» prefix: the nearest upcoming one.
        val prefixed = w(i, "این", "همین")
        val (index, nameEnd) = weekdayName(if (prefixed) i + 1 else i) ?: return null
        var end = nameEnd
        var shift = WeekShift.NEAREST

        // «شنبه‌ها»: every Saturday (optionally a list: «شنبه‌ها و دوشنبه‌ها»).
        if (!prefixed && w(end, "ها")) {
            val days = mutableSetOf(PersianWeek.dayAt(index))
            var k = end + 1
            while (w(k, "و")) {
                val next = weekdayName(k + 1) ?: break
                if (!w(next.second, "ها")) break
                days += PersianWeek.dayAt(next.first)
                k = next.second + 1
            }
            return RecurrenceComp(i, k, Frequency.WEEKLY, weekdays = days)
        }

        val j = skipEzafe(end)
        when {
            w(j, *Lexicon.NEXT) -> { shift = WeekShift.NEXT_WEEK; end = j + 1 }
            w(j, "این", "همین") && w(j + 1, "هفته") -> { shift = WeekShift.THIS_WEEK; end = j + 2 }
            w(j, "هفته") && nextModifier(j + 1) != null -> { shift = WeekShift.NEXT_WEEK; end = nextModifier(j + 1)!! }
        }
        return WeekdayComp(i, end, index, shift)
    }

    private fun dayOffset(i: Int): Component? = when {
        w(i, "امروز") -> DayOffsetComp(i, i + 1, 0)
        w(i, "امشب") -> DayOffsetComp(i, i + 1, 0, PartOfDay.NIGHT)
        w(i, "فردا") -> DayOffsetComp(i, i + 1, 1)
        w(i, "فرداشب") -> DayOffsetComp(i, i + 1, 1, PartOfDay.NIGHT)
        w(i, "پسفردا") -> DayOffsetComp(i, i + 1, 2)
        w(i, "پس") && w(i + 1, "پسفردا") -> DayOffsetComp(i, i + 2, 3)
        w(i, "پس") && w(i + 1, "پس") && w(i + 2, "فردا") -> DayOffsetComp(i, i + 3, 3)
        w(i, "پس") && w(i + 1, "فردا") -> DayOffsetComp(i, i + 2, 2)
        w(i, "دیروز") -> DayOffsetComp(i, i + 1, -1)
        else -> null
    }

    /** «آخر هفته»، «هفته‌ی بعد»، «آخر ماه»، «اول ماه بعد»، «دهم ماه»، «سال بعد». */
    private fun anchor(i: Int): Component? {
        val edgeDay: Int? = when {
            w(i, "آخر", "پایان", "اواخر") -> -1
            w(i, "اول", "ابتدای", "ابتدا", "شروع", "اوایل") -> 1
            w(i, "وسط", "نیمه", "اواسط") -> 15
            else -> number(i)?.takeIf { it.ordinal && it.value in 1..31 }?.value
        }
        if (edgeDay != null) {
            val start = number(i)?.takeIf { it.ordinal }?.end ?: (i + 1)
            var j = skipEzafe(start)
            var shift: Int? = null
            if (w(j, "این", "همین")) { shift = 0; j++ }
            when {
                w(j, "هفته") && (edgeDay == 1 || edgeDay == -1) -> {
                    val next = nextModifier(j + 1)
                    val s = if (next != null) 1 else shift
                    return WeekAnchorComp(i, next ?: (j + 1), if (edgeDay == 1) Edge.START else Edge.END, s)
                }
                w(j, "ماه") -> {
                    val next = nextModifier(j + 1)
                    return MonthAnchorComp(i, next ?: (j + 1), edgeDay, if (next != null) 1 else shift)
                }
                w(j, "سال") && (edgeDay == 1 || edgeDay == -1) -> {
                    val next = nextModifier(j + 1)
                    return YearAnchorComp(i, next ?: (j + 1), if (edgeDay == 1) Edge.START else Edge.END, if (next != null) 1 else shift)
                }
            }
            return null
        }
        // «این هفته» → until the end of this week.
        if (w(i, "این", "همین") && w(i + 1, "هفته")) return WeekAnchorComp(i, i + 2, Edge.END, 0)
        // «هفته‌ی بعد»، «ماه آینده»، «سال دیگه»
        val next = nextModifier(i + 1) ?: return null
        return when {
            w(i, "هفته") -> WeekAnchorComp(i, next, Edge.START, 1)
            w(i, "ماه") -> MonthAnchorComp(i, next, 1, 1)
            w(i, "سال") -> YearAnchorComp(i, next, Edge.START, 1)
            else -> null
        }
    }

    // ---------------------------------------------------------------- times

    private fun partOfDay(i: Int): Component? {
        val (part, end) = when {
            w(i, "بعد") && w(i + 1, "از") && w(i + 2, "ظهر") -> PartOfDay.AFTERNOON to i + 3
            w(i, "بعدازظهر") -> PartOfDay.AFTERNOON to i + 1
            w(i, "اول") && w(i + 1, "صبح") -> PartOfDay.EARLY_MORNING to i + 2
            w(i, "صبح") && w(i + 1, "زود") -> PartOfDay.EARLY_MORNING to i + 2
            w(i, "صبح") -> PartOfDay.MORNING to i + 1
            w(i, "ظهر") -> PartOfDay.NOON to i + 1
            w(i, "عصر") -> PartOfDay.EVENING to i + 1
            w(i, "غروب") -> PartOfDay.DUSK to i + 1
            w(i, "نیمه", "نصف") && w(i + 1, "شب") -> PartOfDay.MIDNIGHT to i + 2
            w(i, "شب") -> PartOfDay.NIGHT to i + 1
            else -> return null
        }
        // «صبح‌ها»: every morning.
        if (w(end, "ها")) return RecurrenceComp(i, end + 1, Frequency.DAILY, impliedPart = part)
        return PartOfDayComp(i, end, part)
    }

    /** Minutes after the hour: «و نیم»، «و ربع»، «و ۲۰ دقیقه». Returns (minutes, end). */
    private fun minutesAfter(i: Int): Pair<Int, Int>? {
        if (!w(i, "و")) return null
        return when {
            w(i + 1, "نیم") -> 30 to i + 2
            w(i + 1, "ربع") -> 15 to i + 2
            else -> cardinal(i + 1)?.takeIf { it.value in 1..59 }?.let { m ->
                m.value to (if (w(m.end, "دقیقه")) m.end + 1 else m.end)
            }
        }
    }

    /** Hour with optional minutes at [i]: «۵»، «۵ و نیم»، «۱۷:۳۰». Returns (h, m, end). */
    private fun hourMinute(i: Int): Triple<Int, Int, Int>? {
        val tok = t.getOrNull(i) ?: return null
        if (tok.type == TokenType.CLOCK) {
            val (h, m) = tok.text.split(':').map { it.toInt() }
            return if (h in 0..24 && m in 0..59) Triple(h, m, i + 1) else null
        }
        val h = cardinal(i)?.takeIf { it.value in 0..24 } ?: return null
        val min = minutesAfter(h.end)
        return if (min != null) Triple(h.value, min.first, min.second) else Triple(h.value, 0, h.end)
    }

    /** «یه ربع به ۶»، «ده دقیقه به ۵», optionally after «ساعت». */
    private fun clockTo(i: Int): Component? {
        val s = if (w(i, "ساعت")) i + 1 else i
        val (minutes, afterAmount) = when {
            w(s, "ربع") -> 15 to s + 1
            cardinal(s) != null && w(cardinal(s)!!.end, "ربع") -> 15 * cardinal(s)!!.value to cardinal(s)!!.end + 1
            cardinal(s) != null && w(cardinal(s)!!.end, "دقیقه") -> cardinal(s)!!.value to cardinal(s)!!.end + 1
            else -> return null
        }
        if (minutes !in 1..59 || !w(afterAmount, "به")) return null
        val hEnd = if (w(afterAmount + 1, "ساعت")) afterAmount + 2 else afterAmount + 1
        val h = cardinal(hEnd)?.takeIf { it.value in 1..24 } ?: return null
        // «یه ربع به یک» is 12:45, not 00:45.
        val hour = if (h.value == 1) 12 else h.value - 1
        return ClockComp(i, h.end, hour, 60 - minutes, weak = false)
    }

    /**
     * «دو ساعت دیگر»، «بعد از ۲ ساعت»، «سه روز بعد» → [OffsetComp];
     * «۳۰ دقیقه»، «یک ساعت و نیم» without a future marker → [DurationComp].
     */
    private fun offsetOrDuration(i: Int): Component? {
        val prefixed = (w(i, "بعد") || w(i, "پس")) && w(i + 1, "از")
        val start = if (prefixed) i + 2 else i
        val amount = amountWithUnit(start) ?: return null
        val suffixEnd = if (w(amount.end, *Lexicon.OFFSET_SUFFIXES)) amount.end + 1 else null
        if (prefixed || suffixEnd != null) {
            val end = suffixEnd ?: amount.end
            return if (amount.unit == TimeUnit.MINUTE) {
                OffsetComp(i, end, amount.minutes, TimeUnit.MINUTE)
            } else {
                if (amount.whole == null) null else OffsetComp(i, end, amount.whole, amount.unit)
            }
        }
        return if (amount.unit == TimeUnit.MINUTE && amount.minutes in 1..24 * 60) DurationComp(i, amount.end, amount.minutes) else null
    }

    /** [unit] is MINUTE for any sub-day amount (then [minutes] is set), otherwise a calendar unit with [whole]. */
    private class Amount(val unit: TimeUnit, val minutes: Long, val whole: Long?, val end: Int)

    private fun amountWithUnit(i: Int): Amount? {
        // «ربع ساعت»، «یک ربع»، «سه ربع»
        if (w(i, "ربع")) {
            val end = if (w(i + 1, "ساعت")) i + 2 else i + 1
            return Amount(TimeUnit.MINUTE, 15, null, end)
        }
        val (value, afterNum) = when {
            w(i, "نیم") -> 0.5 to i + 1
            else -> {
                val n = cardinal(i) ?: return null
                // «یک و نیم ساعت»
                if (w(n.end, "و") && w(n.end + 1, "نیم")) (n.value + 0.5) to n.end + 2 else n.value.toDouble() to n.end
            }
        }
        if (w(afterNum, "ربع")) {
            val end = if (w(afterNum + 1, "ساعت")) afterNum + 2 else afterNum + 1
            return Amount(TimeUnit.MINUTE, (value * 15).toLong(), null, end)
        }
        val unit = unitAt(afterNum) ?: return null
        var end = afterNum + 1
        return when (unit) {
            TimeUnit.MINUTE, TimeUnit.HOUR -> {
                var minutes = if (unit == TimeUnit.HOUR) (value * 60).toLong() else value.toLong()
                if (unit == TimeUnit.HOUR && w(end, "و")) {
                    when {
                        w(end + 1, "نیم") -> { minutes += 30; end += 2 }
                        w(end + 1, "ربع") -> { minutes += 15; end += 2 }
                        else -> cardinal(end + 1)?.let { m ->
                            if (w(m.end, "دقیقه")) { minutes += m.value; end = m.end + 1 }
                        }
                    }
                }
                Amount(TimeUnit.MINUTE, minutes, null, end)
            }
            else -> {
                val whole = if (value % 1.0 == 0.0) value.toLong() else null
                if (whole == null && unit == TimeUnit.DAY) {
                    Amount(TimeUnit.MINUTE, (value * 24 * 60).toLong(), null, end)
                } else {
                    Amount(unit, 0, whole, end)
                }
            }
        }
    }

    /** «ساعت ۵»، «ساعت ۵ و نیم»، «۱۷:۳۰»، or a weak bare number «۵». */
    private fun clock(i: Int): Component? {
        if (w(i, "ساعت")) {
            val (h, m, end) = hourMinute(i + 1) ?: return null
            return ClockComp(i, end, h, m, weak = false)
        }
        val tok = t.getOrNull(i) ?: return null
        if (tok.type == TokenType.CLOCK) {
            val (h, m, end) = hourMinute(i) ?: return null
            return ClockComp(i, end, h, m, weak = false)
        }
        val (h, m, end) = hourMinute(i) ?: return null
        if (h == 0 && m == 0) return null
        return ClockComp(i, end, h, m, weak = true)
    }

    companion object {
        private val EVEN_DAYS = setOf(DayOfWeek.SATURDAY, DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY)
        private val ODD_DAYS = setOf(DayOfWeek.SUNDAY, DayOfWeek.TUESDAY, DayOfWeek.THURSDAY)
        private val WORKDAYS = setOf(
            DayOfWeek.SATURDAY, DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
        )
    }
}
