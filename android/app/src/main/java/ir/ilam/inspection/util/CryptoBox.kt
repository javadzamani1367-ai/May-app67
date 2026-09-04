package ir.ilam.inspection.util

import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Password protection for the offline `.cvz` package. Zip entry encryption is
 * not available in the platform zip writer, so the whole archive is wrapped in
 * AES-256-GCM with a PBKDF2 derived key. Layout:
 *
 * `CVZ1 | salt(16) | iv(12) | ciphertext`
 */
object CryptoBox {

    private const val MAGIC = "CVZ1"
    private const val SALT_SIZE = 16
    private const val IV_SIZE = 12
    private const val TAG_BITS = 128
    private const val ITERATIONS = 120_000

    fun encrypt(plain: File, encrypted: File, password: String) {
        val salt = ByteArray(SALT_SIZE).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(IV_SIZE).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, deriveKey(password, salt), GCMParameterSpec(TAG_BITS, iv))
        }
        encrypted.outputStream().use { raw ->
            raw.write(MAGIC.toByteArray(Charsets.US_ASCII))
            raw.write(salt)
            raw.write(iv)
            CipherOutputStream(raw, cipher).use { out ->
                plain.inputStream().use { it.copyTo(out) }
            }
        }
    }

    fun decrypt(encrypted: File, plain: File, password: String) {
        encrypted.inputStream().use { raw ->
            val magic = ByteArray(MAGIC.length).also { raw.readFully(it) }
            require(String(magic, Charsets.US_ASCII) == MAGIC) { "not a cvz package" }
            val salt = ByteArray(SALT_SIZE).also { raw.readFully(it) }
            val iv = ByteArray(IV_SIZE).also { raw.readFully(it) }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.DECRYPT_MODE, deriveKey(password, salt), GCMParameterSpec(TAG_BITS, iv))
            }
            plain.outputStream().use { out: OutputStream ->
                javax.crypto.CipherInputStream(raw, cipher).use { it.copyTo(out) }
            }
        }
    }

    private fun deriveKey(password: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, 256)
        val key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec)
        return SecretKeySpec(key.encoded, "AES")
    }

    private fun InputStream.readFully(target: ByteArray) {
        var offset = 0
        while (offset < target.size) {
            val read = read(target, offset, target.size - offset)
            require(read > 0) { "unexpected end of package" }
            offset += read
        }
    }
}
