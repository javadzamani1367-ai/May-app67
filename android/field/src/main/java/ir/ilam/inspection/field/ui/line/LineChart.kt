package ir.ilam.inspection.field.ui.line

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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import ir.ilam.inspection.field.R
import ir.ilam.inspection.field.data.LineVerdict
import ir.ilam.inspection.field.data.SegmentKind
import ir.ilam.inspection.ui.theme.Spacing
import ir.ilam.inspection.ui.theme.Tavan
import ir.ilam.inspection.util.PersianNumbers
import kotlin.math.ceil

/** The colour of each phase, in the order R, S, T: red, amber, blue, as on the network's own markings. */
@Composable
fun phaseColors(): List<Color> = listOf(Tavan.colors.danger.strong, Tavan.colors.warning.strong, Tavan.colors.info.strong)

/**
 * The current on each phase along the walk, the panel first. A stretch that
 * lost a lot is shaded red, one that gained is shaded amber, so where the
 * load sits is seen before a number is read. Drawn left to right whatever
 * the layout direction: the walk starts at the panel and goes on.
 */
@Composable
fun LineChart(verdict: LineVerdict, modifier: Modifier = Modifier, height: Dp = 220.dp) {
    val points = verdict.points
    val colors = phaseColors()
    val grid = MaterialTheme.colorScheme.outlineVariant
    val label = MaterialTheme.colorScheme.onSurfaceVariant
    val heavyShade = Tavan.colors.danger.container
    val riseShade = Tavan.colors.warning.container
    val surface = MaterialTheme.colorScheme.surface
    val measurer = rememberTextMeasurer()
    val panel = stringResource(R.string.line_panel_short)
    val style = MaterialTheme.typography.labelSmall.copy(color = label)

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Column(modifier = modifier.fillMaxWidth()) {
            Canvas(modifier = Modifier.fillMaxWidth().height(height)) {
                if (points.size < 2) return@Canvas
                val left = 40.dp.toPx()
                val right = 12.dp.toPx()
                val top = 10.dp.toPx()
                val bottom = 26.dp.toPx()
                val plotW = size.width - left - right
                val plotH = size.height - top - bottom
                val maxValue = points.maxOf { it.phases.max() }.coerceAtLeast(1.0)
                val step = niceStep(maxValue)
                val ceiling = ceil(maxValue / step) * step
                fun x(i: Int) = left + plotW * i / (points.size - 1)
                fun y(v: Double) = top + plotH * (1 - (v / ceiling)).toFloat()

                verdict.segments.forEachIndexed { i, segment ->
                    val shade = when (verdict.kindOf(segment)) {
                        SegmentKind.HEAVY -> heavyShade
                        SegmentKind.RISE -> riseShade
                        SegmentKind.NORMAL -> null
                    } ?: return@forEachIndexed
                    drawRect(shade, topLeft = Offset(x(i), top), size = Size(x(i + 1) - x(i), plotH))
                }
                var v = 0.0
                while (v <= ceiling + 0.001) {
                    val yy = y(v)
                    drawLine(grid, Offset(left, yy), Offset(size.width - right, yy), strokeWidth = 1.dp.toPx())
                    val text = measurer.measure(PersianNumbers.toPersian(v.toLong()), style)
                    drawText(text, topLeft = Offset(left - text.size.width - 6.dp.toPx(), yy - text.size.height / 2))
                    v += step
                }
                points.forEachIndexed { i, point ->
                    val name = if (point.pole == 0) panel else PersianNumbers.toPersian(point.pole)
                    val text = measurer.measure(name, style)
                    drawText(text, topLeft = Offset(x(i) - text.size.width / 2, size.height - bottom + 6.dp.toPx()))
                }
                (0 until 3).forEach { phase ->
                    val path = Path()
                    points.forEachIndexed { i, point ->
                        val at = Offset(x(i), y(point.phases[phase]))
                        if (i == 0) path.moveTo(at.x, at.y) else path.lineTo(at.x, at.y)
                    }
                    drawPath(path, colors[phase], style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                    points.forEachIndexed { i, point -> dot(Offset(x(i), y(point.phases[phase])), colors[phase], surface) }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.xs),
                horizontalArrangement = Arrangement.spacedBy(Spacing.lg, Alignment.CenterHorizontally)
            ) {
                listOf("R", "S", "T").forEachIndexed { i, name -> Legend(name, colors[i]) }
            }
        }
    }
}

private fun DrawScope.dot(at: Offset, color: Color, ring: Color) {
    drawCircle(ring, radius = 5.5.dp.toPx(), center = at)
    drawCircle(color, radius = 4.dp.toPx(), center = at)
}

/** A step of 1, 2 or 5 × 10ⁿ that puts four or five lines on the axis. */
private fun niceStep(max: Double): Double {
    val rough = max / 4
    var magnitude = 1.0
    while (magnitude * 10 <= rough) magnitude *= 10
    return listOf(1.0, 2.0, 5.0, 10.0).map { it * magnitude }.first { it >= rough }
}

@Composable
private fun Legend(name: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(modifier = Modifier.size(10.dp).background(color, CircleShape))
        Text(name, style = MaterialTheme.typography.labelMedium)
    }
}
