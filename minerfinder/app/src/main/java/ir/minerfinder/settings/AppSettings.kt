package ir.minerfinder.settings

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Everything the user can set. Weights and switches are keyed by source id, so a new source needs no new field. */
data class Settings(
    val alertThreshold: Float = 60f,
    val beep: Boolean = true,
    val vibrate: Boolean = true,
    /** 0 = the phone's default input. */
    val audioInputId: Int = 0,
    val coilInputId: Int = 0,
    val stabilityWindowS: Int = 20,
    val peakThresholdDb: Float = 12f,
    val logIntervalS: Int = 3,
    val weights: Map<String, Float> = emptyMap(),
    /** The coil starts switched off: it needs hardware most users do not have. */
    val disabled: Set<String> = setOf("coil"),
) {
    fun weight(id: String, default: Float): Float = weights[id] ?: default
    fun isEnabled(id: String): Boolean = id !in disabled
}

class AppSettings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(load())
    val state: StateFlow<Settings> = _state.asStateFlow()
    val current: Settings get() = _state.value

    fun update(change: (Settings) -> Settings) {
        _state.update(change)
        save(_state.value)
    }

    private fun load(): Settings {
        val weights = prefs.all.filterKeys { it.startsWith(WEIGHT) }
            .mapNotNull { (k, v) -> (v as? Float)?.let { k.removePrefix(WEIGHT) to it } }.toMap()
        return Settings(
            alertThreshold = prefs.getFloat("alert_threshold", 60f),
            beep = prefs.getBoolean("beep", true),
            vibrate = prefs.getBoolean("vibrate", true),
            audioInputId = prefs.getInt("audio_input", 0),
            coilInputId = prefs.getInt("coil_input", 0),
            stabilityWindowS = prefs.getInt("stability_window", 20),
            peakThresholdDb = prefs.getFloat("peak_threshold", 12f),
            logIntervalS = prefs.getInt("log_interval", 3),
            weights = weights,
            disabled = prefs.getStringSet("disabled", setOf("coil"))!!.toSet(),
        )
    }

    private fun save(s: Settings) {
        val edit = prefs.edit()
            .putFloat("alert_threshold", s.alertThreshold)
            .putBoolean("beep", s.beep)
            .putBoolean("vibrate", s.vibrate)
            .putInt("audio_input", s.audioInputId)
            .putInt("coil_input", s.coilInputId)
            .putInt("stability_window", s.stabilityWindowS)
            .putFloat("peak_threshold", s.peakThresholdDb)
            .putInt("log_interval", s.logIntervalS)
            .putStringSet("disabled", s.disabled)
        for ((id, w) in s.weights) edit.putFloat(WEIGHT + id, w)
        edit.apply()
    }

    /** Calibration data is stored as plain numbers, separate from the settings snapshot. */
    fun putVector(key: String, values: DoubleArray?) {
        prefs.edit().apply {
            if (values == null) remove(VECTOR + key) else putString(VECTOR + key, values.joinToString(","))
        }.apply()
    }

    fun getVector(key: String): DoubleArray? =
        prefs.getString(VECTOR + key, null)?.split(',')?.mapNotNull { it.toDoubleOrNull() }?.toDoubleArray()

    private companion object {
        const val WEIGHT = "weight."
        const val VECTOR = "vector."
    }
}
