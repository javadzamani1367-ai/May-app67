package ir.roozban.core.domain

import ir.roozban.core.calendar.IranHolidays
import ir.roozban.core.calendar.JalaliDate
import ir.roozban.core.calendar.toJalali
import ir.roozban.core.model.AttendanceEntry
import ir.roozban.core.model.AttendanceKind
import ir.roozban.core.model.AttendanceSettings
import ir.roozban.core.model.LeaveType
import kotlinx.coroutines.flow.Flow
import java.time.Clock
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import javax.inject.Inject

interface AttendanceRepository {
    /** Entries touching [from]..[to] (inclusive), oldest first. */
    fun observeBetween(from: LocalDate, to: LocalDate): Flow<List<AttendanceEntry>>

    /** The check-in without a check-out, if any. */
    fun observeOpen(): Flow<AttendanceEntry?>

    suspend fun open(): AttendanceEntry?

    suspend fun upsert(entry: AttendanceEntry)

    suspend fun delete(id: String)
}

class AttendanceUseCases @Inject constructor(
    private val repository: AttendanceRepository,
    private val clock: Clock,
) {
    private fun now() = LocalDateTime.now(clock).withSecond(0).withNano(0)

    /** Check in now; a check-in left open is closed first. */
    suspend fun checkIn(): AttendanceEntry {
        repository.open()?.let { checkOut() }
        val t = Instant.now(clock)
        return AttendanceEntry(UUID.randomUUID().toString(), AttendanceKind.WORK, now(), null, createdAt = t, updatedAt = t)
            .also { repository.upsert(it) }
    }

    /** Check out now; null when nobody checked in. */
    suspend fun checkOut(): AttendanceEntry? {
        val open = repository.open() ?: return null
        val end = maxOf(now(), open.start)
        return open.copy(end = end, updatedAt = Instant.now(clock)).also { repository.upsert(it) }
    }

    suspend fun save(entry: AttendanceEntry) = repository.upsert(entry.copy(updatedAt = Instant.now(clock)))

    fun new(kind: AttendanceKind, start: LocalDateTime, end: LocalDateTime?, allDay: Boolean, leaveType: LeaveType?, note: String): AttendanceEntry {
        val t = Instant.now(clock)
        return AttendanceEntry(UUID.randomUUID().toString(), kind, start, end, allDay, leaveType.takeIf { kind == AttendanceKind.LEAVE }, note.trim(), t, t)
    }

    suspend fun delete(entry: AttendanceEntry): Undo {
        repository.delete(entry.id)
        return Undo { repository.upsert(entry) }
    }
}

/** One day of the monthly attendance report. Minutes throughout. */
data class AttendanceDay(
    val date: LocalDate,
    val required: Int,
    val holiday: Boolean,
    val worked: Int,
    val mission: Int,
    val leave: Map<LeaveType, Int>,
    /** A whole-day mission or leave covers the day. */
    val missionDay: Boolean,
    val leaveDay: LeaveType?,
    val firstIn: LocalDateTime?,
    val lastOut: LocalDateTime?,
    /** Checked in and never checked out (a past day). */
    val missingCheckOut: Boolean,
) {
    /** Time that counts towards [required]: work, missions and leave. */
    val credited: Int get() = if (missionDay || leaveDay != null) maxOf(required, worked + mission) else worked + mission + leave.values.sum()
    val present: Boolean get() = worked > 0 || mission > 0 || missionDay
}

data class AttendanceMonth(
    val year: Int,
    val month: Int,
    val days: List<AttendanceDay>,
) {
    val workedMinutes: Int get() = days.sumOf { it.worked }
    val missionMinutes: Int get() = days.sumOf { it.mission }
    val presentDays: Int get() = days.count { it.present }
    val missionDays: Int get() = days.count { it.missionDay }
    val requiredMinutes: Int get() = days.sumOf { it.required }

    /** Whole days of leave by type, workdays only. */
    val leaveDays: Map<LeaveType, Int> get() = days.mapNotNull { d -> d.leaveDay?.takeIf { d.required > 0 } }.groupingBy { it }.eachCount()

    /** Hourly leave by type, in minutes. */
    val leaveMinutes: Map<LeaveType, Int> get() = LeaveType.entries.associateWith { t -> days.sumOf { it.leave[t] ?: 0 } }.filterValues { it > 0 }

    /** Time beyond the requirement, day by day. */
    val overtimeMinutes: Int get() = days.sumOf { maxOf(0, it.credited - it.required) }

    /** Time short of the requirement on workdays so far (today counts once it is over). */
    fun shortfallMinutes(today: LocalDate): Int = days.filter { it.date.isBefore(today) }.sumOf { maxOf(0, it.required - it.credited) }

    /** Workdays so far with nothing recorded. */
    fun absentDays(today: LocalDate): Int = days.count { it.date.isBefore(today) && it.required > 0 && it.credited == 0 }
}

/** Builds the monthly report from the raw entries. Pure, so it is tested without a database. */
object AttendanceReport {
    fun month(
        entries: List<AttendanceEntry>,
        year: Int,
        month: Int,
        settings: AttendanceSettings,
        now: LocalDateTime,
        hijriOffset: Int = 0,
        isHoliday: (LocalDate) -> Boolean = { IranHolidays.on(it, hijriOffset).isNotEmpty() },
    ): AttendanceMonth {
        val first = JalaliDate.of(year, month, 1)
        val days = (0 until first.lengthOfMonth).map { i ->
            day(first.plusDays(i.toLong()).toLocalDate(), entries, settings, now, isHoliday)
        }
        return AttendanceMonth(year, month, days)
    }

