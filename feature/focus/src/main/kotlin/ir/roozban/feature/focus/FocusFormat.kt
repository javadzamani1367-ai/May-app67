package ir.roozban.feature.focus

import ir.roozban.core.calendar.PersianDigits

/** «۲۴:۵۹» from milliseconds, rounded up so the display never shows 00:00 while time is left. */
fun formatCountdown(millis: Long): String {
    val seconds = (millis + 999) / 1000
    return PersianDigits.format2((seconds / 60).toInt()) + ":" + PersianDigits.format2((seconds % 60).toInt())
}
