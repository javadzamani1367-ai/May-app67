package ir.ilam.inspection.data.model

import ir.ilam.inspection.BuildConfig

/**
 * Who is holding the phone. This is decided by which APK was installed, not by
 * a setting: an expert must not be able to hand themselves the manager's
 * screens by tapping a dropdown, and the two apps are built from this one
 * codebase as the `expert` and `manager` flavours.
 */
enum class UserRole {
    EXPERT,
    MANAGER;

    companion object {
        val current: UserRole = if (BuildConfig.MANAGER) MANAGER else EXPERT

        val isManager: Boolean get() = current == MANAGER
    }
}
