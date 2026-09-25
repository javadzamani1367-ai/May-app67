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
    ThemePalette.INDIGO -> Accent(Color(0xFF4A5BD4), Color(0xFFE2E5FF), Color(0xFF0E1A66), Color(0xFFB8C2FF), Color(0xFF15237A), Color(0xFF3141A8), Color(0xFFE2E5FF))
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
        secondary = Color(0xFF0E8A86),
        onSecondary = Color.White,
        secondaryContainer = a.lightContainer.copy(alpha = 0.6f).compositeOverWhite(),
        onSecondaryContainer = a.lightOnContainer,
        tertiary = Color(0xFFB9650B),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFFFE6C7),
        onTertiaryContainer = Color(0xFF3F2200),
        error = Color(0xFFD13B3B),
        onError = Color.White,
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002),
        // A barely tinted canvas with white cards on it: depth without heaviness.
        background = mix(Color(0xFFF4F6FB), a.light, 0.035f),
        onBackground = Color(0xFF161A26),
        surface = mix(Color(0xFFF4F6FB), a.light, 0.035f),
        onSurface = Color(0xFF161A26),
        surfaceVariant = mix(Color(0xFFE6E9F2), a.light, 0.06f),
        onSurfaceVariant = Color(0xFF545A6B),
        outline = Color(0xFF7A8193),
        outlineVariant = mix(Color(0xFFD9DDE8), a.light, 0.08f),
        inverseSurface = Color(0xFF2B2F3B),
        inverseOnSurface = Color(0xFFF1F2F7),
        surfaceTint = a.light,
        surfaceDim = Color(0xFFD9DCE6),
        surfaceBright = Color.White,
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = Color(0xFFFCFCFE),
        surfaceContainer = Color.White,
        surfaceContainerHigh = mix(Color(0xFFF1F3F9), a.light, 0.04f),
        surfaceContainerHighest = mix(Color(0xFFE8EBF3), a.light, 0.06f),
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
        secondary = Color(0xFF6ED7D0),
        onSecondary = Color(0xFF003734),
        secondaryContainer = a.darkContainer,
        onSecondaryContainer = a.darkOnContainer,
        tertiary = Color(0xFFFFBE73),
        onTertiary = Color(0xFF462600),
        tertiaryContainer = Color(0xFF5E3A06),
        onTertiaryContainer = Color(0xFFFFE6C7),
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),
        // Deep blue-grey rather than black; raised surfaces get lighter, accents stay pastel.
        background = mix(Color(0xFF0F121A), a.dark, 0.03f),
        onBackground = Color(0xFFE6E8F0),
        surface = mix(Color(0xFF0F121A), a.dark, 0.03f),
        onSurface = Color(0xFFE6E8F0),
        surfaceVariant = Color(0xFF2E3342),
        onSurfaceVariant = Color(0xFFB3B9CA),
        outline = Color(0xFF8A90A2),
        outlineVariant = Color(0xFF363B4B),
        inverseSurface = Color(0xFFE6E8F0),
        inverseOnSurface = Color(0xFF2B2F3B),
        surfaceTint = a.dark,
        surfaceDim = Color(0xFF0F121A),
        surfaceBright = Color(0xFF363B4B),
        surfaceContainerLowest = Color(0xFF0B0D13),
        surfaceContainerLow = mix(Color(0xFF151923), a.dark, 0.03f),
        surfaceContainer = mix(Color(0xFF1A1F2B), a.dark, 0.04f),
        surfaceContainerHigh = mix(Color(0xFF232838), a.dark, 0.05f),
        surfaceContainerHighest = mix(Color(0xFF2C3244), a.dark, 0.06f),
    )
}

/** Blends [accent] into [base] by [amount] (0..1). */
internal fun mix(base: Color, accent: Color, amount: Float): Color = Color(
    red = base.red + (accent.red - base.red) * amount,
    green = base.green + (accent.green - base.green) * amount,
    blue = base.blue + (accent.blue - base.blue) * amount,
)

private fun Color.compositeOverWhite(): Color = Color(
    red = red * alpha + (1 - alpha),
    green = green * alpha + (1 - alpha),
    blue = blue * alpha + (1 - alpha),
)

/** Holiday red, the same in every palette so holidays always read as holidays. */
object CalendarColors {
    val holidayLight = LightSemantic.holiday
    val holidayDark = DarkSemantic.holiday
}
