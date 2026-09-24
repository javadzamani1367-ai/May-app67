package ir.roozban.core.backup

import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class WrongPasswordException : Exception("Wrong password or corrupted backup")

class InvalidBackupException(message: String) : Exception(message)

/**
 * Password-based encryption for backup files.
 *
 * Layout (big-endian):
 * ```
 * "RZBK" | version:1 | kdf:1 (1 = Argon2id) | memoryKiB:4 | iterations:4 | parallelism:1 | salt:16 | nonce:12 | ciphertext+tag
 * ```
 * The key is derived with Argon2id; the payload is sealed with AES-256-GCM and the whole header is
 * authenticated as associated data, so tampering with parameters is detected.
 */
class BackupCrypto(
    private val params: KdfParams = KdfParams.DEFAULT,
    private val random: SecureRandom = SecureRandom(),
) {
    data class KdfParams(val memoryKiB: Int, val iterations: Int, val parallelism: Int) {
        companion object {
            /** OWASP 2023 minimum for Argon2id; takes well under a second on mid-range phones. */
            val DEFAULT = KdfParams(memoryKiB = 19_456, iterations = 2, parallelism = 1)

            /** For tests only. */
            val FAST = KdfParams(memoryKiB = 1_024, iterations = 1, parallelism = 1)
        }
    }

    fun encrypt(plain: ByteArray, password: CharArray): ByteArray {
        val salt = ByteArray(SALT_LEN).also(random::nextBytes)
        val nonce = ByteArray(NONCE_LEN).also(random::nextBytes)
        val header = ByteBuffer.allocate(HEADER_LEN)
            .put(MAGIC)
            .put(VERSION)
            .put(KDF_ARGON2ID)
            .putInt(params.memoryKiB)
            .putInt(params.iterations)
            .put(params.parallelism.toByte())
            .put(salt)
            .put(nonce)
            .array()
        val key = deriveKey(password, salt, params)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
        cipher.updateAAD(header)
        val sealed = cipher.doFinal(plain)
        key.fill(0)
        return header + sealed
    }

    fun decrypt(data: ByteArray, password: CharArray): ByteArray {
        if (data.size < HEADER_LEN + TAG_BITS / 8) throw InvalidBackupException("File is too short")
        val buf = ByteBuffer.wrap(data)
        val magic = ByteArray(4).also { buf.get(it) }
        if (!magic.contentEquals(MAGIC)) throw InvalidBackupException("Not a Roozban backup")
        val version = buf.get()
        if (version != VERSION) throw InvalidBackupException("Unsupported backup version $version")
        if (buf.get() != KDF_ARGON2ID) throw InvalidBackupException("Unsupported key derivation")
        val memory = buf.int
        val iterations = buf.int
        val parallelism = buf.get().toInt()
        // Bounds protect against a crafted header asking for gigabytes of memory.
        if (memory !in 1_024..262_144 || iterations !in 1..10 || parallelism !in 1..4) {
            throw InvalidBackupException("Unreasonable key derivation parameters")
        }
        val salt = ByteArray(SALT_LEN).also { buf.get(it) }
        val nonce = ByteArray(NONCE_LEN).also { buf.get(it) }
        val key = deriveKey(password, salt, KdfParams(memory, iterations, parallelism))
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
            cipher.updateAAD(data, 0, HEADER_LEN)
            cipher.doFinal(data, HEADER_LEN, data.size - HEADER_LEN)
        } catch (e: AEADBadTagException) {
            throw WrongPasswordException()
        } catch (e: GeneralSecurityException) {
            throw WrongPasswordException()
        } finally {
            key.fill(0)
        }
    }

    private fun deriveKey(password: CharArray, salt: ByteArray, p: KdfParams): ByteArray {
        val generator = Argon2BytesGenerator()
        generator.init(
            Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                .withVersion(Argon2Parameters.ARGON2_VERSION_13)
                .withSalt(salt)
                .withMemoryAsKB(p.memoryKiB)
                .withIterations(p.iterations)
                .withParallelism(p.parallelism)
                .build(),
        )
        val key = ByteArray(KEY_LEN)
        generator.generateBytes(password, key)
        return key
    }

    companion object {
        private val MAGIC = "RZBK".toByteArray(Charsets.US_ASCII)
        private const val VERSION: Byte = 1
        private const val KDF_ARGON2ID: Byte = 1
        private const val SALT_LEN = 16
        private const val NONCE_LEN = 12
        private const val KEY_LEN = 32
        private const val TAG_BITS = 128
        private const val HEADER_LEN = 4 + 1 + 1 + 4 + 4 + 1 + SALT_LEN + NONCE_LEN
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
