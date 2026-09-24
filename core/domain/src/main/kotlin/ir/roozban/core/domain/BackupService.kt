package ir.roozban.core.domain

enum class RestoreMode {
    /** The backup replaces everything on the device. */
    REPLACE,

    /** Items from the backup are added; where both have an item, the newer edit wins. */
    MERGE,
}

sealed interface RestoreResult {
    data class Success(val tasks: Int, val projects: Int) : RestoreResult
    data object WrongPassword : RestoreResult
    data class Invalid(val reason: String) : RestoreResult
}

/** Encrypted, password-protected backups of all data and settings. */
interface BackupService {
    suspend fun createBackup(password: CharArray): ByteArray

    /** Restores and then re-plans every reminder. */
    suspend fun restore(file: ByteArray, password: CharArray, mode: RestoreMode): RestoreResult
}
