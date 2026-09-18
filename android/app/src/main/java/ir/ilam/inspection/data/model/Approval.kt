package ir.ilam.inspection.data.model

/**
 * Where a case stands with the manager.
 *
 * A visit is not finished when the expert says it is. The documents go to the
 * manager, who either accepts them or sends them back with what is missing,
 * and only an accepted case can be filed for good. The codes are stored, so
 * they are fixed: add at the end, never renumber.
 */
enum class ApprovalState(val code: Int) {
    /** Never submitted. The expert is still working on it. */
    DRAFT(0),

    /** With the manager, waiting for a decision. */
    PENDING(1),

    /** Accepted. The case may now be archived. */
    APPROVED(2),

    /** Sent back for correction or completion, with a comment saying why. */
    RETURNED(3);

    /** Whether the expert may still edit. A case under review is frozen. */
    val editable: Boolean get() = this != PENDING && this != APPROVED

    companion object {
        fun of(code: Int?): ApprovalState = entries.firstOrNull { it.code == code } ?: DRAFT
    }
}

/** How a dispatch physically left the phone. */
enum class DispatchChannel(val code: Int) {
    SYSTEM(0), SOCIAL(1), OFFLINE_PACKAGE(2);

    companion object {
        fun of(code: Int?): DispatchChannel = entries.firstOrNull { it.code == code } ?: SYSTEM
    }
}

/** Where a dispatch has got to with the unit that received it. */
enum class DispatchStatus(val code: Int) {
    SENT(0), SEEN(1), ANSWERED(2);

    companion object {
        fun of(code: Int?): DispatchStatus = entries.firstOrNull { it.code == code } ?: SENT
    }

    /**
     * Overdue is worked out from the deadline and the answer, never stored:
     * a stored flag would need something to keep it up to date, and would be
     * wrong the moment nobody did.
     */
    fun isOverdue(deadlineAt: Long?, now: Long = System.currentTimeMillis()): Boolean =
        deadlineAt != null && this != ANSWERED && deadlineAt < now
}
