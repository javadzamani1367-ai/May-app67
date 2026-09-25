package ir.roozban.feature.assistant

import ir.roozban.core.calendar.PersianDigits
import java.util.Locale

/** «۳۹۷ مگابایت», «۱٫۱ گیگابایت». */
internal fun formatSize(bytes: Long): String {
    val mb = bytes / (1024.0 * 1024)
    return if (mb < 1000) {
        "${PersianDigits.format(mb.toLong().coerceAtLeast(1))} مگابایت"
    } else {
        PersianDigits.toPersian(String.format(Locale.US, "%.1f", mb / 1024)).replace('.', '٫') + " گیگابایت"
    }
}
