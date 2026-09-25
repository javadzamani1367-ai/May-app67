package ir.roozban.core.calendar

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.MonthDay

enum class OccasionSource { SOLAR, LUNAR, GREGORIAN }

/** One entry of the official calendar: a public holiday or a (non-holiday) occasion. */
data class Occasion(
    val date: LocalDate,
    val title: String,
    val holiday: Boolean,
    val source: OccasionSource,
    /** Lunar dates follow moon sighting and may differ from the computed day by one. */
    val approximate: Boolean,
)

/**
 * The occasions of the Iranian official calendar: holidays (from [IranHolidays]) plus the
 * national, religious and international days printed in the calendar. Solar and Gregorian
 * dates are exact; lunar ones use the Hijri calendar with the user's offset.
 */
object IranOccasions {

    /** (Jalali month, day, title). */
    private val SOLAR = listOf(
        Triple(1, 20, "روز ملی فناوری هسته‌ای"),
        Triple(1, 25, "روز بزرگداشت عطار نیشابوری"),
        Triple(1, 29, "روز ارتش جمهوری اسلامی"),
        Triple(2, 1, "روز بزرگداشت سعدی"),
        Triple(2, 3, "روز بزرگداشت شیخ بهایی؛ روز معماری"),
        Triple(2, 9, "روز ملی شوراها"),
        Triple(2, 10, "روز ملی خلیج فارس"),
        Triple(2, 12, "روز معلم"),
        Triple(2, 25, "روز بزرگداشت فردوسی؛ پاسداشت زبان فارسی"),
        Triple(2, 27, "روز ارتباطات و روابط عمومی"),
        Triple(2, 28, "روز بزرگداشت حکیم عمر خیام"),
        Triple(3, 1, "روز بزرگداشت ملاصدرا"),
        Triple(3, 3, "فتح خرمشهر؛ روز مقاومت، ایثار و پیروزی"),
        Triple(3, 27, "روز جهاد کشاورزی"),
        Triple(3, 29, "درگذشت دکتر علی شریعتی"),
        Triple(4, 7, "روز قوه قضاییه"),
        Triple(4, 8, "روز مبارزه با سلاح‌های شیمیایی و میکروبی"),
        Triple(4, 10, "روز صنعت و معدن"),
        Triple(4, 14, "روز قلم"),
        Triple(4, 25, "روز بهزیستی و تأمین اجتماعی"),
        Triple(5, 8, "روز بزرگداشت شیخ شهاب‌الدین سهروردی"),
        Triple(5, 14, "سالروز صدور فرمان مشروطیت"),
        Triple(5, 17, "روز خبرنگار"),
        Triple(5, 26, "سالروز بازگشت آزادگان به میهن"),
        Triple(6, 1, "روز بزرگداشت ابوعلی سینا؛ روز پزشک"),
        Triple(6, 2, "آغاز هفته دولت"),
        Triple(6, 4, "روز کارمند"),
        Triple(6, 5, "روز بزرگداشت محمد بن زکریای رازی؛ روز داروسازی"),
        Triple(6, 8, "روز مبارزه با تروریسم"),
        Triple(6, 13, "روز بزرگداشت ابوریحان بیرونی"),
        Triple(6, 17, "قیام ۱۷ شهریور"),
        Triple(6, 21, "روز سینما"),
        Triple(6, 27, "روز شعر و ادب پارسی؛ بزرگداشت شهریار"),
        Triple(6, 31, "آغاز هفته دفاع مقدس"),
        Triple(7, 1, "آغاز سال تحصیلی"),
        Triple(7, 7, "روز آتش‌نشانی و ایمنی"),
        Triple(7, 8, "روز بزرگداشت مولوی"),
        Triple(7, 13, "روز نیروی انتظامی"),
        Triple(7, 14, "روز دامپزشکی"),
        Triple(7, 20, "روز بزرگداشت حافظ"),
        Triple(7, 26, "روز تربیت بدنی و ورزش"),
        Triple(8, 1, "روز آمار و برنامه‌ریزی"),
        Triple(8, 13, "روز دانش‌آموز"),
        Triple(8, 24, "روز کتاب و کتاب‌خوانی"),
        Triple(9, 5, "روز بسیج مستضعفان"),
        Triple(9, 7, "روز نیروی دریایی"),
        Triple(9, 10, "روز مجلس"),
        Triple(9, 13, "روز بیمه"),
        Triple(9, 16, "روز دانشجو"),
        Triple(9, 25, "روز پژوهش"),
        Triple(9, 26, "روز حمل و نقل"),
        Triple(9, 30, "شب یلدا"),
        Triple(10, 5, "سالروز زلزله بم"),
        Triple(10, 19, "قیام مردم قم"),
        Triple(10, 20, "سالروز شهادت امیرکبیر"),
        Triple(11, 12, "بازگشت امام خمینی؛ آغاز دهه فجر"),
        Triple(11, 19, "روز نیروی هوایی"),
        Triple(12, 5, "روز بزرگداشت خواجه نصیرالدین طوسی؛ روز مهندس"),
        Triple(12, 14, "روز احسان و نیکوکاری"),
        Triple(12, 15, "روز درختکاری"),
        Triple(12, 25, "روز بزرگداشت پروین اعتصامی"),
    )

