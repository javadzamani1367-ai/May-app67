package ir.roozban.core.model

import java.time.Instant
import java.time.LocalTime

enum class EventKind { BIRTHDAY, WEDDING, ENGAGEMENT, MEMORIAL, OTHER }

/** Which calendar an anniversary follows (a birthday by the Jalali date, a religious date by the Hijri one, ...). */
enum class EventCalendar { JALALI, GREGORIAN, HIJRI }

/**
 * A personal occasion: a birthday, a wedding or «عقد» anniversary, a memorial or anything the user
 * wants marked on the calendar. Stored as month/day in [calendar] so it repeats on the right day.
 */
data class PersonalEvent(
    val id: String,
    val title: String,
    val kind: EventKind = EventKind.OTHER,
    val color: Int = 0,
    val calendar: EventCalendar = EventCalendar.JALALI,
    val month: Int,
    val day: Int,
    /** Year of the first occurrence in [calendar]; lets the app say «۳۰ سالگی». */
    val year: Int? = null,
    val yearly: Boolean = true,
    /** Days before the occasion to remind (0 = on the day). Empty = no reminder. */
    val remindDaysBefore: Set<Int> = setOf(0, 1),
    val reminderTime: LocalTime = LocalTime.of(9, 0),
    val notes: String = "",
    val createdAt: Instant,
    val updatedAt: Instant,
)
