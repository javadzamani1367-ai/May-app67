package ir.roozban.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Colors for projects and labels. Stored as an index so each entry can have a light- and
 * dark-theme variant with enough contrast.
 */
object TagColors {
    private val light = listOf(
        Color(0xFF1F6F6B), Color(0xFF3B5BA9), Color(0xFF8A5A00), Color(0xFFB3261E),
        Color(0xFF7B4FA0), Color(0xFF2E7D32), Color(0xFFC2185B), Color(0xFF5D6B75),
    )
    private val dark = listOf(
        Color(0xFF8FD5CD), Color(0xFFAFC6FF), Color(0xFFFFB951), Color(0xFFFFB4AB),
        Color(0xFFD9B8FF), Color(0xFF9BD89E), Color(0xFFFFB0CB), Color(0xFFBBC7D0),
    )

    val count: Int get() = light.size

    @Composable
    fun color(index: Int): Color {
        val palette = if (isSystemInDarkTheme()) dark else light
        return palette[Math.floorMod(index, palette.size)]
    }
}
