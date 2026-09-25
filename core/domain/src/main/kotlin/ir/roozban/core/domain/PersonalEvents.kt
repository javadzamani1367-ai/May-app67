package ir.roozban.core.domain

import ir.roozban.core.calendar.HijriDates
import ir.roozban.core.calendar.JalaliDate
import ir.roozban.core.calendar.toJalali
import ir.roozban.core.model.EventCalendar
import ir.roozban.core.model.EventKind
import ir.roozban.core.model.PersonalEvent
import kotlinx.coroutines.flow.Flow
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

interface EventRepository {
    fun observeEvents(): Flow<List<PersonalEvent>>

    suspend fun get(id: String): PersonalEvent?

    suspend fun all(): List<PersonalEvent>

    suspend fun upsert(event: PersonalEvent)

    suspend fun delete(id: String)
}

/** One day an event falls on; [count] is the anniversary number (e.g. the age) when the year is known. */
data class EventOccurrence(val event: PersonalEvent, val date: LocalDate, val count: Int?)

object EventDates {
    /** Occurrences of [event] between [from] and [to] (inclusive), in date order. */
    fun between(event: PersonalEvent, from: LocalDate, to: LocalDate, hijriOffset: Int = 0): List<EventOccurrence> {
        if (to.isBefore(from)) return emptyList()
        val result = ArrayList<EventOccurrence>()
        val years: IntRange = when (event.calendar) {
            EventCalendar.JALALI -> from.toJalali().year..to.toJalali().year
            EventCalendar.GREGORIAN -> from.year..to.year
            EventCalendar.HIJRI -> {
                val a = HijriDates.from(from, hijriOffset)?.year ?: return emptyList()
                val b = HijriDates.from(to, hijriOffset)?.year ?: return emptyList()
                a..b
            }
        }
        val firstYear = event.year
        for (y in years) {
            if (!event.yearly && firstYear != null && y != firstYear) continue
            if (firstYear != null && y < firstYear) continue
            val date = dateIn(event, y, hijriOffset) ?: continue
            if (date.isBefore(from) || date.isAfter(to)) continue
            result += EventOccurrence(event, date, firstYear?.let { y - it })
        }
        return result.sortedBy { it.date }
    }

    /** The first occurrence on or after [from]. */
    fun next(event: PersonalEvent, from: LocalDate, hijriOffset: Int = 0): EventOccurrence? =
        between(event, from, from.plusDays(400), hijriOffset).firstOrNull()

    private fun dateIn(event: PersonalEvent, year: Int, hijriOffset: Int): LocalDate? = when (event.calendar) {
        EventCalendar.JALALI -> {
            if (year !in JalaliDate.MIN_YEAR..JalaliDate.MAX_YEAR) {
                null
            } else {
                // «۳۰ اسفند» in a common year is kept on the 29th.
                JalaliDate.of(year, event.month, minOf(event.day, JalaliDate.monthLength(year, event.month))).toLocalDate()
            }
        }
        EventCalendar.GREGORIAN -> {
            val length = java.time.YearMonth.of(year, event.month).lengthOfMonth()
            LocalDate.of(year, event.month, minOf(event.day, length))
        }
        EventCalendar.HIJRI -> HijriDates.toLocalDate(year, event.month, event.day, hijriOffset)
    }
}

