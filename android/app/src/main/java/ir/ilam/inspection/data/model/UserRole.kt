package ir.ilam.inspection.data.model

/**
 * Who is holding the phone. An expert works their own cases; a manager also
 * assembles the full document set for a case, so the dispatch screen offers
 * them every document category rather than only what is already attached.
 */
enum class UserRole(val code: String) {
    EXPERT("expert"),
    MANAGER("manager");

    companion object {
        fun of(value: String?): UserRole = entries.firstOrNull { it.code == value } ?: EXPERT
    }
}
