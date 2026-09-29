package ir.roozban.core.designsystem.theme

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import ir.roozban.core.model.ThemeMode
import ir.roozban.core.model.ThemePalette

private val RoozbanShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun ThemeMode.isDark(): Boolean = when (this) {
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
    ThemeMode.SYSTEM -> {
        // Read again whenever the configuration changes.
        LocalConfiguration.current
        systemIsDark(LocalContext.current) ?: isSystemInDarkTheme()
    }
}

/**
 * The phone's own light/dark setting. The activity's configuration is not trusted here: with the
 * per-app Persian locale, AppCompat rebuilds it and on some phones it reports night while the phone
 * is light. The dark-mode switch itself (UiModeManager) and the system configuration are.
 */
fun systemIsDark(context: Context): Boolean? {
    when (context.getSystemService(UiModeManager::class.java)?.nightMode) {
        UiModeManager.MODE_NIGHT_YES -> return true
        UiModeManager.MODE_NIGHT_NO -> return false
    }
    // Automatic or scheduled: whatever the system is showing right now.
    val night = Resources.getSystem().configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
    return when (night) {
        Configuration.UI_MODE_NIGHT_YES -> true
        Configuration.UI_MODE_NIGHT_NO -> false
        else -> null
    }
}

/** Whether the app is showing its dark theme (not the phone's). */
val LocalDarkTheme = androidx.compose.runtime.staticCompositionLocalOf { false }

/**
 * Root theme. Always right-to-left, regardless of the system locale, because the whole UI is
 * Persian. The chosen [palette] colors the accents; backgrounds stay white (or dark grey).
 * With [transparentBackground] screens let the app backdrop (a picture or pattern) show through.
 */
@Composable
fun RoozbanTheme(
    darkTheme: Boolean = false,
    palette: ThemePalette = ThemePalette.INDIGO,
    dynamicColor: Boolean = false,
    transparentBackground: Boolean = false,
    content: @Composable () -> Unit,
) {
    val base = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> darkSchemeOf(palette)
        else -> lightSchemeOf(palette)
    }
    val colors = if (transparentBackground) base.copy(background = Color.Transparent, surface = Color.Transparent) else base
    CompositionLocalProvider(
        LocalLayoutDirection provides LayoutDirection.Rtl,
        LocalSemanticColors provides if (darkTheme) DarkSemantic else LightSemantic,
        LocalDarkTheme provides darkTheme,
    ) {
        MaterialTheme(
            colorScheme = colors,
            typography = RoozbanTypography,
            shapes = RoozbanShapes,
            content = content,
        )
    }
}
