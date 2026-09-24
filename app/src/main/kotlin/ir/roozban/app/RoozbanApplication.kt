package ir.roozban.app

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class RoozbanApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // The UI is Persian-only: pin the app locale so system components (pickers, dialogs,
        // accessibility services) are Persian and RTL too, whatever the device language.
        if (AppCompatDelegate.getApplicationLocales().isEmpty) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(PERSIAN))
        }
    }

    private companion object {
        const val PERSIAN = "fa-IR"
    }
}
