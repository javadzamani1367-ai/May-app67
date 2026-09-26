package ir.ilam.inspection.ui.theme

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Light, dark, or whatever the phone is set to. */
enum class ThemeMode(val code: Int) {
    LIGHT(0), DARK(1), SYSTEM(2);

    companion object {
        fun of(code: Int) = entries.firstOrNull { it.code == code } ?: LIGHT
    }
}

/**
 * The theme choice, kept outside the encrypted database on purpose.
 *
 * The very first frame has to be painted before anything else happens — the
 * database is opened later, off the main thread — and a preference about
 * colours tells nobody anything worth protecting. So it lives in plain
 * preferences and is readable instantly.
 *
 * Light is the default, not "follow the phone": the app is used outdoors in
 * daylight, where a dark screen is the one that cannot be read.
 */
object ThemePreference {

    private const val PREFS = "ui"
    private const val KEY = "theme_mode"

    var mode by mutableStateOf(ThemeMode.LIGHT)
        private set

    fun load(context: Context) {
        mode = ThemeMode.of(prefs(context).getInt(KEY, ThemeMode.LIGHT.code))
    }

    fun set(context: Context, value: ThemeMode) {
        mode = value
        prefs(context).edit().putInt(KEY, value.code).apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
