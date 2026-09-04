package ir.ilam.inspection.ui

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import ir.ilam.inspection.container
import ir.ilam.inspection.ui.lock.LockScreen
import ir.ilam.inspection.util.CrashReporter
import ir.ilam.inspection.ui.theme.InspectionTheme

// FragmentActivity, not ComponentActivity: BiometricPrompt needs a fragment host.
class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val crashReporter = CrashReporter(applicationContext).apply { install() }
        val lastCrash = crashReporter.pending()
        enableEdgeToEdge()
        setContent {
            InspectionTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val vault = remember { container.vault }
                    var unlocked by remember { mutableStateOf(false) }
                    var crashToShow by remember { mutableStateOf(lastCrash) }

                    val report = crashToShow
                    when {
                        report != null -> CrashScreen(
                            report = report,
                            onDismiss = {
                                crashReporter.clear()
                                crashToShow = null
                            }
                        )
                        unlocked -> AppNavigation()
                        else -> LockScreen(vault = vault, onUnlocked = { unlocked = true })
                    }
                }
            }
        }
    }
}