    fun required(date: LocalDate, settings: AttendanceSettings, holiday: Boolean): Int = when {
        holiday || date.dayOfWeek == DayOfWeek.FRIDAY -> 0
        date.dayOfWeek == DayOfWeek.THURSDAY -> settings.thursdayMinutes
        else -> settings.dailyMinutes
    }

    private fun day(
        date: LocalDate,
        entries: List<AttendanceEntry>,
        settings: AttendanceSettings,
        now: LocalDateTime,
        isHoliday: (LocalDate) -> Boolean,
    ): AttendanceDay {
        val holiday = isHoliday(date)
        val dayStart = date.atStartOfDay()
        val dayEnd = date.plusDays(1).atStartOfDay()
        var worked = 0
        var mission = 0
        val leave = mutableMapOf<LeaveType, Int>()
        var missionDay = false
        var leaveDay: LeaveType? = null
        var firstIn: LocalDateTime? = null
        var lastOut: LocalDateTime? = null
        var missing = false
        for (e in entries) {
            if (e.allDay) {
                val last = (e.end ?: e.start).toLocalDate()
                if (date.isBefore(e.start.toLocalDate()) || date.isAfter(last)) continue
                when (e.kind) {
                    AttendanceKind.MISSION -> missionDay = true
                    AttendanceKind.LEAVE -> leaveDay = e.leaveType ?: LeaveType.ANNUAL
                    AttendanceKind.WORK -> Unit
                }
                continue
            }
            val open = e.end == null
            // An open check-in runs until now if it is today's; one left from an earlier day is flagged.
            val end = e.end ?: if (e.kind == AttendanceKind.WORK && e.start.toLocalDate() == now.toLocalDate()) now else null
            if (end == null) {
                if (e.start.toLocalDate() == date) missing = true
                continue
            }
            val from = maxOf(e.start, dayStart)
            val to = minOf(end, dayEnd)
            if (!to.isAfter(from)) continue
            val minutes = Duration.between(from, to).toMinutes().toInt()
            when (e.kind) {
                AttendanceKind.WORK -> {
                    worked += minutes
                    if (firstIn == null || from.isBefore(firstIn)) firstIn = from
                    if (!open && (lastOut == null || to.isAfter(lastOut))) lastOut = to
                }
                AttendanceKind.MISSION -> mission += minutes
                AttendanceKind.LEAVE -> {
                    val t = e.leaveType ?: LeaveType.ANNUAL
                    leave[t] = (leave[t] ?: 0) + minutes
                }
            }
        }
        return AttendanceDay(
            date = date,
            required = required(date, settings, holiday),
            holiday = holiday,
            worked = worked,
            mission = mission,
            leave = leave,
            missionDay = missionDay,
            leaveDay = leaveDay,
            firstIn = firstIn,
            lastOut = lastOut,
            missingCheckOut = missing,
        )
    }

    /** «۸:۳۰» from minutes. */
    fun hours(minutes: Int): String = "%d:%02d".format(minutes / 60, minutes % 60)

    /** The month as CSV (for a spreadsheet), one row per day. */
    fun csv(m: AttendanceMonth, today: LocalDate): String = buildString {
        append("تاریخ,روز,ورود,خروج,کارکرد,مأموریت,مرخصی,موظف,اضافه‌کار,کسرکار,توضیح\n")
        for (d in m.days) {
            val j = d.date.toJalali()
            val leave = d.leaveDay?.let { "روزانه ${leaveName(it)}" } ?: d.leave.entries.joinToString(" ") { "${leaveName(it.key)} ${hours(it.value)}" }
            val note = listOfNotNull(
                "تعطیل".takeIf { d.holiday },
                "مأموریت روزانه".takeIf { d.missionDay },
                "خروج ثبت نشده".takeIf { d.missingCheckOut },
            ).joinToString(" / ")
            append("${j.year}/${j.month}/${j.day},${weekday(d.date.dayOfWeek)},${d.firstIn?.toLocalTime() ?: ""},${d.lastOut?.toLocalTime() ?: ""},")
            append("${hours(d.worked)},${hours(d.mission)},$leave,${hours(d.required)},${hours(maxOf(0, d.credited - d.required))},")
            append("${if (d.date.isBefore(today)) hours(maxOf(0, d.required - d.credited)) else ""},$note\n")
        }
        append("جمع,,,,${hours(m.workedMinutes)},${hours(m.missionMinutes)},,${hours(m.requiredMinutes)},${hours(m.overtimeMinutes)},${hours(m.shortfallMinutes(today))},\n")
    }

    fun leaveName(t: LeaveType) = when (t) {
        LeaveType.ANNUAL -> "استحقاقی"
        LeaveType.SICK -> "استعلاجی"
        LeaveType.UNPAID -> "بدون حقوق"
    }

    private fun weekday(d: DayOfWeek) = when (d) {
        DayOfWeek.SATURDAY -> "شنبه"
        DayOfWeek.SUNDAY -> "یکشنبه"
        DayOfWeek.MONDAY -> "دوشنبه"
        DayOfWeek.TUESDAY -> "سه‌شنبه"
        DayOfWeek.WEDNESDAY -> "چهارشنبه"
        DayOfWeek.THURSDAY -> "پنجشنبه"
        DayOfWeek.FRIDAY -> "جمعه"
    }
}
