package ir.roozban.core.backup

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class BackupTest {
    private val codec = BackupCodec(BackupCrypto(BackupCrypto.KdfParams.FAST))
    private val password = "رمز امن ۱۲۳".toCharArray()

    private val data = BackupData(
        createdAt = 1_000,
        appVersion = "0.2.0",
        tasks = listOf(
            BackupTask(id = "t1", title = "خرید نان", dueDate = 20_720, dueMinute = 17 * 60, labelIds = listOf("l1"), projectId = "p1", createdAt = 1, updatedAt = 2),
            BackupTask(id = "t2", title = "زیرکار", parentId = "t1", createdAt = 1, updatedAt = 1),
        ),
        projects = listOf(BackupProject("p1", "خانه", color = 3, createdAt = 1, updatedAt = 1)),
        labels = listOf(BackupLabel("l1", "خرید", createdAt = 1, updatedAt = 1)),
        completions = listOf(BackupCompletion("t1", 20_719, 5)),
        settings = BackupSettings(9, 12, 15, 17, 20, allDayReminderMinute = 540, defaultReminderKind = "ALARM"),
    )

    @Test
    fun `round trip`() {
        val file = codec.encode(data, password)
        assertThat(codec.decode(file, password)).isEqualTo(data)
    }

    @Test
    fun `content is not readable without the password`() {
        val file = codec.encode(data, password)
        assertThat(String(file, Charsets.UTF_8)).doesNotContain("خرید")
        assertThrows<WrongPasswordException> { codec.decode(file, "wrong".toCharArray()) }
    }

    @Test
    fun `each backup uses a fresh salt and nonce`() {
        assertThat(codec.encode(data, password).contentEquals(codec.encode(data, password))).isFalse()
    }

    @Test
    fun `tampering is detected`() {
        val file = codec.encode(data, password)
        // Flip a bit in the ciphertext and, separately, in the authenticated header.
        assertThrows<WrongPasswordException> { codec.decode(file.copyOf().also { it[it.size - 5] = (it[it.size - 5] + 1).toByte() }, password) }
        assertThrows<WrongPasswordException> { codec.decode(file.copyOf().also { it[20] = (it[20] + 1).toByte() }, password) }
    }

    @Test
    fun `not a backup`() {
        assertThrows<InvalidBackupException> { codec.decode("hello world, this is not a backup file at all".toByteArray(), password) }
        assertThrows<InvalidBackupException> { codec.decode(ByteArray(3), password) }
    }

    @Test
    fun `absurd KDF parameters are rejected before deriving`() {
        val file = codec.encode(data, password)
        // memoryKiB lives at offset 6..9; set it to ~2 GiB.
        file[6] = 0x7F
        assertThrows<InvalidBackupException> { codec.decode(file, password) }
    }

    @Test
    fun `merge keeps the newest version of each entity`() {
        val local = data
        val incoming = BackupData(
            createdAt = 2_000,
            tasks = listOf(
                data.tasks[0].copy(title = "خرید نان سنگک", updatedAt = 10), // newer → wins
                data.tasks[1].copy(title = "قدیمی", updatedAt = 0), // older → loses
                BackupTask(id = "t3", title = "فقط در پشتیبان", createdAt = 3, updatedAt = 3),
            ),
            completions = listOf(BackupCompletion("t1", 20_719, 5), BackupCompletion("t3", 20_700, 6)),
        )
        val merged = BackupMerger.merge(local, incoming)
        assertThat(merged.tasks.associate { it.id to it.title })
            .containsExactly("t1", "خرید نان سنگک", "t2", "زیرکار", "t3", "فقط در پشتیبان")
        assertThat(merged.completions).hasSize(2)
        assertThat(merged.projects).isEqualTo(data.projects)
        assertThat(merged.settings).isEqualTo(data.settings)
    }
}
