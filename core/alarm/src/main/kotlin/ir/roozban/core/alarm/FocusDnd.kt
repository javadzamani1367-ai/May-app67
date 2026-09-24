package ir.roozban.core.alarm

import android.app.AutomaticZenRule
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.service.notification.Condition
import android.service.notification.ZenPolicy
import androidx.annotation.RequiresApi
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Do Not Disturb while focusing.
 * - Android 10+: an app-owned [AutomaticZenRule] («تمرکز روزبان») switched on and off, so the user
 *   sees and can tune it in the system's Do Not Disturb settings. Alarms and media still play,
 *   starred contacts and repeat callers still ring.
 * - Android 8–9: the global filter goes to «alarms only» and is restored afterwards, but only if
 *   the user had not set Do Not Disturb themselves.
 * Needs notification policy access; without it silencing is skipped.
 */
@Singleton
class FocusDnd @Inject constructor(@ApplicationContext private val context: Context) {
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val prefs = context.getSharedPreferences("focus_dnd", Context.MODE_PRIVATE)

    fun hasAccess(): Boolean = manager.isNotificationPolicyAccessGranted

    fun accessSettingsIntent(): Intent =
        Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Returns false when silencing was wanted but is not possible. */
    fun set(on: Boolean): Boolean {
        val active = prefs.getBoolean(KEY_ACTIVE, false)
        if (on == active) return true
        if (!hasAccess()) {
            if (!on) prefs.edit().putBoolean(KEY_ACTIVE, false).apply()
            return !on
        }
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) setRule(on) else setLegacy(on)
            prefs.edit().putBoolean(KEY_ACTIVE, on).apply()
            true
        } catch (e: RuntimeException) {
            // SecurityException (access revoked) or a vendor restriction: focus goes on without it.
            false
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun setRule(on: Boolean) {
        val id = ruleId(create = on) ?: return
        val state = if (on) Condition.STATE_TRUE else Condition.STATE_FALSE
        manager.setAutomaticZenRuleState(id, Condition(CONDITION, context.getString(R.string.focus_dnd_summary), state))
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun ruleId(create: Boolean): String? {
        prefs.getString(KEY_RULE, null)?.let { id -> if (manager.getAutomaticZenRule(id) != null) return id }
        if (!create) return null
        val owner = context.packageManager.getLaunchIntentForPackage(context.packageName)?.component
        val policy = ZenPolicy.Builder()
            .disallowAllSounds()
            .allowAlarms(true)
            .allowMedia(true)
            .allowCalls(ZenPolicy.PEOPLE_TYPE_STARRED)
            .allowRepeatCallers(true)
            .build()
        val rule = AutomaticZenRule(
            context.getString(R.string.focus_dnd_rule),
            null,
            owner,
            CONDITION,
            policy,
            NotificationManager.INTERRUPTION_FILTER_PRIORITY,
            true,
        )
        return manager.addAutomaticZenRule(rule).also { prefs.edit().putString(KEY_RULE, it).apply() }
    }

    private fun setLegacy(on: Boolean) {
        if (on) {
            if (manager.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_ALL) {
                manager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALARMS)
                prefs.edit().putBoolean(KEY_LEGACY_SET, true).apply()
            }
        } else if (prefs.getBoolean(KEY_LEGACY_SET, false)) {
            // Only undo our own change.
            if (manager.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_ALARMS) {
                manager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
            }
            prefs.edit().putBoolean(KEY_LEGACY_SET, false).apply()
        }
    }

    private companion object {
        val CONDITION: Uri = Uri.parse("condition://ir.roozban/focus")
        const val KEY_RULE = "rule_id"
        const val KEY_ACTIVE = "active"
        const val KEY_LEGACY_SET = "legacy_set"
    }
}
