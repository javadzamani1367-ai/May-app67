package ir.roozban.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import dagger.hilt.android.AndroidEntryPoint
import ir.roozban.app.navigation.RoozbanNavHost
import ir.roozban.core.designsystem.theme.RoozbanTheme

/** Single activity. AppCompat is used only for per-app locale support. */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            RoozbanTheme {
                RoozbanNavHost()
            }
        }
    }
}