/** Arms one alarm per event: its nearest reminder. */
@Singleton
class EventReminders @Inject constructor(
    private val events: EventRepository,
    private val settings: SettingsRepository,
    private val alarms: RoutineAlarms,
    private val clock: Clock,
) {
    data class Due(val occurrence: EventOccurrence, val daysBefore: Int)

    suspend fun syncAll() = events.all().forEach { sync(it) }

    suspend fun sync(event: PersonalEvent, after: LocalDateTime = LocalDateTime.now(clock)) {
        val next = nextReminder(event, after, settings.current().hijriOffset)
        if (next == null) alarms.cancelEvent(event.id) else alarms.scheduleEvent(event.id, next.first.atZone(clock.zone).toInstant().toEpochMilli())
    }

    fun cancel(id: String) = alarms.cancelEvent(id)

    /** The event alarm fired: returns what to announce and arms the next reminder. */
    suspend fun onAlarm(id: String): Due? {
        val event = events.get(id) ?: return null.also { alarms.cancelEvent(id) }
        val now = LocalDateTime.now(clock)
        val offset = settings.current().hijriOffset
        // The reminder that is due now (fired up to a few minutes late or early).
        val due = reminders(event, now.toLocalDate().minusDays(1), offset)
            .filter { (at, _) -> !at.isAfter(now.plusMinutes(2)) && at.isAfter(now.minusHours(12)) }
            .maxByOrNull { it.first }?.second
        sync(event, after = now.plusMinutes(1))
        return due
    }

    internal fun nextReminder(event: PersonalEvent, after: LocalDateTime, hijriOffset: Int): Pair<LocalDateTime, Due>? =
        reminders(event, after.toLocalDate(), hijriOffset).firstOrNull { it.first.isAfter(after) }

    /** Reminder moments from [from] on, soonest first. */
    private fun reminders(event: PersonalEvent, from: LocalDate, hijriOffset: Int): List<Pair<LocalDateTime, Due>> {
        if (event.remindDaysBefore.isEmpty()) return emptyList()
        val maxBefore = event.remindDaysBefore.max().toLong()
        return EventDates.between(event, from, from.plusDays(400 + maxBefore), hijriOffset)
            .flatMap { occ -> event.remindDaysBefore.map { d -> occ.date.minusDays(d.toLong()).atTime(event.reminderTime) to Due(occ, d) } }
            .filter { !it.first.toLocalDate().isBefore(from) }
            .sortedBy { it.first }
    }
}

class EventUseCases @Inject constructor(
    private val events: EventRepository,
    private val reminders: EventReminders,
    private val clock: Clock,
) {
    /**
     * Creates an event on [date] (picked on the Jalali calendar), repeating by [calendar].
     * [knownYear] keeps the year so anniversaries can be counted.
     */
    suspend fun create(
        title: String,
        kind: EventKind,
        color: Int,
        date: LocalDate,
        calendar: EventCalendar,
        knownYear: Boolean,
        remindDaysBefore: Set<Int>,
        reminderTime: LocalTime,
        notes: String = "",
        hijriOffset: Int = 0,
    ): PersonalEvent? {
        if (title.isBlank()) return null
        val (y, m, d) = parts(date, calendar, hijriOffset) ?: return null
        val now = Instant.now(clock)
        val event = PersonalEvent(
            id = UUID.randomUUID().toString(),
            title = title.trim(),
            kind = kind,
            color = color,
            calendar = calendar,
            month = m,
            day = d,
            year = if (knownYear) y else null,
            remindDaysBefore = remindDaysBefore,
            reminderTime = reminderTime,
            notes = notes,
            createdAt = now,
            updatedAt = now,
        )
        events.upsert(event)
        reminders.sync(event)
        return event
    }

    suspend fun update(event: PersonalEvent) {
        val updated = event.copy(title = event.title.trim(), updatedAt = Instant.now(clock))
        events.upsert(updated)
        reminders.sync(updated)
    }

    /** Moves the event to [date] in its calendar. */
    suspend fun reschedule(event: PersonalEvent, date: LocalDate, calendar: EventCalendar, knownYear: Boolean, hijriOffset: Int = 0) {
        val (y, m, d) = parts(date, calendar, hijriOffset) ?: return
        update(event.copy(calendar = calendar, month = m, day = d, year = if (knownYear) y else null))
    }

    suspend fun delete(event: PersonalEvent) {
        events.delete(event.id)
        reminders.cancel(event.id)
    }

    companion object {
        /** (year, month, day) of [date] in [calendar]. */
        fun parts(date: LocalDate, calendar: EventCalendar, hijriOffset: Int = 0): Triple<Int, Int, Int>? = when (calendar) {
            EventCalendar.JALALI -> date.toJalali().let { Triple(it.year, it.month, it.day) }
            EventCalendar.GREGORIAN -> Triple(date.year, date.monthValue, date.dayOfMonth)
            EventCalendar.HIJRI -> HijriDates.from(date, hijriOffset)?.let { Triple(it.year, it.month, it.day) }
        }
    }
}
