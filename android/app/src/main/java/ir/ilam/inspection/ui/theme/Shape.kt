package ir.ilam.inspection.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Rounded, not pill-shaped: firm enough to read as a professional tool, soft
 * enough that cards and buttons are obviously separate things to touch.
 */
val TavanShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(26.dp)
)

/** The spacing scale. Screens use these instead of inventing their own numbers. */
object Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp

    /** Outer margin of a screen's content. */
    val screen = 16.dp

    /** The height of a primary action: a gloved thumb, in sunlight. */
    val touch = 56.dp
}
