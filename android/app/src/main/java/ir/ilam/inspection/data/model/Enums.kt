package ir.ilam.inspection.data.model

/**
 * Every enum here is persisted as the integer `code`. The codes are part of the
 * shared schema with the Windows archive — never renumber them.
 */

enum class ReportType(val code: Int, val letter: String?) {
    SORAGH(1, null),          // tracking code comes from the external system
    SYSTEM_121(2, "N"),
    PUBLIC(3, "M"),
    COLLEAGUE(4, "H"),
    TAVANIR(5, "T"),
    FIELD(6, "F");

    /** Types whose tracking code the app generates itself. */
    val generatesCode: Boolean get() = letter != null

    companion object {
        fun of(code: Int): ReportType = entries.firstOrNull { it.code == code } ?: PUBLIC
    }
}

enum class ReportStatus(val code: Int) {
    PENDING(0), VISITED(1), ARCHIVED(2);

    companion object {
        fun of(code: Int): ReportStatus = entries.firstOrNull { it.code == code } ?: PENDING
    }
}

enum class MediaType(val code: Int) {
    IMAGE(0), VIDEO(1);

    companion object {
        fun of(code: Int): MediaType = if (code == VIDEO.code) VIDEO else IMAGE
    }
}

enum class EntryMethod(val code: Int) {
    BARCODE(0), MANUAL(1);

    companion object {
        fun of(code: Int): EntryMethod = if (code == BARCODE.code) BARCODE else MANUAL
    }
}

enum class AttendeeOrg(val code: Int) {
    POWER_COMPANY(0), SECURITY_POLICE(1), OTHER(2);

    companion object {
        fun of(code: Int): AttendeeOrg = entries.firstOrNull { it.code == code } ?: OTHER
    }
}

enum class AttachmentCategory(val code: Int) {
    MINER_LOGS(0),
    MINER_ANALYSIS(1),
    COMMISSION_MINUTES(2),
    ENERGY_BILL(3),
    DAMAGE_BILL(4),
    LETTER_SALES(5),
    LETTER_LEGAL(6),
    LETTER_COUNTY_POWER(7),
    OTHER(8);

    companion object {
        fun of(code: Int): AttachmentCategory = entries.firstOrNull { it.code == code } ?: OTHER
    }
}

enum class DispatchUnit(val code: Int) {
    SALES(0), SECURITY(1), LEGAL(2), COUNTY_POWER(3);

    companion object {
        fun of(code: Int): DispatchUnit = entries.firstOrNull { it.code == code } ?: SALES
    }
}

enum class OutputFormat(val code: Int) {
    PDF(0), WORD(1);

    companion object {
        fun of(code: Int): OutputFormat = if (code == WORD.code) WORD else PDF
    }
}
