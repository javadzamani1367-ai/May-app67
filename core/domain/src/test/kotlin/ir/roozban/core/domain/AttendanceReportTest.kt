package ir.roozban.core.domain

import com.google.common.truth.Truth.assertThat
import ir.roozban.core.calendar.JalaliDate
import ir.roozban.core.model.AttendanceEntry
import ir.roozban.core.model.AttendanceKind
import ir.roozban.core.model.AttendanceSettings
import ir.roozban.core.model.LeaveType
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class AttendanceReportTest {
    private val settings = AttendanceSettings(dailyMinutes = 480, thursdayMinutes = 240)
    private fun j(d: Int): LocalDate = JalaliDate.of(1405, 7, d).toLocalDate()
    private fun at(d: Int, h: Int, m: Int = 0): LocalDateTime = j(d).atTime(LocalTime.of(h, m))
    private var n = 0
    private fun entry(kind: AttendanceKind, start: LocalDateTime, end: LocalDateTime?, allDay: Boolean = false, leave: LeaveType? = null) =
        AttendanceEntry("e${n++}", kind, start, end, allDay, leave, "", Instant.EPOCH, Instant.EPOCH)

    private fun report(vararg e: AttendanceEntry, now: LocalDateTime = at(30, 23)) =
        AttendanceReport.month(e.toList(), 1405, 7, settings, now, isHoliday = { false })

    @Test
    fun `worked time, overtime and first in last out`() {
        // 1 Mehr 1405 is a Thursday? Use day 5 whatever it is; required comes from the weekday.
        val d = (1..30).first { j(it).dayOfWeek == java.time.DayOfWeek.SATURDAY }
        val m = report(entry(AttendanceKind.WORK, at(d, 7, 30), at(d, 12)), entry(AttendanceKind.WORK, at(d, 13), at(d, 17, 30)))
        val day = m.days[d - 1]
        assertThat(day.worked).isEqualTo(540)
        assertThat(day.firstIn).isEqualTo(at(d, 7, 30))
        assertThat(day.lastOut).isEqualTo(at(d, 17, 30))
        assertThat(m.overtimeMinutes).isEqualTo(60)
        assertThat(m.presentDays).isEqualTo(1)
    }

    @Test
    fun `Friday is off and Thursday has its own hours`() {
        val fri = (1..30).first { j(it).dayOfWeek == java.time.DayOfWeek.FRIDAY }
        val thu = (1..30).first { j(it).dayOfWeek == java.time.DayOfWeek.THURSDAY }
        val m = report()
        assertThat(m.days[fri - 1].required).isEqualTo(0)
        assertThat(m.days[thu - 1].required).isEqualTo(240)
    }

    @Test
    fun `hourly leave and missions count towards the day`() {
        val d = (1..30).first { j(it).dayOfWeek == java.time.DayOfWeek.SUNDAY }
        val m = report(
            entry(AttendanceKind.WORK, at(d, 8), at(d, 12)),
            entry(AttendanceKind.MISSION, at(d, 12), at(d, 14)),
            entry(AttendanceKind.LEAVE, at(d, 14), at(d, 16), leave = LeaveType.ANNUAL),
        )
        val day = m.days[d - 1]
        assertThat(day.credited).isEqualTo(480)
        assertThat(m.leaveMinutes).containsExactly(LeaveType.ANNUAL, 120)
        assertThat(m.shortfallMinutes(j(30))).isEqualTo(m.days.filter { it.date.isBefore(j(30)) && it.date != j(d) }.sumOf { it.required })
    }

    @Test
    fun `whole-day leave and missions over several days`() {
        val m = report(
            entry(AttendanceKind.LEAVE, at(10, 0), at(12, 0), allDay = true, leave = LeaveType.SICK),
            entry(AttendanceKind.MISSION, at(20, 0), at(21, 0), allDay = true),
        )
        val workdays = (10..12).count { AttendanceReport.required(j(it), settings, false) > 0 }
        assertThat(m.leaveDays[LeaveType.SICK]).isEqualTo(workdays)
        assertThat(m.missionDays).isEqualTo(2)
        assertThat(m.days[9].credited).isEqualTo(m.days[9].required)
    }

    @Test
    fun `a shift past midnight is split between the two days`() {
        val m = report(entry(AttendanceKind.WORK, at(3, 22), at(4, 6)))
        assertThat(m.days[2].worked).isEqualTo(120)
        assertThat(m.days[3].worked).isEqualTo(360)
    }

    @Test
    fun `an open check-in counts until now today, and is flagged on an earlier day`() {
        val today = report(entry(AttendanceKind.WORK, at(15, 8), null), now = at(15, 11))
        assertThat(today.days[14].worked).isEqualTo(180)
        val forgot = report(entry(AttendanceKind.WORK, at(14, 8), null), now = at(15, 11))
        assertThat(forgot.days[13].worked).isEqualTo(0)
        assertThat(forgot.days[13].missingCheckOut).isTrue()
    }

    @Test
    fun `csv has a row per day and a total`() {
        val m = report(entry(AttendanceKind.WORK, at(3, 8), at(3, 16)))
        val lines = AttendanceReport.csv(m, j(30)).trim().lines()
        assertThat(lines).hasSize(1 + 30 + 1)
        assertThat(lines[3]).contains("08:00")
    }
}
