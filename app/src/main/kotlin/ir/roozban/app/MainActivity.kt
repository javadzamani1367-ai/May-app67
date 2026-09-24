package ir.roozban.app

import android.app.NotificationManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import ir.roozban.app.navigation.RoozbanApp
import ir.roozban.core.alarm.AppLinks
import ir.roozban.core.designsystem.theme.RoozbanTheme
import ir.roozban.core.domain.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject

/** Single activity. AppCompat is used only for per-app locale support. */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject lateinit var settings: SettingsRepository

    /** A screen requested by a notification or the system's Do Not Disturb settings. */
    private val openRequest = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) openRequest.value = target(intent)
        setContent {
            val userSettings by settings.settings.collectAsStateWithLifecycle(initialValue = null)
            val open by openRequest.collectAsStateWithLifecycle()
            RoozbanTheme(dynamicColor = userSettings?.dynamicColor == true) {
                RoozbanApp(openRequest = open, onOpenHandled = { openRequest.value = null })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        target(intent)?.let { openRequest.value = it }
    }

    private fun target(intent: Intent?): String? = when {
        intent == null -> null
        intent.action == NotificationManager.ACTION_AUTOMATIC_ZEN_RULE -> AppLinks.FOCUS
        else -> intent.getStringExtra(AppLinks.EXTRA_OPEN)
    }
}
