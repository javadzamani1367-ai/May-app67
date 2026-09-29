package ir.roozban.core.designsystem.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Colors for projects and labels. Stored as an index so each entry can have a light- and
 * dark-theme variant with enough contrast.
 */
object TagColors {
    // The first eight are the original colors (stored indexes keep their color); the rest widen the choice.
    private val light = listOf(
        Color(0xFF4355B9), Color(0xFF00639B), Color(0xFF8A5A00), Color(0xFFB3261E),
        Color(0xFF7B4FA0), Color(0xFF2E7D32), Color(0xFFC2185B), Color(0xFF5D6B75),
        Color(0xFF00796B), Color(0xFF0097A7), Color(0xFF0277BD), Color(0xFF283593),
        Color(0xFF512DA8), Color(0xFF8E24AA), Color(0xFFAD1457), Color(0xFFD32F2F),
        Color(0xFFE64A19), Color(0xFFEF6C00), Color(0xFFFF8F00), Color(0xFF827717),
        Color(0xFF558B2F), Color(0xFF388E3C), Color(0xFF6D4C41), Color(0xFF455A64),
    )
    private val dark = listOf(
        Color(0xFFBAC3FF), Color(0xFF96CCFF), Color(0xFFFFB951), Color(0xFFFFB4AB),
        Color(0xFFD9B8FF), Color(0xFF9BD89E), Color(0xFFFFB0CB), Color(0xFFBBC7D0),
        Color(0xFF80CBC4), Color(0xFF80DEEA), Color(0xFF81D4FA), Color(0xFF9FA8DA),
        Color(0xFFB39DDB), Color(0xFFCE93D8), Color(0xFFF48FB1), Color(0xFFEF9A9A),
        Color(0xFFFFAB91), Color(0xFFFFCC80), Color(0xFFFFE082), Color(0xFFE6EE9C),
        Color(0xFFC5E1A5), Color(0xFFA5D6A7), Color(0xFFBCAAA4), Color(0xFFB0BEC5),
    )

    val count: Int get() = light.size

    @Composable
    fun color(index: Int): Color {
        val palette = if (LocalDarkTheme.current) dark else light
        return palette[Math.floorMod(index, palette.size)]
    }
}
