package ir.roozban.core.timeparser

import ir.roozban.core.calendar.JalaliDate
import ir.roozban.core.calendar.PersianWeek
import ir.roozban.core.calendar.toJalali
import ir.roozban.core.recurrence.Frequency
import ir.roozban.core.recurrence.RecurrenceSpec
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit

/** Turns one cluster of components into a concrete time. Pure and deterministic. */
internal class Resolver(private val prefs: TimeParserPrefs) {

    class Resolution(val time: ResolvedTime?, val recurrence: RecurrenceSpec?, val confidence: Float)

    private data class DayTime(val time: LocalTime, val carryDays: Long)

    fun resolve(comps: List<Component>, now: LocalDateTime): Resolution {
        val today = now.toLocalDate()
        var confidence = 1f

        val offsets = comps.filterIsInstance<OffsetComp>()
        val relTime = offsets.firstOrNull { !it.isCalendarUnit }
        val dateLikes = comps.filter { it.isDateLike }
        val weekAnchor = comps.filterIsInstance<WeekAnchorComp>().firstOrNull()
        val weekday = comps.filterIsInstance<WeekdayComp>().firstOrNull()
        val clock = comps.filterIsInstance<ClockComp>().firstOrNull()
        val partComp = comps.filterIsInstance<PartOfDayComp>().firstOrNull()
        val recurrenceComp = comps.filterIsInstance<RecurrenceComp>().firstOrNull()

        // «دو ساعت دیگر» is an absolute offset from now; everything else is irrelevant.
        if (relTime != null) {
            val at = now.truncatedTo(ChronoUnit.MINUTES).plusMinutes(relTime.amount)
            return Resolution(ResolvedTime.At(at), null, if (comps.size > 1) 0.8f else 1f)
        }

        // «هفته‌ی بعد شنبه»: a week anchor refines a weekday rather than conflicting with it.
        val primaryDate: Component? = when {
            weekday != null && weekAnchor != null && weekday.shift == WeekShift.NEAREST ->
                weekday.copy(shift = if ((weekAnchor.shift ?: 0) >= 1) WeekShift.NEXT_WEEK else WeekShift.THIS_WEEK)
            else -> dateLikes.firstOrNull()
        }
        val consumed = if (weekday != null && weekAnchor != null) 2 else 1
        if (dateLikes.size > consumed) confidence -= 0.2f * (dateLikes.size - consumed)

        val part = partComp?.part
            ?: (primaryDate as? DayOffsetComp)?.impliedPart
            ?: recurrenceComp?.impliedPart
        // With a recurrence the day is chosen by the rule, so «هر شنبه ساعت ۸» means 08:00, not 20:00 today.
        val explicitDay = primaryDate != null || recurrenceComp != null

        // ---- time of day
        var dayTime: DayTime? = null
        if (clock != null) {
            val (dt, ambiguous) = clockTime(clock, part, explicitDay, now)
            dayTime = dt
            if (ambiguous) confidence -= 0.15f
            if (clock.weak) confidence -= 0.1f
        } else if (part != null) {
            val h = prefs.hourOf(part)
            dayTime = if (h >= 24) DayTime(LocalTime.MIDNIGHT, 1) else DayTime(LocalTime.of(h, 0), 0)
        }

        // ---- day
        var date: LocalDate? = primaryDate?.let { dateOf(it, today) }
        if (primaryDate is ExplicitDateComp && date == null) confidence -= 0.4f // e.g. «۳۱ مهر»

        // A weekday said on that same weekday means today only if the time is still ahead.
        if (primaryDate is WeekdayComp && primaryDate.shift == WeekShift.NEAREST && date == today) {
            val stillAhead = dayTime != null && today.atTime(dayTime.time).plusDays(dayTime.carryDays).isAfter(now)
            if (!stillAhead) date = today.plusWeeks(1)
        }

        val recurrence = recurrenceComp?.let { toSpec(it, weekday) }
        if (date == null && recurrence != null) date = firstOccurrence(recurrence, today, dayTime, now)

        if (date == null && dayTime != null) {
            // Implied today; roll over to tomorrow if the moment has passed.
            date = today
            if (!today.atTime(dayTime.time).plusDays(dayTime.carryDays).isAfter(now)) date = today.plusDays(1)
        }

        if (date == null) {
            return Resolution(null, recurrence, if (recurrence != null) confidence * 0.8f else 0f)
        }
        if (date.isBefore(today)) confidence = minOf(confidence, 0.5f)

        val time = if (dayTime == null) {
            ResolvedTime.AllDay(date)
        } else {
            ResolvedTime.At(date.plusDays(dayTime.carryDays).atTime(dayTime.time))
        }
        return Resolution(time, recurrence, confidence.coerceIn(0.05f, 1f))
    }

