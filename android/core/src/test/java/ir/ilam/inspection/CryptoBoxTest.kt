package ir.ilam.inspection

import ir.ilam.inspection.util.CryptoBox
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.security.SecureRandom

/**
 * The `.cvz` package is how a case leaves a phone when there is no connection
 * at all, and it carries owner names, national ids and the names of security
 * personnel. Nothing else in the project has tested that the bytes can come
 * back — or that they cannot come back to the wrong hands.
 */
class CryptoBoxTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val password = "a-field-package-password"

    @Test
    fun `a package survives the round trip`() {
        // Larger than one cipher block and not a multiple of it, so a padding
        // mistake shows up instead of being hidden by a tidy size.
        val original = write("data.zip", ByteArray(70_001).also { SecureRandom().nextBytes(it) })
        val sealed = folder.newFile("package.cvz")
        val opened = folder.newFile("opened.zip")

        CryptoBox.encrypt(original, sealed, password)
        CryptoBox.decrypt(sealed, opened, password)

        assertArrayEquals(original.readBytes(), opened.readBytes())
    }

    @Test
    fun `the sealed file is not the plain file`() {
        val original = write("data.zip", "شماره ملی و نام مالک".toByteArray())
        val sealed = folder.newFile("package.cvz")

        CryptoBox.encrypt(original, sealed, password)

        assertFalse(sealed.readBytes().contentEquals(original.readBytes()))
        // The header is readable on purpose — it is how a reader knows what
        // the file is — but nothing after it may be.
        assertEquals("CVZ1", String(sealed.readBytes().take(4).toByteArray()))
        assertFalse(String(sealed.readBytes(), Charsets.UTF_8).contains("مالک"))
    }

    @Test
    fun `the wrong password does not open it`() {
        val original = write("data.zip", "محتوای پرونده".toByteArray())
        val sealed = folder.newFile("package.cvz")
        CryptoBox.encrypt(original, sealed, password)

        assertTrue(fails { CryptoBox.decrypt(sealed, folder.newFile("wrong.zip"), "$password-x") })
    }

    @Test
    fun `a tampered package is refused rather than half read`() {
        val original = write("data.zip", ByteArray(4_096).also { SecureRandom().nextBytes(it) })
        val sealed = folder.newFile("package.cvz")
        CryptoBox.encrypt(original, sealed, password)

        // Flip one byte of the ciphertext. GCM authenticates, so this must be
        // caught: a silently truncated case file is worse than none.
        val bytes = sealed.readBytes()
        bytes[bytes.size - 1] = (bytes[bytes.size - 1] + 1).toByte()
        sealed.writeBytes(bytes)

        assertTrue(fails { CryptoBox.decrypt(sealed, folder.newFile("tampered.zip"), password) })
    }

    @Test
    fun `something that is not a package is refused at once`() {
        val notAPackage = write("random.bin", ByteArray(64).also { SecureRandom().nextBytes(it) })

        assertTrue(fails { CryptoBox.decrypt(notAPackage, folder.newFile("out.zip"), password) })
    }

    @Test
    fun `two packages of the same bytes are not identical`() {
        val original = write("data.zip", "همان محتوا".toByteArray())
        val first = folder.newFile("first.cvz")
        val second = folder.newFile("second.cvz")

        CryptoBox.encrypt(original, first, password)
        CryptoBox.encrypt(original, second, password)

        // A fresh salt and iv each time, so identical cases do not produce
        // identical files that could be compared against each other.
        assertNotEquals(first.readBytes().toList(), second.readBytes().toList())
    }

    private fun write(name: String, bytes: ByteArray): File =
        folder.newFile(name).apply { writeBytes(bytes) }

    private inline fun fails(block: () -> Unit): Boolean = try {
        block()
        false
    } catch (expected: Exception) {
        true
    }
}
