package ir.roozban.core.backup

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import java.util.zip.ZipException

/** Serializes backups: JSON → gzip → [BackupCrypto]. */
class BackupCodec(private val crypto: BackupCrypto = BackupCrypto()) {

    private val json = Json {
        ignoreUnknownKeys = true // newer backups can add fields
        encodeDefaults = false
    }

    fun encode(data: BackupData, password: CharArray): ByteArray {
        val bytes = ByteArrayOutputStream()
        GZIPOutputStream(bytes).use { it.write(json.encodeToString(BackupData.serializer(), data).toByteArray(Charsets.UTF_8)) }
        return crypto.encrypt(bytes.toByteArray(), password)
    }

    fun decode(file: ByteArray, password: CharArray): BackupData {
        val plain = crypto.decrypt(file, password)
        val text = try {
            GZIPInputStream(ByteArrayInputStream(plain)).use { it.readBytes().toString(Charsets.UTF_8) }
        } catch (e: ZipException) {
            throw InvalidBackupException("Corrupted backup content")
        }
        val data = try {
            json.decodeFromString(BackupData.serializer(), text)
        } catch (e: SerializationException) {
            throw InvalidBackupException("Unreadable backup content")
        } catch (e: IllegalArgumentException) {
            throw InvalidBackupException("Unreadable backup content")
        }
        if (data.formatVersion > BackupData.FORMAT_VERSION) {
            throw InvalidBackupException("Backup was made by a newer version of Roozban")
        }
        return data
    }
}

/**
 * Merges a backup into local data: for every entity present in both, the most recently updated
 * version wins; entities present in only one side are kept. Completion records are unioned.
 */
object BackupMerger {
    fun merge(local: BackupData, incoming: BackupData): BackupData {
        fun <T> newest(a: List<T>, b: List<T>, id: (T) -> String, updated: (T) -> Long): List<T> =
            (a + b).groupBy(id).values.map { versions -> versions.maxBy(updated) }

        val tasks = newest(local.tasks, incoming.tasks, { it.id }, { it.updatedAt })
        val taskIds = tasks.map { it.id }.toSet()
        val habits = newest(local.habits, incoming.habits, { it.id }, { it.updatedAt })
        val habitIds = habits.map { it.id }.toSet()
        return local.copy(
            tasks = tasks,
            projects = newest(local.projects, incoming.projects, { it.id }, { it.updatedAt }),
            labels = newest(local.labels, incoming.labels, { it.id }, { it.updatedAt }),
            completions = (local.completions + incoming.completions)
                .filter { it.taskId in taskIds }
                .distinctBy { it.taskId to it.occurrence },
            settings = local.settings ?: incoming.settings,
            focusSessions = (local.focusSessions + incoming.focusSessions).distinctBy { it.id },
            timeEntries = (local.timeEntries + incoming.timeEntries).distinctBy { it.id },
            habits = habits,
            habitLogs = (local.habitLogs + incoming.habitLogs)
                .filter { it.habitId in habitIds }
                .groupBy { it.habitId to it.date }.values.map { versions -> versions.maxBy { it.updatedAt } },
        )
    }
}
