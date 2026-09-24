package ir.roozban.core.calendar

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalTime

class PersianFormattingTest {
    private val date = JalaliDate.of(1405, 7, 2)
    private val today = date.toLocalDate()

    @Test
    fun digits() {
        assertThat(PersianDigits.toPersian("1405/07/02 17:30")).isEqualTo("۱۴۰۵/۰۷/۰۲ ۱۷:۳۰")
        assertThat(PersianDigits.toAscii("۱۴۰۵ و ٢٣")).isEqualTo("1405 و 23")
        assertThat(PersianDigits.format2(5)).isEqualTo("۰۵")
    }

    @Test
    fun dates() {
        assertThat(PersianDateFormatter.fullDate(date)).isEqualTo("پنجشنبه ۲ مهر ۱۴۰۵")
        assertThat(PersianDateFormatter.dayMonth(date)).isEqualTo("۲ مهر")
        assertThat(PersianDateFormatter.numeric(date)).isEqualTo("۱۴۰۵/۰۷/۰۲")
        assertThat(PersianDateFormatter.time(LocalTime.of(7, 5))).isEqualTo("۰۷:۰۵")
        assertThat(PersianDateFormatter.gregorian(today)).isEqualTo("۲۴ سپتامبر ۲۰۲۶")
    }

    @Test
    fun hijri() {
        // 24 Sep 2026 is 13 Rabi' al-Thani 1448 in Umm al-Qura.
        assertThat(HijriDates.from(today)).isEqualTo(HijriDate(1448, 4, 13))
        assertThat(HijriDates.from(today, offsetDays = -1)).isEqualTo(HijriDate(1448, 4, 12))
        assertThat(PersianDateFormatter.hijri(today)).isEqualTo("۱۳ ربیع‌الثانی ۱۴۴۸")
        assertThat(HijriDates.from(LocalDate.of(1800, 1, 1))).isNull()
    }

    @Test
    fun `relative days`() {
        fun rel(days: Long) = PersianDateFormatter.relativeDay(today.plusDays(days), today)
        assertThat(rel(0)).isEqualTo("امروز")
        assertThat(rel(1)).isEqualTo("فردا")
        assertThat(rel(2)).isEqualTo("پس‌فردا")
        assertThat(rel(-1)).isEqualTo("دیروز")
        assertThat(rel(3)).isEqualTo("یکشنبه")
        assertThat(rel(13)).isEqualTo("۱۵ مهر")
        assertThat(rel(200)).isEqualTo("۲۳ فروردین ۱۴۰۶")
    }
}
