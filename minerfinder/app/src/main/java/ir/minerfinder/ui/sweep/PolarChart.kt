package ir.minerfinder.ui.sweep

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.minerfinder.R
import ir.minerfinder.ui.theme.Palette
import kotlin.math.cos
import kotlin.math.sin

/**
 * North-up polar plot of the sweep: one spoke per bin, length by its mean
 * score (scaled between the circle's min and max), the current heading as a
 * grey needle and the estimated bearing as a red arrow.
 */
@Composable
fun PolarChart(bins: DoubleArray, heading: Double?, bearing: Double?, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val names = listOf(
        stringResource(R.string.north), stringResource(R.string.east),
        stringResource(R.string.south), stringResource(R.string.west),
    )
    Canvas(modifier.fillMaxWidth().aspectRatio(1f)) {
        val c = Offset(size.width / 2, size.height / 2)
        val r = size.minDimension / 2 - 28.dp.toPx()
        fun at(deg: Double, radius: Float): Offset {
            val a = Math.toRadians(deg)
            return Offset(c.x + radius * sin(a).toFloat(), c.y - radius * cos(a).toFloat())
        }
        drawCircle(Color(0xFF0F1A26), r + 22.dp.toPx(), c)
        for (k in 1..4) drawCircle(Color(0x33FFFFFF), r * k / 4, c, style = Stroke(1f))
        for (k in 0 until 4) {
            drawLine(Color(0x33FFFFFF), c, at(k * 90.0, r))
            val label = measurer.measure(names[k], TextStyle(fontSize = 12.sp, color = Color.White))
            val p = at(k * 90.0, r + 12.dp.toPx())
            drawText(label, topLeft = Offset(p.x - label.size.width / 2, p.y - label.size.height / 2))
        }

        val filled = bins.filter { !it.isNaN() }
        if (filled.isNotEmpty()) {
            val lo = filled.min()
            val hi = maxOf(filled.max(), lo + 1e-6)
            val step = 360.0 / bins.size
            val path = Path()
            var started = false
            for (i in bins.indices) {
                val v = bins[i]
                if (v.isNaN()) continue
                val len = (0.15f + 0.85f * ((v - lo) / (hi - lo)).toFloat()) * r
                val p = at((i + 0.5) * step, len)
                drawLine(Palette.Teal.copy(alpha = 0.5f), c, p, strokeWidth = 3.dp.toPx())
                if (!started) { path.moveTo(p.x, p.y); started = true } else path.lineTo(p.x, p.y)
            }
            drawPath(path, Palette.Teal, style = Stroke(2.dp.toPx()))
        }
        heading?.let { drawLine(Color(0xFFB0BEC5), c, at(it, r), strokeWidth = 3.dp.toPx()) }
        bearing?.let {
            val tip = at(it, r)
            drawLine(Palette.Hot, c, tip, strokeWidth = 5.dp.toPx())
            drawCircle(Palette.Hot, 7.dp.toPx(), tip)
        }
        drawCircle(Color.White, 4.dp.toPx(), c)
    }
}
