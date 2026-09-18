package ir.ilam.inspection.data.model

/**
 * The coded technical answers. Like every other enum in the shared schema the
 * integer `code` is what reaches the database and the Windows archive, so the
 * numbers are fixed for good — add new members at the end, never renumber.
 */

/** Where the consumption is taken from: ahead of the meter, or behind it. */
enum class TapPoint(val code: Int) {
    BEFORE_METER(0), AFTER_METER(1);

    companion object {
        fun of(code: Int?): TapPoint? = entries.firstOrNull { it.code == code }
    }
}

/** Single phase carries one measurement; three phase carries R, S and T. */
enum class PhaseType(val code: Int, val phases: Int) {
    SINGLE(0, 1), THREE(1, 3);

    companion object {
        fun of(code: Int?): PhaseType? = entries.firstOrNull { it.code == code }
    }
}

/** The tariff recorded at the site. */
enum class TariffType(val code: Int) {
    DOMESTIC(0),
    INDUSTRIAL(1),
    AGRICULTURAL(2),
    PUBLIC(3),
    OTHER_USE(4),
    UNMETERED_ILLEGAL(5);

    companion object {
        fun of(code: Int?): TariffType? = entries.firstOrNull { it.code == code }
    }
}

enum class MeterType(val code: Int) {
    MECHANICAL(0), DIGITAL(1);

    companion object {
        fun of(code: Int?): MeterType? = entries.firstOrNull { it.code == code }
    }
}

/**
 * A yes/no answer that may also be unanswered, which is not the same as "no":
 * an official report must not claim a question was answered when it was not.
 */
object YesNo {
    const val NO = 0
    const val YES = 1

    fun of(value: Int?): Boolean? = when (value) {
        YES -> true
        NO -> false
        else -> null
    }

    fun code(value: Boolean?): Int? = when (value) {
        true -> YES
        false -> NO
        null -> null
    }
}
