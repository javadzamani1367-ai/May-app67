package ir.minerfinder.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.minerfinder.ui.theme.Palette

/** A small history chart, oldest on the left. The range grows to fit the data and never below [minSpan]. */
@Composable
fun LineChart(values: List<Float>, modifier: Modifier = Modifier, color: Color = Palette.Teal, minSpan: Float = 1f) {
    val measurer = rememberTextMeasurer()
    Canvas(modifier.fillMaxWidth().height(140.dp)) {
        drawRect(Color(0xFF0F1A26))
        if (values.size < 2) return@Canvas
        val lo = values.min()
        val hi = maxOf(values.max(), lo + minSpan)
        fun y(v: Float) = size.height * (1 - (v - lo) / (hi - lo)) * 0.9f + size.height * 0.05f
        val path = Path()
        values.forEachIndexed { i, v ->
            val x = size.width * i / (values.size - 1)
            if (i == 0) path.moveTo(x, y(v)) else path.lineTo(x, y(v))
        }
        drawPath(path, color, style = Stroke(2.dp.toPx()))
        val style = TextStyle(fontSize = 10.sp, color = Color(0xFFB0BEC5))
        drawText(measurer.measure(Fa.num(hi, 1), style), topLeft = Offset(4f, 2f))
        val low = measurer.measure(Fa.num(lo, 1), style)
        drawText(low, topLeft = Offset(4f, size.height - low.size.height - 2f))
    }
}
