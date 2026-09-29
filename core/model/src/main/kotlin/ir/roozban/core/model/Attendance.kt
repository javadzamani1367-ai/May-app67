package ir.roozban.core.model

import java.time.Instant
import java.time.LocalDateTime

/** What an attendance entry records. */
enum class AttendanceKind {
    /** Present at work: from check-in to check-out. */
    WORK,

    /** On a work assignment away from the office; counts as working time. */
    MISSION,

    /** Time off. */
    LEAVE,
}

/** Kinds of leave in Iranian workplaces. */
enum class LeaveType {
    /** استحقاقی */
    ANNUAL,

    /** استعلاجی */
    SICK,

    /** بدون حقوق */
    UNPAID,
}

/**
 * One attendance entry. Times are the phone's local wall-clock time. An [allDay] entry (a
 * whole-day mission or leave) covers every day from [start]'s date to [end]'s date; otherwise it
 * covers the time between them. A check-in not yet checked out has no [end].
 */
data class AttendanceEntry(
    val id: String,
    val kind: AttendanceKind,
    val start: LocalDateTime,
    val end: LocalDateTime?,
    val allDay: Boolean = false,
    val leaveType: LeaveType? = null,
    val note: String = "",
    val createdAt: Instant,
    val updatedAt: Instant,
)

/** Working hours the monthly report measures against. Fridays and official holidays are off. */
data class AttendanceSettings(
    /** Required minutes on Saturday to Wednesday. */
    val dailyMinutes: Int = 8 * 60,
    /** Required minutes on Thursday; 0 = day off. */
    val thursdayMinutes: Int = 0,
)
