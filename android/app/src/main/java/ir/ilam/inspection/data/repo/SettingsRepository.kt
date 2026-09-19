package ir.ilam.inspection.data.repo

import ir.ilam.inspection.data.db.SettingDao
import ir.ilam.inspection.data.db.SettingEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Key-value settings: expert identity, default area code, sync target, media quality. */
class SettingsRepository(private val dao: SettingDao) {

    val settings: Flow<AppSettings> = dao.observeAll().map { rows ->
        val map = rows.associate { it.key to it.value }
        AppSettings(
            expertCode = map[KEY_EXPERT_CODE].orEmpty(),
            expertName = map[KEY_EXPERT_NAME].orEmpty(),
            defaultAreaCode = map[KEY_DEFAULT_AREA] ?: DEFAULT_AREA,
            syncTarget = map[KEY_SYNC_TARGET].orEmpty(),
            mediaQuality = map[KEY_MEDIA_QUALITY]?.toIntOrNull() ?: DEFAULT_QUALITY,
            autoSync = map[KEY_AUTO_SYNC] != FALSE
        )
    }

    /** A one-shot read, for the places that need the values once, not a stream. */
    suspend fun current(): AppSettings = settings.first()

    suspend fun put(key: String, value: String) = dao.put(SettingEntity(key, value))

    suspend fun expertCode(): String = dao.value(KEY_EXPERT_CODE).orEmpty()

    suspend fun defaultAreaCode(): String = dao.value(KEY_DEFAULT_AREA) ?: DEFAULT_AREA

    suspend fun mediaQuality(): Int =
        dao.value(KEY_MEDIA_QUALITY)?.toIntOrNull() ?: DEFAULT_QUALITY

    suspend fun setExpert(code: String, name: String) {
        put(KEY_EXPERT_CODE, code)
        put(KEY_EXPERT_NAME, name)
    }

    suspend fun setDefaultAreaCode(code: String) = put(KEY_DEFAULT_AREA, code)

    suspend fun setSyncTarget(target: String) = put(KEY_SYNC_TARGET, target)

    suspend fun setMediaQuality(quality: Int) = put(KEY_MEDIA_QUALITY, quality.toString())

    /**
     * How far this phone has pulled cases from the server, as the server's own
     * `updated_at`. Not the local clock: two phones' clocks disagree by
     * minutes and a case would be skipped for ever.
     */
    suspend fun pulledAt(): Long = dao.value(KEY_PULLED_AT)?.toLongOrNull() ?: 0L

    suspend fun setPulledAt(timestamp: Long) = put(KEY_PULLED_AT, timestamp.toString())

    /**
     * Background sync on Wi-Fi. On unless it was explicitly turned off, so a
     * phone that upgrades into this version starts sending without anyone
     * having to find the switch.
     */
    suspend fun autoSync(): Boolean = dao.value(KEY_AUTO_SYNC) != FALSE

    suspend fun setAutoSync(enabled: Boolean) = put(KEY_AUTO_SYNC, if (enabled) TRUE else FALSE)


    companion object {
        const val KEY_EXPERT_CODE = "expert_code"
        const val KEY_EXPERT_NAME = "expert_name"
        const val KEY_DEFAULT_AREA = "default_area_code"
        const val KEY_SYNC_TARGET = "sync_target"
        const val KEY_MEDIA_QUALITY = "media_quality"
        const val KEY_PULLED_AT = "server_pulled_at"
        const val KEY_AUTO_SYNC = "auto_sync"
        private const val TRUE = "1"
        private const val FALSE = "0"
        const val DEFAULT_AREA = "401"
        const val DEFAULT_QUALITY = 85
    }
}

data class AppSettings(
    val expertCode: String = "",
    val expertName: String = "",
    val defaultAreaCode: String = SettingsRepository.DEFAULT_AREA,
    val syncTarget: String = "",
    val mediaQuality: Int = SettingsRepository.DEFAULT_QUALITY,
    val autoSync: Boolean = true
)
