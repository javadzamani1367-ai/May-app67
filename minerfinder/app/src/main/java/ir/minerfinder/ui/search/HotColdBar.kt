package ir.minerfinder.ui.search

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import ir.minerfinder.ui.theme.Palette

/**
 * The big hot/cold bar: blue on the right (cold, RTL start) to red on the left.
 * The filled part and the marker move together with the score.
 */
@Composable
fun HotColdBar(score: Float, modifier: Modifier = Modifier) {
    val value by animateFloatAsState((score / 100f).coerceIn(0f, 1f), tween(350), label = "heat")
    Canvas(modifier.fillMaxWidth().height(64.dp)) {
        val r = CornerRadius(18.dp.toPx())
        // RTL: the bar grows from right to left.
        val gradient = Brush.horizontalGradient(
            listOf(Palette.Hot, Palette.Warm, Color(0xFF9BC53D), Palette.Cold),
        )
        drawRoundRect(Color(0xFFE3E8EF), cornerRadius = r)
        val filled = size.width * value
        drawRoundRect(
            brush = gradient,
            topLeft = Offset(size.width - filled, 0f),
            size = Size(filled, size.height),
            cornerRadius = r,
            alpha = 0.95f,
        )
        val x = size.width - filled
        drawLine(Color.White, Offset(x, 4f), Offset(x, size.height - 4f), strokeWidth = 6.dp.toPx())
        drawRoundRect(Palette.Navy, cornerRadius = r, style = Stroke(2.dp.toPx()))
    }
}

fun heatColor(score: Float): Color = when {
    score >= 75 -> Palette.Hot
    score >= 50 -> Palette.Warm
    score >= 25 -> Color(0xFF9BC53D)
    else -> Palette.Cold
}
