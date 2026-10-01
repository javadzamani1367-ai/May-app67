package ir.ilam.inspection.field.data

import android.content.Context
import android.content.SharedPreferences
import ir.ilam.inspection.field.BuildConfig

/**
 * What this phone knows about its account and the manager's settings, as the
 * server last said. Nothing here is secret — the token and the password hash
 * live in the KeyStore vault — so plain preferences are enough, and reading
 * them never waits on the encrypted database.
 */
class FieldPrefs(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("field", Context.MODE_PRIVATE)

    var serverAddress: String
        get() = prefs.getString(KEY_SERVER, null) ?: BuildConfig.DEFAULT_SERVER
        set(value) = prefs.edit().putString(KEY_SERVER, value.trim()).apply()

    var fullName: String
        get() = prefs.getString(KEY_NAME, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_NAME, value).apply()

    /** The bits from the last successful sign-in or sync; nothing until then. */
    var permissions: Int
        get() = prefs.getInt(KEY_PERMISSIONS, 0)
        set(value) = prefs.edit().putInt(KEY_PERMISSIONS, value).apply()

    var ampTolerancePct: Int
        get() = prefs.getInt(KEY_AMP_PCT, 10)
        set(value) = prefs.edit().putInt(KEY_AMP_PCT, value).apply()

    var ampToleranceMinA: Int
        get() = prefs.getInt(KEY_AMP_MIN, 5)
        set(value) = prefs.edit().putInt(KEY_AMP_MIN, value).apply()

    var purgeAfterSync: Boolean
        get() = prefs.getBoolean(KEY_PURGE, false)
        set(value) = prefs.edit().putBoolean(KEY_PURGE, value).apply()

    /**
     * The server stopped accepting this phone's session. Work goes on offline;
     * only sending waits, and the home screen asks for a sign-in.
     */
    var sessionExpired: Boolean
        get() = prefs.getBoolean(KEY_EXPIRED, false)
        set(value) = prefs.edit().putBoolean(KEY_EXPIRED, value).apply()

    /** Server time of the last status pull, so the next one asks only for what changed. */
    var lastPull: Long
        get() = prefs.getLong(KEY_LAST_PULL, 0)
        set(value) = prefs.edit().putLong(KEY_LAST_PULL, value).apply()

    var lastSyncAt: Long
        get() = prefs.getLong(KEY_LAST_SYNC, 0)
        set(value) = prefs.edit().putLong(KEY_LAST_SYNC, value).apply()

    private companion object {
        const val KEY_SERVER = "server_address"
        const val KEY_NAME = "full_name"
        const val KEY_PERMISSIONS = "permissions"
        const val KEY_AMP_PCT = "amp_tolerance_pct"
        const val KEY_AMP_MIN = "amp_tolerance_min_a"
        const val KEY_PURGE = "purge_after_sync"
        const val KEY_EXPIRED = "session_expired"
        const val KEY_LAST_PULL = "last_pull"
        const val KEY_LAST_SYNC = "last_sync_at"
    }
}
