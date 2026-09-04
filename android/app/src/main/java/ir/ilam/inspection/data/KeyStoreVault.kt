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

    /** Created once on first launch and reused for the life of the install. */
    fun databasePassphrase(): ByteArray {
        val stored = prefs.getString(KEY_DB_PASSPHRASE, null)
        if (stored != null) return stored.hexToBytes()
        val fresh = ByteArray(32).also { SecureRandom().nextBytes(it) }
        prefs.edit().putString(KEY_DB_PASSPHRASE, fresh.toHex()).apply()
        return fresh
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
    }
}
