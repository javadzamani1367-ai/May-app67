package ir.ilam.inspection.data.model

import ir.ilam.inspection.BuildConfig

/**
 * Who is holding the phone. The app is decided by which APK was installed,
 * not by a setting: an expert must not be able to hand themselves the
 * manager's screens by tapping a dropdown. Three apps come from this one
 * codebase as the `expert`, `manager` and `illegal` flavours.
 *
 * The manager app serves two server roles: the manager, and the crypto
 * executor of one or more areas, who has the manager's powers there but makes
 * no accounts. Which of the two signed in is [serverRole], stored at the last
 * online entry. The server applies the area to every call; the app only uses
 * it to leave out screens that would answer "not for your role".
 */
enum class UserRole {
    EXPERT,
    MANAGER,
    ILLEGAL;

    companion object {
        const val ROLE_MANAGER = 1
        const val ROLE_EXECUTOR = 4

        val current: UserRole = when {
            BuildConfig.ILLEGAL -> ILLEGAL
            BuildConfig.MANAGER -> MANAGER
            else -> EXPERT
        }

        /** The manager app, whoever of the manager or an executor is using it. */
        val isManager: Boolean get() = current == MANAGER

        val isIllegal: Boolean get() = current == ILLEGAL

        /** The server role of the signed-in account; -1 before the first online entry. */
        @Volatile
        var serverRole: Int = -1

        /** Area codes of the signed-in account, for titles; the server does the filtering. */
        @Volatile
        var areas: List<String> = emptyList()

        /** An area executor in the manager app: everything a manager does, in their areas only. */
        val isExecutor: Boolean get() = isManager && serverRole == ROLE_EXECUTOR

        /** The manager proper: accounts, the device registry and overtime approval are theirs alone. */
        val isFullManager: Boolean get() = isManager && serverRole != ROLE_EXECUTOR

        /** Sent with the login so the server refuses an account meant for another app. */
        val appName: String
            get() = when (current) {
                EXPERT -> "expert"
                MANAGER -> "manager"
                ILLEGAL -> "illegal"
            }
    }
}
