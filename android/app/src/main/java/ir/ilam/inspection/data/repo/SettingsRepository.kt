package ir.ilam.inspection.data.repo

import ir.ilam.inspection.data.db.SettingDao
import ir.ilam.inspection.data.db.SettingEntity
import ir.ilam.inspection.data.model.countyCodeSettingKey
import kotlinx.coroutines.flow.Flow
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
            countyCodeOverrides = map.filterKeys { it.startsWith(COUNTY_PREFIX) }
                .mapNotNull { (key, value) ->
                    key.removePrefix(COUNTY_PREFIX).toIntOrNull()?.let { it to value }
                }
                .toMap()
        )
    }

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

    suspend fun setCountyCode(index: Int, code: String) = put(countyCodeSettingKey(index), code)

    /** The area code to stamp into a tracking code for a given county. */
    suspend fun areaCodeFor(countyIndex: Int?, fallback: String): String {
        if (countyIndex == null) return defaultAreaCode()
        return dao.value(countyCodeSettingKey(countyIndex)) ?: fallback
    }

    companion object {
        const val KEY_EXPERT_CODE = "expert_code"
        const val KEY_EXPERT_NAME = "expert_name"
        const val KEY_DEFAULT_AREA = "default_area_code"
        const val KEY_SYNC_TARGET = "sync_target"
        const val KEY_MEDIA_QUALITY = "media_quality"
        const val COUNTY_PREFIX = "county_code_"
        const val DEFAULT_AREA = "01"
        const val DEFAULT_QUALITY = 85
    }
}

data class AppSettings(
    val expertCode: String = "",
    val expertName: String = "",
    val defaultAreaCode: String = SettingsRepository.DEFAULT_AREA,
    val syncTarget: String = "",
    val mediaQuality: Int = SettingsRepository.DEFAULT_QUALITY,
    val countyCodeOverrides: Map<Int, String> = emptyMap()
)
