package ir.roozban.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import ir.roozban.core.model.ThemePalette

/**
 * Accent tones of one palette: key color, container and on-container, for light and dark.
 * Neutrals are shared by every palette: pure white with near-black text in light mode, deep
 * grey with off-white text in dark mode.
 */
private data class Accent(
    val light: Color,
    val lightContainer: Color,
    val lightOnContainer: Color,
    val dark: Color,
    val darkOn: Color,
    val darkContainer: Color,
    val darkOnContainer: Color,
)

private fun accentOf(palette: ThemePalette): Accent = when (palette) {
    ThemePalette.INDIGO -> Accent(Color(0xFF4355B9), Color(0xFFDEE0FF), Color(0xFF00105C), Color(0xFFBAC3FF), Color(0xFF08218A), Color(0xFF293CA0), Color(0xFFDEE0FF))
    ThemePalette.OCEAN -> Accent(Color(0xFF00639B), Color(0xFFCEE5FF), Color(0xFF001D33), Color(0xFF96CCFF), Color(0xFF003353), Color(0xFF004A76), Color(0xFFCEE5FF))
    ThemePalette.VIOLET -> Accent(Color(0xFF6750A4), Color(0xFFEADDFF), Color(0xFF21005D), Color(0xFFD0BCFF), Color(0xFF381E72), Color(0xFF4F378B), Color(0xFFEADDFF))
    ThemePalette.ROSE -> Accent(Color(0xFFA23F6A), Color(0xFFFFD9E3), Color(0xFF3E0021), Color(0xFFFFB0CA), Color(0xFF5F1138), Color(0xFF832755), Color(0xFFFFD9E3))
    ThemePalette.CORAL -> Accent(Color(0xFFA63B2B), Color(0xFFFFDAD4), Color(0xFF3F0400), Color(0xFFFFB4A8), Color(0xFF611207), Color(0xFF842718), Color(0xFFFFDAD4))
    ThemePalette.AMBER -> Accent(Color(0xFF8B5000), Color(0xFFFFDCBE), Color(0xFF2C1600), Color(0xFFFFB870), Color(0xFF4A2800), Color(0xFF693C00), Color(0xFFFFDCBE))
    ThemePalette.TEAL -> Accent(Color(0xFF006A6A), Color(0xFF9CF1F0), Color(0xFF002020), Color(0xFF4CDADA), Color(0xFF003737), Color(0xFF004F4F), Color(0xFF9CF1F0))
    ThemePalette.SLATE -> Accent(Color(0xFF4A5568), Color(0xFFDCE2F2), Color(0xFF121B2A), Color(0xFFBCC6DB), Color(0xFF263041), Color(0xFF333D50), Color(0xFFDCE2F2))
    ThemePalette.GREEN -> Accent(Color(0xFF1F6F6B), Color(0xFFB6EDE6), Color(0xFF00201E), Color(0xFF8FD5CD), Color(0xFF003734), Color(0xFF00504C), Color(0xFFABF1E9))
}

/** Swatch shown in the palette picker. */
fun paletteSwatch(palette: ThemePalette): Color = accentOf(palette).light

fun lightSchemeOf(palette: ThemePalette): ColorScheme {
    val a = accentOf(palette)
    return lightColorScheme(
        primary = a.light,
        onPrimary = Color.White,
        primaryContainer = a.lightContainer,
        onPrimaryContainer = a.lightOnContainer,
        inversePrimary = a.dark,
        secondary = Color(0xFF5B5D72),
        onSecondary = Color.White,
        secondaryContainer = a.lightContainer.copy(alpha = 0.6f).compositeOverWhite(),
        onSecondaryContainer = a.lightOnContainer,
        tertiary = Color(0xFF9A4521),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFFFDBCC),
        onTertiaryContainer = Color(0xFF360F00),
        error = Color(0xFFBA1A1A),
        onError = Color.White,
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002),
        background = Color.White,
        onBackground = Color(0xFF16171B),
        surface = Color.White,
        onSurface = Color(0xFF16171B),
        surfaceVariant = Color(0xFFE4E3EB),
        onSurfaceVariant = Color(0xFF45464F),
        outline = Color(0xFF767680),
        outlineVariant = Color(0xFFC8C7D0),
        inverseSurface = Color(0xFF2F3036),
        inverseOnSurface = Color(0xFFF2F0F4),
        surfaceTint = a.light,
        surfaceDim = Color(0xFFDCDBE0),
        surfaceBright = Color.White,
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = Color(0xFFF8F8FB),
        surfaceContainer = Color(0xFFF3F3F7),
        surfaceContainerHigh = Color(0xFFEDEDF1),
        surfaceContainerHighest = Color(0xFFE7E7EC),
    )
}

fun darkSchemeOf(palette: ThemePalette): ColorScheme {
    val a = accentOf(palette)
    return darkColorScheme(
        primary = a.dark,
        onPrimary = a.darkOn,
        primaryContainer = a.darkContainer,
        onPrimaryContainer = a.darkOnContainer,
        inversePrimary = a.light,
        secondary = Color(0xFFC4C5DD),
        onSecondary = Color(0xFF2D2F42),
        secondaryContainer = Color(0xFF3A3C50),
        onSecondaryContainer = Color(0xFFE0E1F9),
        tertiary = Color(0xFFFFB595),
        onTertiary = Color(0xFF571E00),
        tertiaryContainer = Color(0xFF7B2E0B),
        onTertiaryContainer = Color(0xFFFFDBCC),
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),
        background = Color(0xFF121316),
        onBackground = Color(0xFFE4E2E6),
        surface = Color(0xFF121316),
        onSurface = Color(0xFFE4E2E6),
        surfaceVariant = Color(0xFF46464F),
        onSurfaceVariant = Color(0xFFC7C5D0),
        outline = Color(0xFF90909A),
        outlineVariant = Color(0xFF46464F),
        inverseSurface = Color(0xFFE4E2E6),
        inverseOnSurface = Color(0xFF2F3036),
        surfaceTint = a.dark,
        surfaceDim = Color(0xFF121316),
        surfaceBright = Color(0xFF38393C),
        surfaceContainerLowest = Color(0xFF0D0E11),
        surfaceContainerLow = Color(0xFF1A1B1F),
        surfaceContainer = Color(0xFF1F1F23),
        surfaceContainerHigh = Color(0xFF292A2D),
        surfaceContainerHighest = Color(0xFF343438),
    )
}

private fun Color.compositeOverWhite(): Color = Color(
    red = red * alpha + (1 - alpha),
    green = green * alpha + (1 - alpha),
    blue = blue * alpha + (1 - alpha),
)

/** Holiday red, the same in every palette so holidays always read as holidays. */
object CalendarColors {
    val holidayLight = Color(0xFFD32F2F)
    val holidayDark = Color(0xFFFF8A80)
}
