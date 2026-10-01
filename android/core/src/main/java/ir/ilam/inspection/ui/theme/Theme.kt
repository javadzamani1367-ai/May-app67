package ir.ilam.inspection.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

/*
 * Light: white cards on a cool grey page, navy for the actions that matter,
 * and text dark enough to read in direct sun.
 */
private val LightColors = lightColorScheme(
    primary = Navy800,
    onPrimary = White,
    primaryContainer = Navy100,
    onPrimaryContainer = Navy900,
    inversePrimary = Teal300,
    secondary = Teal700,
    onSecondary = White,
    secondaryContainer = Teal100,
    onSecondaryContainer = Teal900,
    tertiary = Amber700,
    onTertiary = White,
    tertiaryContainer = Amber100,
    onTertiaryContainer = Amber900,
    error = Red700,
    onError = White,
    errorContainer = Red100,
    onErrorContainer = Red900,
    background = Color(0xFFF2F5F9),
    onBackground = Slate900,
    surface = White,
    onSurface = Slate900,
    surfaceVariant = Color(0xFFE6EBF2),
    onSurfaceVariant = Slate600,
    surfaceTint = Navy800,
    inverseSurface = Slate800,
    inverseOnSurface = Slate100,
    outline = Color(0xFF8391A5),
    outlineVariant = Color(0xFFD5DCE6),
    scrim = Color(0xFF000000),
    surfaceBright = White,
    surfaceDim = Color(0xFFDDE3EC),
    surfaceContainerLowest = White,
    surfaceContainerLow = Color(0xFFF7F9FC),
    surfaceContainer = Color(0xFFEEF2F7),
    surfaceContainerHigh = Color(0xFFE6EBF2),
    surfaceContainerHighest = Color(0xFFDDE3EC)
)

/*
 * Dark: deep navy, never pure black, with teal taking over as the action
 * colour because navy disappears on navy. Built for a car at night or an
 * unlit room, not converted from the light scheme.
 */
private val DarkColors = darkColorScheme(
    primary = Teal400,
    onPrimary = Color(0xFF032B28),
    primaryContainer = Color(0xFF0E3B3A),
    onPrimaryContainer = Teal100,
    inversePrimary = Teal700,
    secondary = Navy300,
    onSecondary = Navy950,
    secondaryContainer = Color(0xFF1A2E52),
    onSecondaryContainer = Navy100,
    tertiary = Amber400,
    onTertiary = Color(0xFF3A2503),
    tertiaryContainer = Color(0xFF3F2A0B),
    onTertiaryContainer = Amber100,
    error = Red400,
    onError = Color(0xFF3A0A0A),
    errorContainer = Color(0xFF451717),
    onErrorContainer = Red100,
    background = Slate950,
    onBackground = Color(0xFFE6EDF7),
    surface = Color(0xFF0F1B2E),
    onSurface = Color(0xFFE6EDF7),
    surfaceVariant = Color(0xFF1B2A44),
    onSurfaceVariant = Color(0xFF9FB0C8),
    surfaceTint = Teal400,
    inverseSurface = Slate200,
    inverseOnSurface = Slate900,
    outline = Color(0xFF5B6B85),
    outlineVariant = Color(0xFF26344D),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF22324D),
    surfaceDim = Color(0xFF08101D),
    surfaceContainerLowest = Color(0xFF08101D),
    surfaceContainerLow = Color(0xFF0F1B2E),
    surfaceContainer = Color(0xFF13213A),
    surfaceContainerHigh = Color(0xFF182843),
    surfaceContainerHighest = Color(0xFF1E3050)
)

/**
 * The whole app is laid out right to left; individual screens never have to
 * think about direction.
 */
@Composable
fun InspectionTheme(
    mode: ThemeMode = ThemePreference.mode,
    content: @Composable () -> Unit
) {
    val dark = when (mode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val context = LocalContext.current
    val typography = remember(context) { appTypography(AppFontFamilies.load(context)) }
    CompositionLocalProvider(
        LocalLayoutDirection provides LayoutDirection.Rtl,
        LocalTavanColors provides if (dark) DarkTavanColors else LightTavanColors
    ) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
            typography = typography,
            shapes = TavanShapes,
            content = content
        )
    }
}

/** `Tavan.colors.warning`, the way `MaterialTheme.colorScheme` reads. */
object Tavan {
    val colors: TavanColors
        @Composable @ReadOnlyComposable get() = LocalTavanColors.current
}
