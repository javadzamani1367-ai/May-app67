package ir.minerfinder.ui.spectrum

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import ir.minerfinder.core.acoustic.AcousticFrame

/**
 * Scrolling spectrogram: newest frame on top, frequency left to right, level
 * as colour relative to the frame's noise floor. Kept as a small bitmap that
 * is shifted one row per frame; touched only on the main thread.
 */
class SpectrogramBuffer(val columns: Int = 240, val rows: Int = 180) {
    private val pixels = IntArray(columns * rows) { PALETTE[0] }
    private val bitmap: Bitmap = Bitmap.createBitmap(columns, rows, Bitmap.Config.ARGB_8888)
    val image: ImageBitmap = bitmap.asImageBitmap()

    /** Bumped on every update so the canvas redraws. */
    var version by mutableIntStateOf(0)
        private set

    fun push(frame: AcousticFrame) {
        System.arraycopy(pixels, 0, pixels, columns, columns * (rows - 1))
        val span = CHART_MAX_HZ - CHART_MIN_HZ
        for (c in 0 until columns) {
            val f0 = CHART_MIN_HZ + span * c / columns
            val f1 = CHART_MIN_HZ + span * (c + 1) / columns
            val b0 = ((f0 - frame.firstHz) / frame.binHz).toInt().coerceIn(0, frame.rawDb.size - 1)
            val b1 = ((f1 - frame.firstHz) / frame.binHz).toInt().coerceIn(b0, frame.rawDb.size - 1)
            var max = -1000f
            for (b in b0..b1) if (frame.rawDb[b] > max) max = frame.rawDb[b]
            // 0 dB above the floor is black, 45 dB above it is the top of the scale.
            val t = ((max - frame.floorDb) / 45.0).coerceIn(0.0, 1.0)
            pixels[c] = PALETTE[(t * (PALETTE.size - 1)).toInt()]
        }
        bitmap.setPixels(pixels, 0, columns, 0, 0, columns, rows)
        version++
    }

    fun clear() {
        pixels.fill(PALETTE[0])
        bitmap.setPixels(pixels, 0, columns, 0, 0, columns, rows)
        version++
    }

    companion object {
        /** Black → blue → cyan → yellow → red. */
        val PALETTE = IntArray(256) { i ->
            val t = i / 255.0
            val stops = arrayOf(
                doubleArrayOf(0.0, 0.0, 0.0), doubleArrayOf(0.1, 0.1, 0.6), doubleArrayOf(0.0, 0.7, 0.9),
                doubleArrayOf(1.0, 0.9, 0.1), doubleArrayOf(0.95, 0.15, 0.1),
            )
            val pos = t * (stops.size - 1)
            val k = pos.toInt().coerceAtMost(stops.size - 2)
            val f = pos - k
            val c = DoubleArray(3) { stops[k][it] + (stops[k + 1][it] - stops[k][it]) * f }
            (0xFF shl 24) or ((c[0] * 255).toInt() shl 16) or ((c[1] * 255).toInt() shl 8) or (c[2] * 255).toInt()
        }
    }
}

@Composable
fun Spectrogram(buffer: SpectrogramBuffer, modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxWidth().height(200.dp)) {
        @Suppress("UNUSED_VARIABLE") val v = buffer.version
        drawImage(
            buffer.image,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(buffer.columns, buffer.rows),
            dstOffset = IntOffset.Zero,
            dstSize = IntSize(size.width.toInt(), size.height.toInt()),
        )
    }
}
