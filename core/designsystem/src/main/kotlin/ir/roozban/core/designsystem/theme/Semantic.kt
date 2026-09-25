package ir.roozban.core.designsystem.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import ir.roozban.core.model.Quadrant

/** One semantic role: the strong color, text on it, a soft container and text on that. */
@Immutable
data class Role(val color: Color, val onColor: Color, val container: Color, val onContainer: Color)

/**
 * Meaning-carrying colors, the same in every palette so states read without text:
 * success/completed green-teal, warning amber, error red, info blue, focus violet.
 */
@Immutable
data class SemanticColors(
    val success: Role,
    val warning: Role,
    val error: Role,
    val info: Role,
    val focus: Role,
    /** Completed tasks and done habit days. */
    val completed: Role,
    val holiday: Color,
    /** Streaks. */
    val streak: Role,
)

internal val LightSemantic = SemanticColors(
    success = Role(Color(0xFF1E8E5A), Color.White, Color(0xFFDDF4E7), Color(0xFF0B3D24)),
    warning = Role(Color(0xFFC26A00), Color.White, Color(0xFFFFEBCF), Color(0xFF4A2A00)),
    error = Role(Color(0xFFD13B3B), Color.White, Color(0xFFFFE1DE), Color(0xFF5C0A0A)),
    info = Role(Color(0xFF1E6FD9), Color.White, Color(0xFFDCEBFF), Color(0xFF0A2E5C)),
    focus = Role(Color(0xFF7A4CE0), Color.White, Color(0xFFEEE6FF), Color(0xFF2E1566)),
    completed = Role(Color(0xFF0F9D8A), Color.White, Color(0xFFD6F3EE), Color(0xFF053D35)),
    holiday = Color(0xFFD32F2F),
    streak = Role(Color(0xFFE8710A), Color.White, Color(0xFFFFE9D6), Color(0xFF4A2200)),
)

internal val DarkSemantic = SemanticColors(
    success = Role(Color(0xFF6FD6A0), Color(0xFF003920), Color(0xFF0F3D27), Color(0xFFC9F2DB)),
    warning = Role(Color(0xFFFFB866), Color(0xFF462600), Color(0xFF4A2E00), Color(0xFFFFE0B8)),
    error = Role(Color(0xFFFF8F87), Color(0xFF5C0A0A), Color(0xFF5A1A18), Color(0xFFFFDAD6)),
    info = Role(Color(0xFF8CB8FF), Color(0xFF0A2E5C), Color(0xFF0E305E), Color(0xFFD8E6FF)),
    focus = Role(Color(0xFFC7B1FF), Color(0xFF2E1566), Color(0xFF3A2475), Color(0xFFEDE4FF)),
    completed = Role(Color(0xFF5ED8C6), Color(0xFF003731), Color(0xFF073D36), Color(0xFFC9F4EC)),
    holiday = Color(0xFFFF8A80),
    streak = Role(Color(0xFFFFA858), Color(0xFF4A2200), Color(0xFF4F2A06), Color(0xFFFFE0C2)),
)

val LocalSemanticColors = staticCompositionLocalOf { LightSemantic }

/** Access point for the design system's extra tokens: `Roozban.colors.success.color`. */
object Roozban {
    val colors: SemanticColors
        @Composable @ReadOnlyComposable get() = LocalSemanticColors.current
}

/**
 * Eisenhower quadrants: urgent+important red, important violet (planned focus work),
 * urgent amber, the rest neutral.
 */
@Composable
@ReadOnlyComposable
fun priorityColor(quadrant: Quadrant, neutral: Color): Color = when (quadrant) {
    Quadrant.DO_FIRST -> Roozban.colors.error.color
    Quadrant.SCHEDULE -> Roozban.colors.focus.color
    Quadrant.DELEGATE -> Roozban.colors.warning.color
    Quadrant.ELIMINATE, Quadrant.NONE -> neutral
}
