package ir.roozban.core.designsystem.theme

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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import ir.roozban.core.model.ThemeMode
import ir.roozban.core.model.ThemePalette

private val RoozbanShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun ThemeMode.isDark(): Boolean = when (this) {
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
}

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
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme(
            colorScheme = colors,
            typography = RoozbanTypography,
            shapes = RoozbanShapes,
            content = content,
        )
    }
}
