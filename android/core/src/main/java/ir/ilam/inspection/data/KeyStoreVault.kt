package ir.ilam.inspection.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Secrets that must never reach a backup or a log: the SQLCipher passphrase,
 * the app PIN and the sync pairing code. They live in an
 * EncryptedSharedPreferences file whose master key is held by the Android
 * KeyStore, so the passphrase never exists as plain text on disk.
 */
class KeyStoreVault(context: Context) {

    private val prefs: SharedPreferences = run {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    /**
     * The file name of the signed-in account's database. One person may hold an
     * account in several areas, signing in to one at a time on the same phone; each
     * account's cases live in their own file, so switching hides the others' work
     * without deleting it. The account that used this install before multi-account
     * support keeps the original file.
     */
    fun databaseName(base: String): String {
        val code = userCode()?.takeIf { it.isNotBlank() } ?: return "$base.db"
        val owner = prefs.getString(KEY_DB_OWNER, null) ?: code.also { prefs.edit().putString(KEY_DB_OWNER, it).apply() }
        if (owner == code) return "$base.db"
        return "$base-" + code.filter { it.isLetterOrDigit() || it == '-' }.lowercase() + ".db"
    }

    /**
     * Makes [code] the signed-in account, keeping the previous account's password
     * hash, activation and session aside so switching back later still works
     * offline. Returns the previous code when an account was replaced, or null.
     */
    fun switchAccount(code: String): String? {
        val previous = userCode()?.trim().orEmpty()
        val next = code.trim()
        if (previous == next || next.isEmpty()) return null
        val edit = prefs.edit()
        if (previous.isNotEmpty()) {
            for (key in PER_ACCOUNT) {
                // Booleans and ints too, stored as text: the stash is read back by key type.
                val value = prefs.all[key]?.toString()
                if (value != null) edit.putString(stash(previous, key), value) else edit.remove(stash(previous, key))
            }
        }
        for (key in PER_ACCOUNT) {
            edit.remove(key)
            val saved = prefs.getString(stash(next, key), null) ?: continue
            when (key) {
                KEY_ACTIVATED -> edit.putBoolean(key, saved.toBoolean())
                KEY_ROLE -> edit.putInt(key, saved.toIntOrNull() ?: -1)
                else -> edit.putString(key, saved)
            }
        }
        edit.putString(KEY_USER_CODE, next).apply()
        // A first sign-in on this install opens the same database file it already had.
        return previous.ifEmpty { null }
    }

    /**
     * Set when a sign-in switched accounts: the app restarts to open that account's
     * database, and the restarted app opens straight away instead of asking again
     * for the password typed a moment ago.
     */
    fun markFreshUnlock() {
        prefs.edit().putLong(KEY_FRESH_UNLOCK, System.currentTimeMillis() + FRESH_UNLOCK_MILLIS).apply()
    }

    fun consumeFreshUnlock(): Boolean {
        val until = prefs.getLong(KEY_FRESH_UNLOCK, 0)
        prefs.edit().remove(KEY_FRESH_UNLOCK).apply()
        return until > System.currentTimeMillis()
    }

    private fun stash(code: String, key: String) = "acct:$code:$key"

    /** Created once on first launch and reused for the life of the install. */
    fun databasePassphrase(): ByteArray {
        val stored = prefs.getString(KEY_DB_PASSPHRASE, null)
        if (stored != null) return stored.hexToBytes()
        val fresh = ByteArray(32).also { SecureRandom().nextBytes(it) }
        prefs.edit().putString(KEY_DB_PASSPHRASE, fresh.toHex()).apply()
        return fresh
    }

    /**
     * This installation's own code. Generated once from 64 random bits, so a
     * repeat is not something that happens in practice, and it lives with the
     * other secrets — uninstalling the app destroys it, which is exactly the
     * intent: a reinstall or a new phone is a new device and has to be
     * registered again.
     */
    fun deviceCode(): String {
        val stored = prefs.getString(KEY_DEVICE_CODE, null)
        if (stored != null) return stored
        val generated = ByteArray(8).also { SecureRandom().nextBytes(it) }.toHex().uppercase()
        prefs.edit().putString(KEY_DEVICE_CODE, generated).apply()
        return generated
    }

    /** The same code in groups of four, which is what a person reads aloud. */
    fun deviceCodeForDisplay(): String = deviceCode().chunked(4).joinToString("-")

    /** The user code this phone was activated with, shown on every report. */
    fun userCode(): String? = prefs.getString(KEY_USER_CODE, null)

    fun setUserCode(code: String) {
        prefs.edit().putString(KEY_USER_CODE, code.trim()).apply()
    }

    /**
     * Whether the server has ever confirmed this installation belongs to this
     * user. Field work happens with no signal, so the check runs once, at
     * activation; afterwards the phone unlocks on its own. Without it, an
     * unregistered install could be used all day simply by staying offline.
     */
    fun isActivated(): Boolean = prefs.getBoolean(KEY_ACTIVATED, false)

    fun markActivated(token: String) {
        prefs.edit().putBoolean(KEY_ACTIVATED, true).putString(KEY_SERVER_TOKEN, token).apply()
    }

    fun serverToken(): String? = prefs.getString(KEY_SERVER_TOKEN, null)

    /**
     * The role and areas the server gave this account at its last online
     * entry. The manager app serves the manager and the area executors alike,
     * and decides which screens to offer from these, offline too. The server
     * still checks every call: hiding a button protects nothing.
     */
    fun setAccount(role: Int, areas: List<String>) {
        prefs.edit().putInt(KEY_ROLE, role).putString(KEY_AREAS, areas.joinToString(",")).apply()
    }

    fun serverRole(): Int = prefs.getInt(KEY_ROLE, -1)

    fun areas(): List<String> = prefs.getString(KEY_AREAS, null).orEmpty().split(',').filter { it.isNotBlank() }

    /** Undone when the server rejects the pairing, so the next entry is online. */
    fun clearActivation() {
        prefs.edit().remove(KEY_ACTIVATED).remove(KEY_SERVER_TOKEN).apply()
    }

    fun hasPin(): Boolean = prefs.contains(KEY_PIN_HASH)

    fun setPin(pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        prefs.edit()
            .putString(KEY_PIN_SALT, salt.toHex())
            .putString(KEY_PIN_HASH, hashPin(pin, salt).toHex())
            .apply()
    }

    fun verifyPin(pin: String): Boolean {
        val salt = prefs.getString(KEY_PIN_SALT, null)?.hexToBytes() ?: return false
        val expected = prefs.getString(KEY_PIN_HASH, null)?.hexToBytes() ?: return false
        return MessageDigest.isEqual(hashPin(pin, salt), expected)
    }

    /** Passphrase for the offline `.cvz` package, derived from the PIN material. */
    fun packagePassword(): String {
        val stored = prefs.getString(KEY_PACKAGE_PASSWORD, null)
        if (stored != null) return stored
        val bytes = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val generated = bytes.toHex()
        prefs.edit().putString(KEY_PACKAGE_PASSWORD, generated).apply()
        return generated
    }

    private fun hashPin(pin: String, salt: ByteArray): ByteArray {
        // 100k SHA-256 rounds: cheap enough for a six digit PIN on a phone,
        // expensive enough that a stolen file cannot be brute forced quickly.
        val digest = MessageDigest.getInstance("SHA-256")
        var value = salt + pin.toByteArray(Charsets.UTF_8)
        repeat(100_000) {
            value = digest.digest(value)
        }
        return value
    }

    private fun ByteArray.toHex(): String =
        joinToString("") { "%02x".format(it) }

    private fun String.hexToBytes(): ByteArray =
        chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    private companion object {
        const val FILE_NAME = "inspection_vault"
        const val KEY_DB_PASSPHRASE = "db_passphrase"
        const val KEY_PIN_HASH = "pin_hash"
        const val KEY_PIN_SALT = "pin_salt"
        const val KEY_PACKAGE_PASSWORD = "package_password"
        const val KEY_DEVICE_CODE = "device_code"
        const val KEY_USER_CODE = "user_code"
        const val KEY_ACTIVATED = "activated"
        const val KEY_SERVER_TOKEN = "server_token"
        const val KEY_ROLE = "server_role"
        const val KEY_AREAS = "server_areas"
        const val KEY_DB_OWNER = "db_owner"
        const val KEY_FRESH_UNLOCK = "fresh_unlock_until"
        const val FRESH_UNLOCK_MILLIS = 60_000L
        /** What belongs to one account and travels with it on a switch. */
        val PER_ACCOUNT = listOf(KEY_PIN_HASH, KEY_PIN_SALT, KEY_ACTIVATED, KEY_SERVER_TOKEN, KEY_ROLE, KEY_AREAS)
    }
}
