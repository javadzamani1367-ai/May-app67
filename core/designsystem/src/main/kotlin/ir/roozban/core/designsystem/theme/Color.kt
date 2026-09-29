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
    ThemePalette.SKY -> Accent(Color(0xFF0277BD), Color(0xFFD1E8FF), Color(0xFF001D33), Color(0xFF8ECDFF), Color(0xFF00344F), Color(0xFF004B70), Color(0xFFD1E8FF))
    ThemePalette.NAVY -> Accent(Color(0xFF1A3E8C), Color(0xFFDAE2FF), Color(0xFF001849), Color(0xFFB1C5FF), Color(0xFF002C71), Color(0xFF2A4596), Color(0xFFDAE2FF))
    ThemePalette.PURPLE -> Accent(Color(0xFF8E24AA), Color(0xFFF8D8FF), Color(0xFF330045), Color(0xFFEBB2FF), Color(0xFF52006E), Color(0xFF6F1D8A), Color(0xFFF8D8FF))
    ThemePalette.PINK -> Accent(Color(0xFFC2185B), Color(0xFFFFD9E2), Color(0xFF3E001D), Color(0xFFFFB1C8), Color(0xFF650033), Color(0xFF8E0049), Color(0xFFFFD9E2))
    ThemePalette.RED -> Accent(Color(0xFFB3261E), Color(0xFFFFDAD6), Color(0xFF410002), Color(0xFFFFB4AB), Color(0xFF690005), Color(0xFF93000A), Color(0xFFFFDAD6))
    ThemePalette.ORANGE -> Accent(Color(0xFFB33F00), Color(0xFFFFDBCC), Color(0xFF380D00), Color(0xFFFFB595), Color(0xFF5B1B00), Color(0xFF802A00), Color(0xFFFFDBCC))
    ThemePalette.GOLD -> Accent(Color(0xFF7A5900), Color(0xFFFFDF9E), Color(0xFF261A00), Color(0xFFF5BF48), Color(0xFF402D00), Color(0xFF5C4200), Color(0xFFFFDF9E))
    ThemePalette.BROWN -> Accent(Color(0xFF7B5733), Color(0xFFFFDCBF), Color(0xFF2D1600), Color(0xFFEDBD91), Color(0xFF462A09), Color(0xFF60401E), Color(0xFFFFDCBF))
    ThemePalette.OLIVE -> Accent(Color(0xFF5F6130), Color(0xFFE5E6A8), Color(0xFF1C1D00), Color(0xFFC9CA8E), Color(0xFF313209), Color(0xFF47481B), Color(0xFFE5E6A8))
    ThemePalette.LIME -> Accent(Color(0xFF4C6B00), Color(0xFFCDEE8A), Color(0xFF141F00), Color(0xFFB1D271), Color(0xFF253600), Color(0xFF384F00), Color(0xFFCDEE8A))
    ThemePalette.MINT -> Accent(Color(0xFF00875A), Color(0xFFB7F2D6), Color(0xFF002114), Color(0xFF6FDBAA), Color(0xFF003824), Color(0xFF005236), Color(0xFFB7F2D6))
    ThemePalette.GRAPHITE -> Accent(Color(0xFF3C3F44), Color(0xFFE0E2E8), Color(0xFF16181C), Color(0xFFC4C7CE), Color(0xFF2D3035), Color(0xFF44474D), Color(0xFFE0E2E8))
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
        secondary = a.light,
        onSecondary = Color.White,
        secondaryContainer = a.lightContainer,
        onSecondaryContainer = a.lightOnContainer,
        tertiary = Color(0xFFB9650B),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFFFE6C7),
        onTertiaryContainer = Color(0xFF3F2200),
        error = Color(0xFFD13B3B),
        onError = Color.White,
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002),
        // A canvas tinted with the chosen color, white cards on it: the color is felt everywhere.
        background = mix(Color(0xFFF4F6FB), a.light, 0.09f),
        onBackground = Color(0xFF161A26),
        surface = mix(Color(0xFFF4F6FB), a.light, 0.09f),
        onSurface = Color(0xFF161A26),
        surfaceVariant = mix(Color(0xFFE6E9F2), a.light, 0.14f),
        onSurfaceVariant = Color(0xFF545A6B),
        outline = Color(0xFF7A8193),
        outlineVariant = mix(Color(0xFFD9DDE8), a.light, 0.18f),
        inverseSurface = Color(0xFF2B2F3B),
        inverseOnSurface = Color(0xFFF1F2F7),
        surfaceTint = a.light,
        surfaceDim = Color(0xFFD9DCE6),
        surfaceBright = Color.White,
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = Color(0xFFFCFCFE),
        surfaceContainer = Color.White,
        surfaceContainerHigh = mix(Color(0xFFF1F3F9), a.light, 0.10f),
        surfaceContainerHighest = mix(Color(0xFFE8EBF3), a.light, 0.14f),
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
        secondary = a.dark,
        onSecondary = a.darkOn,
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
        background = mix(Color(0xFF0F121A), a.dark, 0.07f),
        onBackground = Color(0xFFE6E8F0),
        surface = mix(Color(0xFF0F121A), a.dark, 0.07f),
        onSurface = Color(0xFFE6E8F0),
        surfaceVariant = mix(Color(0xFF2E3342), a.dark, 0.10f),
        onSurfaceVariant = Color(0xFFB3B9CA),
        outline = Color(0xFF8A90A2),
        outlineVariant = mix(Color(0xFF363B4B), a.dark, 0.14f),
        inverseSurface = Color(0xFFE6E8F0),
        inverseOnSurface = Color(0xFF2B2F3B),
        surfaceTint = a.dark,
        surfaceDim = Color(0xFF0F121A),
        surfaceBright = Color(0xFF363B4B),
        surfaceContainerLowest = Color(0xFF0B0D13),
        surfaceContainerLow = mix(Color(0xFF151923), a.dark, 0.06f),
        surfaceContainer = mix(Color(0xFF1A1F2B), a.dark, 0.08f),
        surfaceContainerHigh = mix(Color(0xFF232838), a.dark, 0.10f),
        surfaceContainerHighest = mix(Color(0xFF2C3244), a.dark, 0.12f),
    )
}

/** Blends [accent] into [base] by [amount] (0..1). */
internal fun mix(base: Color, accent: Color, amount: Float): Color = Color(
    red = base.red + (accent.red - base.red) * amount,
    green = base.green + (accent.green - base.green) * amount,
    blue = base.blue + (accent.blue - base.blue) * amount,
)

/** Holiday red, the same in every palette so holidays always read as holidays. */
object CalendarColors {
    val holidayLight = LightSemantic.holiday
    val holidayDark = DarkSemantic.holiday
}