    /**
     * Resolves the hour of a clock reading. Returns the time and whether the AM/PM choice was a
     * guess between two plausible readings.
     */
    private fun clockTime(clock: ClockComp, part: PartOfDay?, explicitDay: Boolean, now: LocalDateTime): Pair<DayTime, Boolean> {
        val h = clock.hour
        val m = clock.minute
        if (clock.is24h) return dayTime(h, m, 0) to false
        if (part != null) {
            return when (part) {
                PartOfDay.EARLY_MORNING, PartOfDay.MORNING -> dayTime(if (h == 12) 0 else h, m, 0)
                PartOfDay.NOON -> dayTime(if (h in 1..4) h + 12 else h, m, 0)
                PartOfDay.AFTERNOON, PartOfDay.EVENING, PartOfDay.DUSK -> dayTime(if (h in 1..11) h + 12 else h, m, 0)
                PartOfDay.NIGHT, PartOfDay.MIDNIGHT -> when (h) {
                    12 -> dayTime(24, m, 0)
                    in 1..4 -> dayTime(h, m, 1) // «امشب ساعت ۲» = 02:00 after midnight
                    else -> dayTime(h + 12, m, 0)
                }
            } to false
        }
        // No hint: choose among h and h+12 the readings inside the waking-hours window.
        val candidates = listOf(h, h + 12).filter { it < 24 && it in prefs.dayStartHour..prefs.dayEndHour }
            .ifEmpty { listOf(h) }
        val ambiguous = candidates.size > 1
        if (explicitDay) return dayTime(candidates.first(), m, 0) to ambiguous
        val today = now.toLocalDate()
        val upcoming = candidates.firstOrNull { today.atTime(it, m).isAfter(now) }
        return if (upcoming != null) {
            dayTime(upcoming, m, 0) to ambiguous
        } else {
            dayTime(candidates.first(), m, 1) to ambiguous // tomorrow
        }
    }

    private fun dayTime(hour: Int, minute: Int, carry: Long): DayTime =
        if (hour >= 24) DayTime(LocalTime.of(hour - 24, minute), carry + 1) else DayTime(LocalTime.of(hour, minute), carry)

