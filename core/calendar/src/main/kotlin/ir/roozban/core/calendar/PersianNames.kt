package ir.roozban.core.calendar

import java.time.DayOfWeek

object PersianNames {
    val JALALI_MONTHS = listOf(
        "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
        "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند",
    )

    val GREGORIAN_MONTHS = listOf(
        "ژانویه", "فوریه", "مارس", "آوریل", "مه", "ژوئن",
        "ژوئیه", "اوت", "سپتامبر", "اکتبر", "نوامبر", "دسامبر",
    )

    val HIJRI_MONTHS = listOf(
        "محرم", "صفر", "ربیع‌الاول", "ربیع‌الثانی", "جمادی‌الاول", "جمادی‌الثانی",
        "رجب", "شعبان", "رمضان", "شوال", "ذی‌القعده", "ذی‌الحجه",
    )

    /** Weekday names in Iranian order (index 0 = Saturday). */
    val WEEKDAYS = listOf("شنبه", "یکشنبه", "دوشنبه", "سه‌شنبه", "چهارشنبه", "پنجشنبه", "جمعه")

    /** One-letter weekday labels for calendar headers. */
    val WEEKDAYS_SHORT = listOf("ش", "ی", "د", "س", "چ", "پ", "ج")

    fun jalaliMonth(month: Int): String = JALALI_MONTHS[month - 1]

    fun weekday(day: DayOfWeek): String = WEEKDAYS[PersianWeek.indexOf(day)]
}
