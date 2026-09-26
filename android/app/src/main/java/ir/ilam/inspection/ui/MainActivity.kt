package ir.ilam.inspection.ui

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.activity.ComponentActivity
import ir.ilam.inspection.R
import ir.ilam.inspection.container
import ir.ilam.inspection.data.repo.SettingsRepository
import ir.ilam.inspection.ui.lock.LockScreen
import ir.ilam.inspection.util.CrashReporter
import ir.ilam.inspection.ui.theme.InspectionTheme
import ir.ilam.inspection.ui.theme.ThemePreference

/**
 * ComponentActivity, never FragmentActivity: FragmentActivity refuses the
 * request codes the AndroidX result registry generates, which crashed every
 * file picker, barcode scan and permission dialog the app opens.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // The manifest starts on the navy splash; the real theme takes over
        // before anything is drawn.
        setTheme(R.style.Theme_Inspection)
        super.onCreate(savedInstanceState)
        ThemePreference.load(this)
        val crashReporter = CrashReporter(applicationContext).apply { install() }
        val lastCrash = crashReporter.pending()
        // Light status bar icons always: every screen's header is deep navy,
        // in the light theme as much as the dark one.
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        setContent {
            InspectionTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val vault = remember { container.vault }
                    // Saveable, not remembered: Android recreates the activity
                    // when it comes back from a permission dialog or another
                    // app's screen, and a plain remember would drop the expert
                    // back at the PIN screen as if the app had closed.
                    var unlocked by rememberSaveable { mutableStateOf(false) }
                    var crashToShow by rememberSaveable { mutableStateOf(lastCrash) }

                    val report = crashToShow
                    when {
                        report != null -> CrashScreen(
                            report = report,
                            onDismiss = {
                                crashReporter.clear()
                                crashToShow = null
                            }
                        )
                        unlocked -> {
                            // Every report carries the code the expert logged
                            // in with, so the settings copy the exporters read
                            // is brought in line the moment they are through
                            // the door — not whenever they next open settings.
                            val appContainer = container
                            LaunchedEffect(Unit) {
                                vault.userCode()?.takeIf { it.isNotBlank() }?.let { code ->
                                    appContainer.settingsRepository
                                        .put(SettingsRepository.KEY_EXPERT_CODE, code)
                                }
                            }
                            AppNavigation()
                        }
                        else -> LockScreen(vault = vault, onUnlocked = { unlocked = true })
                    }
                }
            }
        }
    }
}