    private fun dateOf(c: Component, today: LocalDate): LocalDate? {
        val jt = today.toJalali()
        return when (c) {
            is DayOffsetComp -> today.plusDays(c.days.toLong())
            is WeekdayComp -> {
                val target = c.index
                val current = PersianWeek.indexOf(today.dayOfWeek)
                when (c.shift) {
                    WeekShift.NEAREST -> today.plusDays(Math.floorMod(target - current, 7).toLong())
                    WeekShift.THIS_WEEK -> PersianWeek.startOfWeek(today).plusDays(target.toLong())
                    WeekShift.NEXT_WEEK -> PersianWeek.startOfWeek(today).plusDays(7L + target)
                }
            }
            is WeekAnchorComp -> {
                val start = PersianWeek.startOfWeek(today)
                val endIndex = PersianWeek.indexOf(prefs.endOfWeek).toLong()
                when (c.edge) {
                    Edge.START -> when (c.shift) {
                        null -> if (start == today) today else start.plusWeeks(1)
                        else -> start.plusWeeks(c.shift.toLong())
                    }
                    Edge.END -> start.plusWeeks((c.shift ?: 0).toLong()).plusDays(endIndex)
                }
            }
            is MonthAnchorComp -> {
                val shift = c.shift ?: 0
                var d = dayOfMonth(jt.plusMonths(shift.toLong()), c.day)
                if (c.shift == null && d.toLocalDate().isBefore(today)) d = dayOfMonth(jt.plusMonths(1), c.day)
                d.toLocalDate()
            }
            is YearAnchorComp -> {
                val shift = c.shift ?: 0
                when (c.edge) {
                    // «اول سال» without a modifier means the coming Nowruz (or today, on Nowruz).
                    Edge.START -> if (c.shift == null && jt.month == 1 && jt.day == 1) {
                        today
                    } else {
                        JalaliDate.of(jt.year + (c.shift ?: 1), 1, 1).toLocalDate()
                    }
                    Edge.END -> JalaliDate.of(jt.year + shift, 12, 1).lastDayOfMonth().toLocalDate()
                }
            }
            is ExplicitDateComp -> {
                fun build(year: Int): JalaliDate? {
                    if (year !in JalaliDate.MIN_YEAR..JalaliDate.MAX_YEAR) return null
                    val len = JalaliDate.monthLength(year, c.month)
                    val day = if (c.day == -1) len else c.day
                    return if (day <= len) JalaliDate.of(year, c.month, day) else null
                }
                if (c.year != null) {
                    build(c.year)?.toLocalDate()
                } else {
                    val thisYear = build(jt.year)
                    if (thisYear != null && !thisYear.toLocalDate().isBefore(today)) thisYear.toLocalDate() else build(jt.year + 1)?.toLocalDate()
                }
            }
            is OffsetComp -> when (c.unit) {
                TimeUnit.DAY -> today.plusDays(c.amount)
                TimeUnit.WEEK -> today.plusWeeks(c.amount)
                TimeUnit.MONTH -> jt.plusMonths(c.amount).toLocalDate()
                TimeUnit.YEAR -> jt.plusYears(c.amount).toLocalDate()
                else -> null
            }
            else -> null
        }
    }

    private fun dayOfMonth(month: JalaliDate, day: Int): JalaliDate =
        month.withDayOfMonth(if (day == -1) month.lengthOfMonth else minOf(day, month.lengthOfMonth))

    private fun toSpec(c: RecurrenceComp, weekday: WeekdayComp?): RecurrenceSpec {
        // «هر هفته شنبه» → weekly on Saturday.
        val days = if (c.frequency == Frequency.WEEKLY && c.weekdays.isEmpty() && weekday != null) {
            setOf(PersianWeek.dayAt(weekday.index))
        } else {
            c.weekdays
        }
        return RecurrenceSpec(c.frequency, c.interval, days, c.monthDay)
    }

    private fun firstOccurrence(r: RecurrenceSpec, today: LocalDate, dayTime: DayTime?, now: LocalDateTime): LocalDate {
        fun ok(d: LocalDate): Boolean =
            d.isAfter(today) || dayTime == null || today.atTime(dayTime.time).plusDays(dayTime.carryDays).isAfter(now)

        val monthDay = r.jalaliMonthDay
        return when {
            r.byWeekdays.isNotEmpty() ->
                (0L..7L).map { today.plusDays(it) }.first { it.dayOfWeek in r.byWeekdays && ok(it) }
            r.frequency == Frequency.MONTHLY && monthDay != null -> {
                val jt = today.toJalali()
                val thisMonth = dayOfMonth(jt, monthDay).toLocalDate()
                if (!thisMonth.isBefore(today) && ok(thisMonth)) thisMonth else dayOfMonth(jt.plusMonths(1), monthDay).toLocalDate()
            }
            ok(today) -> today
            else -> today.plusDays(1)
        }
    }
}
