package ir.roozban.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import ir.roozban.app.navigation.RoozbanApp
import ir.roozban.core.designsystem.theme.RoozbanTheme
import ir.roozban.core.domain.SettingsRepository
import javax.inject.Inject

/** Single activity. AppCompat is used only for per-app locale support. */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject lateinit var settings: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val userSettings by settings.settings.collectAsStateWithLifecycle(initialValue = null)
            RoozbanTheme(dynamicColor = userSettings?.dynamicColor == true) {
                RoozbanApp()
            }
        }
    }
}
