package ir.roozban.core.calendar

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class IranHolidaysTest {

    private fun titlesOn(jalali: String, offset: Int = 0) = IranHolidays.on(JalaliDate.parse(jalali).toLocalDate(), offset).map { it.title }

    @Test
    fun `fixed solar holidays`() {
        assertThat(titlesOn("1405-01-01")).contains("عید نوروز")
        assertThat(titlesOn("1405-01-04")).contains("عید نوروز")
        assertThat(titlesOn("1405-01-13")).contains("روز طبیعت")
        assertThat(titlesOn("1405-03-14")).contains("رحلت امام خمینی")
        assertThat(titlesOn("1405-11-22")).contains("پیروزی انقلاب اسلامی")
        assertThat(titlesOn("1405-12-29")).contains("ملی شدن صنعت نفت")
        assertThat(IranHolidays.on(JalaliDate.parse("1405-01-01").toLocalDate()).first().approximate).isFalse()
    }

    @Test
    fun `lunar holidays fall on their Hijri dates`() {
        for (offset in -1..1) {
            val lunar = IranHolidays.forJalaliYear(1405, offset).filter { it.approximate }
            assertThat(lunar).isNotEmpty()
            lunar.filter { it.title == "عاشورای حسینی" }.forEach {
                assertThat(HijriDates.from(it.date, offset)).isEqualTo(HijriDate(HijriDates.from(it.date, offset)!!.year, 1, 10))
            }
            lunar.filter { it.title == "عید فطر" }.forEach {
                val h = HijriDates.from(it.date, offset)!!
                assertThat(h.month to h.day).isEqualTo(10 to 1)
            }
            // «شهادت امام رضا» is the last day of Safar, whether Safar has 29 or 30 days.
            lunar.filter { it.title == "شهادت امام رضا" }.forEach {
                assertThat(HijriDates.from(it.date, offset)!!.month).isEqualTo(2)
                assertThat(HijriDates.from(it.date.plusDays(1), offset)!!.month).isEqualTo(3)
            }
        }
    }

    @Test
    fun `every lunar holiday occurs at least once per Jalali year`() {
        for (year in 1403..1410) {
            val titles = IranHolidays.forJalaliYear(year).filter { it.approximate }.map { it.title }.toSet()
            assertThat(titles).hasSize(17)
        }
    }

    @Test
    fun `offset shifts lunar holidays by one day`() {
        val base = IranHolidays.forJalaliYear(1405, 0).first { it.title == "عاشورای حسینی" }.date
        val shifted = IranHolidays.forJalaliYear(1405, -1).first { it.title == "عاشورای حسینی" }.date
        assertThat(shifted).isEqualTo(base.plusDays(1))
    }

    @Test
    fun overrides() {
        val date = JalaliDate.parse("1405-05-01").toLocalDate()
        val overrides = IranHolidays.Overrides(added = listOf(Holiday(date, "تعطیل ویژه", approximate = false)))
        assertThat(IranHolidays.on(date, overrides = overrides).map { it.title }).containsExactly("تعطیل ویژه")
        val nowruz = JalaliDate.parse("1405-01-01").toLocalDate()
        assertThat(IranHolidays.on(nowruz, overrides = IranHolidays.Overrides(removed = setOf(nowruz.toEpochDay())))).isEmpty()
    }
}
