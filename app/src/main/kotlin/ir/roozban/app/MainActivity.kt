package ir.roozban.app

import android.app.NotificationManager
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import ir.roozban.app.navigation.RoozbanApp
import ir.roozban.core.alarm.AppLinks
import ir.roozban.core.alarm.DateNotifier
import ir.roozban.core.designsystem.theme.AppBackdrop
import ir.roozban.core.designsystem.theme.Backgrounds
import ir.roozban.core.designsystem.theme.RoozbanTheme
import ir.roozban.core.designsystem.theme.isDark
import ir.roozban.core.domain.SettingsRepository
import ir.roozban.core.model.UserSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Single activity. AppCompat is used only for per-app locale support. */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var dateNotifier: DateNotifier

    /** A screen requested by a notification or the system's Do Not Disturb settings. */
    private val openRequest = MutableStateFlow<String?>(null)

    /** Null until settings are read; the splash screen stays up meanwhile (no theme flash). */
    private val loaded = MutableStateFlow<UserSettings?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        splash.setKeepOnScreenCondition { loaded.value == null }
        lifecycleScope.launch { settings.settings.collect { loaded.value = it } }
        if (savedInstanceState == null) openRequest.value = target(intent)
        setContent {
            val s by loaded.collectAsStateWithLifecycle()
            val open by openRequest.collectAsStateWithLifecycle()
            val current = s
            if (current != null) AppContent(current, open)
        }
    }

    @Composable
    private fun AppContent(userSettings: UserSettings, open: String?) {
        val dark = userSettings.themeMode.isDark()
        LaunchedEffect(dark) {
            val style = if (dark) {
                SystemBarStyle.dark(Color.TRANSPARENT)
            } else {
                SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
            }
            enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
        }
        val background = userSettings.background
        val image = if (background?.startsWith(UserSettings.BACKGROUND_IMAGE) == true) Backgrounds.imageFile(filesDir).takeIf { it.exists() } else null
        val hasBackdrop = Backgrounds.preset(background) != null || image != null
        RoozbanTheme(
            darkTheme = dark,
            palette = userSettings.palette,
            dynamicColor = userSettings.dynamicColor,
            transparentBackground = hasBackdrop,
        ) {
            Box {
                if (hasBackdrop) {
                    AppBackdrop(
                        background = background,
                        imageFile = image,
                        veil = userSettings.backgroundVeil,
                        veilColor = if (dark) ComposeColor(0xFF121316) else ComposeColor.White,
                    )
                } else {
                    AppBackdrop(null, null, 0f, MaterialTheme.colorScheme.background)
                }
                RoozbanApp(
                    startScreen = userSettings.startScreen,
                    openRequest = open,
                    onOpenHandled = { openRequest.value = null },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // E.g. notification permission just granted: show today's date right away.
        lifecycleScope.launch { dateNotifier.refresh() }
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