    /** (Hijri month, day, title). Day -1 = last day of the month. */
    private val LUNAR = listOf(
        Triple(1, 1, "آغاز سال هجری قمری"),
        Triple(1, 25, "شهادت امام زین‌العابدین"),
        Triple(3, 12, "میلاد پیامبر به روایت اهل سنت؛ آغاز هفته وحدت"),
        Triple(4, 8, "ولادت امام حسن عسکری"),
        Triple(5, 5, "ولادت حضرت زینب؛ روز پرستار"),
        Triple(6, 20, "ولادت حضرت فاطمه؛ روز زن و مادر"),
        Triple(7, 1, "ولادت امام محمد باقر"),
        Triple(7, 3, "شهادت امام علی النقی"),
        Triple(7, 10, "ولادت امام محمد تقی"),
        Triple(7, 13, "روز پدر"),
        Triple(7, 15, "وفات حضرت زینب"),
        Triple(7, 25, "شهادت امام موسی کاظم"),
        Triple(8, 3, "ولادت امام حسین؛ روز پاسدار"),
        Triple(8, 4, "ولادت حضرت ابوالفضل؛ روز جانباز"),
        Triple(8, 5, "ولادت امام زین‌العابدین"),
        Triple(8, 11, "ولادت حضرت علی اکبر؛ روز جوان"),
        Triple(9, 1, "آغاز ماه رمضان"),
        Triple(9, 15, "ولادت امام حسن مجتبی"),
        Triple(9, 19, "ضربت خوردن امام علی؛ شب قدر"),
        Triple(9, 23, "شب قدر"),
        Triple(11, 1, "ولادت حضرت معصومه؛ روز دختران"),
        Triple(11, 11, "ولادت امام رضا"),
        Triple(11, -1, "شهادت امام محمد تقی"),
        Triple(12, 1, "سالروز ازدواج حضرت علی و حضرت فاطمه؛ روز ازدواج"),
        Triple(12, 7, "شهادت امام محمد باقر"),
        Triple(12, 9, "روز عرفه"),
        Triple(12, 15, "ولادت امام علی النقی"),
        Triple(12, 24, "روز مباهله"),
    )

    /** International days that appear in the Iranian calendar. */
    private val GREGORIAN = listOf(
        MonthDay.of(1, 1) to "آغاز سال نو میلادی",
        MonthDay.of(4, 7) to "روز جهانی بهداشت",
        MonthDay.of(4, 22) to "روز جهانی زمین پاک",
        MonthDay.of(5, 1) to "روز جهانی کار و کارگر",
        MonthDay.of(5, 18) to "روز جهانی موزه و میراث فرهنگی",
        MonthDay.of(6, 5) to "روز جهانی محیط زیست",
        MonthDay.of(9, 27) to "روز جهانی جهانگردی",
        MonthDay.of(10, 16) to "روز جهانی غذا",
        MonthDay.of(12, 3) to "روز جهانی افراد دارای معلولیت",
        MonthDay.of(12, 25) to "ولادت حضرت عیسی مسیح (کریسمس)",
    )

    private val cache = HashMap<Pair<Int, Int>, List<Occasion>>()

    /** Every occasion of a Jalali year (holidays included), ordered by date, holidays first. */
    fun forJalaliYear(year: Int, hijriOffset: Int = 0, overrides: IranHolidays.Overrides = IranHolidays.Overrides()): List<Occasion> {
        val base = synchronized(cache) { cache.getOrPut(year to hijriOffset) { compute(year, hijriOffset) } }
        val holidays = IranHolidays.forJalaliYear(year, hijriOffset, overrides).map {
            Occasion(it.date, it.title, holiday = true, source = if (it.approximate) OccasionSource.LUNAR else OccasionSource.SOLAR, approximate = it.approximate)
        }
        return (holidays + base).sortedWith(compareBy<Occasion> { it.date }.thenByDescending { it.holiday })
    }

    fun forJalaliMonth(year: Int, month: Int, hijriOffset: Int = 0, overrides: IranHolidays.Overrides = IranHolidays.Overrides()): List<Occasion> =
        forJalaliYear(year, hijriOffset, overrides).filter { it.date.toJalali().month == month }

    fun on(date: LocalDate, hijriOffset: Int = 0, overrides: IranHolidays.Overrides = IranHolidays.Overrides()): List<Occasion> =
        forJalaliYear(date.toJalali().year, hijriOffset, overrides).filter { it.date == date }

    private fun compute(year: Int, hijriOffset: Int): List<Occasion> {
        val result = ArrayList<Occasion>()
        for ((m, d, title) in SOLAR) {
            if (d <= JalaliDate.monthLength(year, m)) result += Occasion(JalaliDate.of(year, m, d).toLocalDate(), title, false, OccasionSource.SOLAR, false)
        }
        // «چهارشنبه‌سوری»: the eve of the last Wednesday of the year.
        val lastDay = JalaliDate.of(year, 12, 1).lastDayOfMonth().toLocalDate()
        var tuesday = lastDay
        while (tuesday.dayOfWeek != DayOfWeek.TUESDAY) tuesday = tuesday.minusDays(1)
        result += Occasion(tuesday, "چهارشنبه‌سوری", false, OccasionSource.SOLAR, false)

        val first = JalaliDate.of(year, 1, 1).toLocalDate()
        var date = first
        var hijri = HijriDates.from(date, hijriOffset)
        while (!date.isAfter(lastDay)) {
            val next = date.plusDays(1)
            val nextHijri = HijriDates.from(next, hijriOffset)
            if (hijri != null) {
                val isLastDay = nextHijri != null && nextHijri.month != hijri.month
                for ((m, d, title) in LUNAR) {
                    if (hijri.month == m && (hijri.day == d || (d == -1 && isLastDay))) {
                        result += Occasion(date, title, false, OccasionSource.LUNAR, approximate = true)
                    }
                }
            }
            for ((md, title) in GREGORIAN) {
                if (date.monthValue == md.monthValue && date.dayOfMonth == md.dayOfMonth) {
                    result += Occasion(date, title, false, OccasionSource.GREGORIAN, false)
                }
            }
            date = next
            hijri = nextHijri
        }
        return result
    }
}
