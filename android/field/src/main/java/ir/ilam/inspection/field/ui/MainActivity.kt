package ir.ilam.inspection.field.ui

import ir.ilam.inspection.field.field

import ir.ilam.inspection.util.KeepLocationWarm
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import ir.ilam.inspection.field.R
import ir.ilam.inspection.field.ui.lock.FieldLockScreen
import ir.ilam.inspection.ui.CrashScreen
import ir.ilam.inspection.ui.theme.InspectionTheme
import ir.ilam.inspection.ui.theme.ThemePreference
import ir.ilam.inspection.util.CrashReporter

/**
 * The one activity. The gate first — the database holds report locations and
 * the people who made them — then the app. ComponentActivity, as in the
 * inspection apps: FragmentActivity refuses the result registry's request codes.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_Inspection)
        super.onCreate(savedInstanceState)
        ThemePreference.load(this)
        val crashReporter = CrashReporter(applicationContext).apply { install() }
        val lastCrash = crashReporter.pending()
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        setContent {
            InspectionTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    // The app draws edge to edge. Below the content: the phone's own
                    // buttons, or the keyboard when it is up — whichever is taller —
                    // so neither a form's buttons nor the field being typed in ever
                    // sit underneath them. Consumed here, once, for every screen.
                    Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))) {
                        // Saveable: coming back from a permission dialog or the
                        // camera app recreates the activity, and must not lock it.
                        // A sign-in that switched accounts restarts the app; it opens straight in.
                        var unlocked by rememberSaveable { mutableStateOf(applicationContext.field.vault.consumeFreshUnlock()) }
                        var crashToShow by rememberSaveable { mutableStateOf(lastCrash) }
                        val report = crashToShow
                        when {
                            report != null -> CrashScreen(report = report, onDismiss = {
                                crashReporter.clear()
                                crashToShow = null
                            })
                            unlocked -> {
                                // Satellites locked by the time a map is opened.
                                KeepLocationWarm()
                                FieldNavigation(onSignInAgain = { unlocked = false })
                            }
                            else -> FieldLockScreen(onUnlocked = { unlocked = true })
                        }
                    }
                }
            }
        }
    }
}
