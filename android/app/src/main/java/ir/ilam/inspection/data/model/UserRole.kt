package ir.ilam.inspection.data.model

import ir.ilam.inspection.BuildConfig

/**
 * Who is holding the phone. The app is decided by which APK was installed,
 * not by a setting: an expert must not be able to hand themselves the
 * manager's screens by tapping a dropdown. Four apps come from this one
 * codebase as the `expert`, `manager`, `executor` and `illegal` flavours.
 *
 * The manager, executor and illegal-power apps share the supervisory screens
 * (desk, approvals, dispatch, scores); the server narrows each to its own
 * domain and areas. The manager is one account for the whole system; a crypto
 * executor has one account per area; the illegal-power app serves both
 * headquarters and each county's executor, told apart by [serverRole].
 */
enum class UserRole {
    EXPERT,
    MANAGER,
    EXECUTOR,
    ILLEGAL;

    companion object {
        const val ROLE_MANAGER = 1
        const val ROLE_EXECUTOR = 4
        const val ROLE_ILLEGAL_HQ = 5
        const val ROLE_ILLEGAL_AREA = 6

        val current: UserRole = when {
            BuildConfig.ILLEGAL -> ILLEGAL
            BuildConfig.EXECUTOR -> EXECUTOR
            BuildConfig.MANAGER -> MANAGER
            else -> EXPERT
        }

        /** One of the supervisory apps: manager, executor or illegal-power. */
        val isManager: Boolean get() = current != EXPERT

        val isIllegal: Boolean get() = current == ILLEGAL

        /** The server role of the signed-in account; -1 before the first online entry. */
        @Volatile
        var serverRole: Int = -1

        /** Area codes of the signed-in account, for titles; the server does the filtering. */
        @Volatile
        var areas: List<String> = emptyList()

        /** Works inside a domain and areas: makes no accounts, no device registry, no overtime approval. */
        val isExecutor: Boolean get() = current == EXECUTOR || current == ILLEGAL

        /** The manager proper: accounts, the device registry and overtime approval are theirs alone. */
        val isFullManager: Boolean get() = current == MANAGER

        /** Illegal-power headquarters: the whole province, like the manager is for crypto. */
        val isIllegalHq: Boolean get() = isIllegal && serverRole == ROLE_ILLEGAL_HQ

        /**
         * Whether this app acts on a case of [caseKind] (0 crypto, 1 illegal power):
         * the manager only supervises illegal-power cases, and the server refuses too.
         */
        fun actsOn(caseKind: Int): Boolean = !(isFullManager && caseKind == 1)

        /** Sent with the login so the server refuses an account meant for another app. */
        val appName: String
            get() = when (current) {
                EXPERT -> "expert"
                MANAGER -> "manager"
                EXECUTOR -> "executor"
                ILLEGAL -> "illegal"
            }
    }
}
