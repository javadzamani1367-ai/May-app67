package ir.ilam.inspection.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * What a colour means, not which colour it is.
 *
 * Every badge, KPI and status in the app names a [Tone]; the tone decides the
 * colour for the current theme. A screen that asks for "warning" gets the
 * amber that reads on white in daylight, and a softer amber on the dark
 * navy at night — and never has to know which.
 */
enum class Tone { NEUTRAL, BRAND, ACCENT, INFO, SUCCESS, WARNING, DANGER }

/** Strong colour for icons and text, a container behind it, and text on the container. */
@Immutable
data class ToneColors(val strong: Color, val container: Color, val onContainer: Color)

@Immutable
data class TavanColors(
    val dark: Boolean,
    val neutral: ToneColors,
    val brand: ToneColors,
    val accent: ToneColors,
    val info: ToneColors,
    val success: ToneColors,
    val warning: ToneColors,
    val danger: ToneColors,
    /** The branded header: deep navy in both themes, so the app is recognisable at a glance. */
    val header: Color,
    val headerDeep: Color,
    val onHeader: Color,
    val onHeaderMuted: Color,
    /** Empty part of a progress bar or chart. */
    val track: Color
) {
    fun of(tone: Tone): ToneColors = when (tone) {
        Tone.NEUTRAL -> neutral
        Tone.BRAND -> brand
        Tone.ACCENT -> accent
        Tone.INFO -> info
        Tone.SUCCESS -> success
        Tone.WARNING -> warning
        Tone.DANGER -> danger
    }
}

val LightTavanColors = TavanColors(
    dark = false,
    neutral = ToneColors(Slate600, Slate100, Slate700),
    brand = ToneColors(Navy800, Navy100, Navy900),
    accent = ToneColors(Teal700, Teal100, Teal900),
    info = ToneColors(Blue700, Blue100, Blue900),
    success = ToneColors(Green700, Green100, Green900),
    warning = ToneColors(Amber700, Amber100, Amber900),
    danger = ToneColors(Red700, Red100, Red900),
    header = Navy900,
    headerDeep = Navy950,
    onHeader = White,
    onHeaderMuted = Navy200,
    track = Slate200
)

/**
 * Dark is designed, not inverted: surfaces are deep navy rather than black,
 * containers are dim tints of each family rather than pale ones, and the
 * strong tones are lifted so they still read at night without glaring.
 */
val DarkTavanColors = TavanColors(
    dark = true,
    neutral = ToneColors(Slate400, Color(0xFF1F2B40), Slate200),
    brand = ToneColors(Navy300, Color(0xFF1A2E52), Navy100),
    accent = ToneColors(Teal400, Color(0xFF0E3B3A), Teal100),
    info = ToneColors(Blue400, Color(0xFF172E5C), Blue100),
    success = ToneColors(Green400, Color(0xFF123822), Green100),
    warning = ToneColors(Amber400, Color(0xFF3F2A0B), Amber100),
    danger = ToneColors(Red400, Color(0xFF451717), Red100),
    header = Color(0xFF0D1A30),
    headerDeep = Color(0xFF081222),
    onHeader = Color(0xFFE6EDF7),
    onHeaderMuted = Color(0xFF9FB0C8),
    track = Color(0xFF22324D)
)

val LocalTavanColors = staticCompositionLocalOf { LightTavanColors }
