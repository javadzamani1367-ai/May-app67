package ir.minerfinder.ui.spectrum

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
import ir.minerfinder.core.acoustic.AcousticFrame
import ir.minerfinder.ui.common.Fa
import ir.minerfinder.ui.theme.Palette

/** Frequency axis drawn left to right (as on every instrument), even in the RTL app. */
const val CHART_MIN_HZ = 100.0
const val CHART_MAX_HZ = 5000.0

/**
 * Live spectrum: this frame's raw spectrum (grey), the cleaned average the
 * detector used (teal), the noise floor (dashed level) and the harmonic
 * series found (red marks with their order).
 */
@Composable
fun SpectrumChart(frame: AcousticFrame?, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 10.sp, color = Palette.Muted)
    Canvas(modifier.fillMaxWidth().height(220.dp)) {
        drawRect(Color(0xFF0F1A26))
        val w = size.width
        val h = size.height - 16.dp.toPx()
        fun x(f: Double) = ((f - CHART_MIN_HZ) / (CHART_MAX_HZ - CHART_MIN_HZ) * w).toFloat()

        for (k in 1..5) {
            val gx = x(k * 1000.0)
            drawLine(Color(0x33FFFFFF), Offset(gx, 0f), Offset(gx, h))
            val label = measurer.measure(Fa.int(k) + "k", labelStyle)
            drawText(label, topLeft = Offset(gx - label.size.width - 2f, h + 2f))
        }
        if (frame == null) return@Canvas

        val bottomDb = frame.floorDb - 15
        val topDb = frame.floorDb + 65
        fun y(db: Double) = (h * (1 - (db - bottomDb) / (topDb - bottomDb))).toFloat().coerceIn(0f, h)

        fun line(values: FloatArray, color: Color, width: Float) {
            val path = Path()
            for (i in values.indices) {
                val px = x(frame.firstHz + i * frame.binHz)
                val py = y(values[i].toDouble())
                if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            drawPath(path, color, style = Stroke(width))
        }
        line(frame.rawDb, Color(0x88B0BEC5), 1f)
        line(frame.cleanDb, Palette.Teal, 2f)
        val fy = y(frame.floorDb)
        drawLine(Color(0x99FFC107), Offset(0f, fy), Offset(w, fy), strokeWidth = 1.5f)

        frame.series?.partials?.forEach { p ->
            val px = x(p.peak.freqHz)
            val py = y(p.peak.levelDb)
            drawLine(Palette.Hot, Offset(px, py - 14f), Offset(px, h), strokeWidth = 2f)
            val label = measurer.measure(Fa.int(p.order), TextStyle(fontSize = 11.sp, color = Color.White))
            drawText(label, topLeft = Offset(px - label.size.width / 2f, (py - 30f).coerceAtLeast(0f)))
        }
    }
}

