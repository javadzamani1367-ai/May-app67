package ir.ilam.inspection.data.model

import ir.ilam.inspection.R

/**
 * A case leaves the pending queue only when the field work is genuinely
 * finished. Anything missing is reported back to the expert by name — the app
 * never silently refuses.
 */
object Completion {

    /** String resources naming what is still missing; empty means ready. */
    fun missing(detail: ReportDetail): List<Int> {
        val report = detail.report
        val problems = mutableListOf<Int>()
        if (report.latitude == null || report.longitude == null) problems += R.string.missing_gps
        if (detail.photos.isEmpty()) problems += R.string.missing_photo
        if (report.meterAmperage == null) problems += R.string.missing_meter_amperage
        if (report.description.isNullOrBlank()) problems += R.string.missing_description
        return problems
    }

    fun isComplete(detail: ReportDetail): Boolean = missing(detail).isEmpty()

    /** Iranian national id checksum, used to warn before a typo is filed. */
    fun isValidNationalId(raw: String?): Boolean {
        val digits = raw?.filter { it.isDigit() } ?: return false
        if (digits.length != 10) return false
        if (digits.all { it == digits[0] }) return false
        val check = digits[9] - '0'
        val sum = (0..8).sumOf { (digits[it] - '0') * (10 - it) }
        val remainder = sum % 11
        return if (remainder < 2) check == remainder else check == 11 - remainder
    }
}
