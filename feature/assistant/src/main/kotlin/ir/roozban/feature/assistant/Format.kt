package ir.roozban.feature.assistant

import ir.roozban.core.calendar.PersianDigits
import java.util.Locale

/** «۳۹۷ مگابایت», «۱٫۱ گیگابایت». */
internal fun formatSize(bytes: Long): String {
    val mb = bytes / 1_000_000.0
    return if (mb < 1000) {
        "${PersianDigits.format(mb.toLong().coerceAtLeast(1))} مگابایت"
    } else {
        PersianDigits.toPersian(String.format(Locale.US, "%.1f", mb / 1000)).replace('.', '٫') + " گیگابایت"
    }
}
