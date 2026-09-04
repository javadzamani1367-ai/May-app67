package ir.ilam.inspection.util

import ir.ilam.inspection.data.model.ReportType

/**
 * Tracking codes: `[type letter]-[area code]-[YYMMDD jalali]-[last 6 of the
 * subscription number]`, e.g. `M-01-050614-482917`.
 *
 * Codes are stored in latin digits — they travel to the Windows archive and
 * into file names — and are only shaped to Persian digits for display.
 */
object TrackingCode {

    const val SEPARATOR = "-"
    private const val SUBSCRIPTION_DIGITS = 6

    /**
     * Final code. Returns null when the type carries an externally issued code
     * (the Soragh system) or the subscription number is not known yet.
     */
    fun generate(type: ReportType, areaCode: String, reportDate: Long, subscription: String?): String? {
        val letter = type.letter ?: return null
        val tail = subscriptionTail(subscription) ?: return null
        return listOf(letter, normalizeArea(areaCode), PersianDate.trackingStamp(reportDate), tail)
            .joinToString(SEPARATOR)
    }

    /**
     * Placeholder used while the subscription number is unknown:
     * `M-01-050614-T0003`, where 3 is that day's sequence number.
     */
    fun temporary(type: ReportType, areaCode: String, reportDate: Long, dailySequence: Int): String? {
        val letter = type.letter ?: return null
        val tail = "T" + "%04d".format(dailySequence.coerceIn(0, 9999))
        return listOf(letter, normalizeArea(areaCode), PersianDate.trackingStamp(reportDate), tail)
            .joinToString(SEPARATOR)
    }

    /** Last six digits of the subscription number, right padded when shorter. */
    fun subscriptionTail(subscription: String?): String? {
        val digits = PersianNumbers.toLatin(subscription).filter { it.isDigit() }
        if (digits.isEmpty()) return null
        return if (digits.length >= SUBSCRIPTION_DIGITS) {
            digits.takeLast(SUBSCRIPTION_DIGITS)
        } else {
            digits.padStart(SUBSCRIPTION_DIGITS, '0')
        }
    }

    fun isTemporary(code: String?): Boolean =
        code != null && code.substringAfterLast(SEPARATOR).startsWith("T")

    private fun normalizeArea(areaCode: String): String {
        val digits = PersianNumbers.toLatin(areaCode).filter { it.isDigit() }
        return when {
            digits.isEmpty() -> "00"
            digits.length == 1 -> "0$digits"
            else -> digits.take(2)
        }
    }

    /** Same code, Persian digits, for anything the expert reads on screen. */
    fun forDisplay(code: String?): String = PersianNumbers.toPersian(code ?: "")
}
