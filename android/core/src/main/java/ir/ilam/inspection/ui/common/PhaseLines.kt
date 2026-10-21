package ir.ilam.inspection.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ir.ilam.inspection.ui.theme.Spacing
import ir.ilam.inspection.ui.theme.Tavan

/** R, S and T: the same three colours in every chart and table of phase currents. */
@Composable
fun phaseColors(): List<Color> = listOf(Tavan.colors.danger.strong, Tavan.colors.warning.strong, Tavan.colors.info.strong)

@Composable
fun PhaseLegend(name: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(modifier = Modifier.size(10.dp).background(color, CircleShape))
        Text(name, style = MaterialTheme.typography.labelMedium)
    }
}

/**
 * Three phases over time, one line each. [points] run oldest to newest, each the
 * three currents (a missing one breaks its line there); the oldest sits on the
 * right, as the tables beside it read right to left.
 */
@Composable
fun PhaseLines(points: List<List<Double?>>, modifier: Modifier = Modifier, height: Dp = 120.dp) {
    if (points.size < 2) return
    val colors = phaseColors()
    val grid = MaterialTheme.colorScheme.outlineVariant
    val max = (points.flatMap { it.filterNotNull() }.maxOrNull() ?: 1.0).coerceAtLeast(1.0) * 1.1
    Column(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxWidth().height(height)) {
            val step = size.width / (points.size - 1)
            for (i in 0..4) drawLine(grid, Offset(0f, size.height * i / 4), Offset(size.width, size.height * i / 4))
            for (phase in 0..2) {
                val path = Path()
                var started = false
                points.forEachIndexed { i, values ->
                    val v = values.getOrNull(phase) ?: run { started = false; return@forEachIndexed }
                    val at = Offset(size.width - step * i, (size.height * (1 - v / max)).toFloat())
                    if (started) path.lineTo(at.x, at.y) else path.moveTo(at.x, at.y)
                    started = true
                    drawCircle(colors[phase], radius = 3.dp.toPx(), center = at)
                }
                drawPath(path, colors[phase], style = Stroke(width = 2.dp.toPx()))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md), modifier = Modifier.padding(top = Spacing.xs)) {
            listOf("R", "S", "T").forEachIndexed { i, name -> PhaseLegend(name, colors[i]) }
        }
    }
}
