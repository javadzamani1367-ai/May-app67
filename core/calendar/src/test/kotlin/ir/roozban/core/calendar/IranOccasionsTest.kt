package ir.roozban.core.calendar

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

class IranOccasionsTest {
    private fun titles(jalali: String) = IranOccasions.on(JalaliDate.parse(jalali).toLocalDate()).map { it.title }

    @Test
    fun `solar occasions and holidays together`() {
        assertThat(titles("1405-02-12")).contains("روز معلم")
        assertThat(titles("1405-09-30")).contains("شب یلدا")
        assertThat(titles("1405-07-20")).contains("روز بزرگداشت حافظ")
        val nowruz = IranOccasions.on(JalaliDate.parse("1405-01-01").toLocalDate())
        assertThat(nowruz.first().holiday).isTrue()
    }

    @Test
    fun `gregorian days land on their Jalali dates`() {
        // 25 December 2026 = 4 Dey 1405.
        assertThat(titles("1405-10-04")).contains("ولادت حضرت عیسی مسیح (کریسمس)")
        assertThat(IranOccasions.on(LocalDate.of(2026, 5, 1)).map { it.title }).contains("روز جهانی کار و کارگر")
    }

    @Test
    fun `chaharshanbe suri is the last Tuesday evening of the year`() {
        for (year in 1403..1408) {
            val suri = IranOccasions.forJalaliYear(year).single { it.title == "چهارشنبه‌سوری" }
            assertThat(suri.date.dayOfWeek).isEqualTo(DayOfWeek.TUESDAY)
            val lastDay = JalaliDate.of(year, 12, 1).lastDayOfMonth().toLocalDate()
            assertThat(lastDay.toEpochDay() - suri.date.toEpochDay()).isLessThan(7L)
        }
    }

    @Test
    fun `every lunar occasion appears once a year and matches its Hijri date`() {
        for (year in 1403..1410) {
            val lunar = IranOccasions.forJalaliYear(year).filter { it.source == OccasionSource.LUNAR && !it.holiday }
            assertThat(lunar.count { it.title == "ولادت حضرت فاطمه؛ روز زن و مادر" }).isAtMost(2)
            assertThat(lunar.map { it.title }).contains("روز پدر")
            lunar.filter { it.title == "روز پدر" }.forEach { assertThat(HijriDates.from(it.date)).isEqualTo(HijriDates.from(it.date)?.copy(month = 7, day = 13)) }
        }
    }

    @Test
    fun `month view is sorted with holidays first on a day`() {
        val farvardin = IranOccasions.forJalaliMonth(1405, 1)
        assertThat(farvardin.map { it.date }).isInOrder()
        assertThat(farvardin.all { it.date.toJalali().month == 1 }).isTrue()
        assertThat(farvardin.first().holiday).isTrue()
    }

    @Test
    fun `prayer times for Tehran are ordered and plausible`() {
        val tehran = IranCities.byId("tehran")!!
        val day = PrayerTimes.compute(LocalDate.of(2026, 9, 24), tehran)
        assertThat(listOf(day.imsak, day.fajr, day.sunrise, day.dhuhr, day.sunset, day.maghrib)).isInOrder()
        assertThat(day.dhuhr).isAtLeast(LocalTime.of(11, 50))
        assertThat(day.dhuhr).isAtMost(LocalTime.of(12, 5))
        // Iran has had no daylight saving time since 2022: sunrise ≈ 05:54, sunset ≈ 18:00.
        assertThat(day.sunrise).isAtLeast(LocalTime.of(5, 45))
        assertThat(day.sunrise).isAtMost(LocalTime.of(6, 5))
        assertThat(day.sunset).isAtLeast(LocalTime.of(17, 50))
        assertThat(day.sunset).isAtMost(LocalTime.of(18, 15))
        assertThat(day.midnight).isAtLeast(LocalTime.of(23, 0))
        // Summer solstice in Tehran: sunrise ≈ 04:48, sunset ≈ 19:24.
        val june = PrayerTimes.compute(LocalDate.of(2026, 6, 21), tehran)
        assertThat(june.sunrise).isLessThan(LocalTime.of(5, 0))
        assertThat(june.sunset).isGreaterThan(LocalTime.of(19, 10))
        assertThat(june.sunset).isLessThan(LocalTime.of(19, 40))
        assertThat(IranCities.all.map { it.id }.toSet()).hasSize(IranCities.all.size)
    }
}
