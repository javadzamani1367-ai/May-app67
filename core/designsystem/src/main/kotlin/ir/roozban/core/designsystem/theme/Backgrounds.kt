package ir.roozban.core.designsystem.theme

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.cos
import kotlin.math.sin

/** A built-in background: a gradient with an optional pattern drawn on top. */
data class BackgroundPreset(val id: String, val name: String, val colors: List<Color>, val pattern: Pattern = Pattern.NONE, val dark: Boolean = false) {
    enum class Pattern { NONE, GIRIH, WAVES, DOTS }
}

object Backgrounds {
    val presets = listOf(
        BackgroundPreset("dawn", "سپیده‌دم", listOf(Color(0xFFFFE0B2), Color(0xFFF8BBD0), Color(0xFFD1C4E9))),
        BackgroundPreset("sky", "آسمان", listOf(Color(0xFFE3F2FD), Color(0xFFBBDEFB), Color(0xFF90CAF9)), Pattern.WAVES),
        BackgroundPreset("lavender", "یاس", listOf(Color(0xFFF3E5F5), Color(0xFFE1BEE7), Color(0xFFD1C4E9)), Pattern.DOTS),
        BackgroundPreset("sunset", "غروب", listOf(Color(0xFFFF8A65), Color(0xFFF06292), Color(0xFF7E57C2))),
        BackgroundPreset("turquoise", "کاشی فیروزه", listOf(Color(0xFF0D47A1), Color(0xFF1565C0), Color(0xFF00838F)), Pattern.GIRIH, dark = true),
        BackgroundPreset("rose", "گل‌سرخ", listOf(Color(0xFFFFEBEE), Color(0xFFFFCDD2), Color(0xFFF8BBD0)), Pattern.GIRIH),
        BackgroundPreset("sand", "کویر", listOf(Color(0xFFFFF8E1), Color(0xFFFFE0B2), Color(0xFFFFCC80)), Pattern.WAVES),
        BackgroundPreset("night", "شب پرستاره", listOf(Color(0xFF0D1B2A), Color(0xFF1B263B), Color(0xFF3A2E5C)), Pattern.DOTS, dark = true),
    )

    fun preset(id: String?): BackgroundPreset? =
        id?.takeIf { it.startsWith(PRESET_PREFIX) }?.removePrefix(PRESET_PREFIX)?.let { key -> presets.firstOrNull { it.id == key } }

    const val PRESET_PREFIX = "preset:"

    /** Where a picture chosen from the gallery is kept (inside the app's private files). */
    fun imageFile(filesDir: File): File = File(filesDir, "backgrounds/custom.jpg")
}

/**
 * Draws the chosen background behind the whole app, veiled with the surface color so text stays
 * black-on-white (or white-on-dark) and readable. [veil] 0..1 is the veil's opacity.
 */
@Composable
fun AppBackdrop(background: String?, imageFile: File?, veil: Float, veilColor: Color, modifier: Modifier = Modifier) {
    val preset = Backgrounds.preset(background)
    Box(modifier.fillMaxSize().background(veilColor)) {
        when {
            preset != null -> PresetCanvas(preset, Modifier.fillMaxSize())
            imageFile != null -> {
                val bitmap by produceState<ImageBitmap?>(null, imageFile.path, imageFile.lastModified()) {
                    value = withContext(Dispatchers.IO) { decode(imageFile) }
                }
                bitmap?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
            }
        }
        if (preset != null || imageFile != null) {
            Box(Modifier.fillMaxSize().background(veilColor.copy(alpha = veil.coerceIn(0f, 1f))))
        }
    }
}

/** A small preview tile for the picker. */
@Composable
fun BackgroundPreview(preset: BackgroundPreset, modifier: Modifier) = PresetCanvas(preset, modifier)

@Composable
private fun PresetCanvas(preset: BackgroundPreset, modifier: Modifier) {
    Canvas(modifier) {
        drawRect(Brush.linearGradient(preset.colors, start = Offset.Zero, end = Offset(size.width, size.height)))
        val ink = if (preset.dark) Color.White.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.35f)
        when (preset.pattern) {
            BackgroundPreset.Pattern.GIRIH -> girih(ink)
            BackgroundPreset.Pattern.WAVES -> waves(ink)
            BackgroundPreset.Pattern.DOTS -> dots(ink)
            BackgroundPreset.Pattern.NONE -> Unit
        }
    }
}

/** Eight-pointed stars on a grid, as in Persian tile work. */
private fun DrawScope.girih(ink: Color) {
    val step = 56.dp.toPx()
    val r = step * 0.32f
    val stroke = Stroke(1.2f.dp.toPx())
    var y = 0f
    var row = 0
    while (y < size.height + step) {
        var x = if (row % 2 == 0) 0f else step / 2
        while (x < size.width + step) {
            val star = Path()
            for (i in 0 until 16) {
                val angle = Math.PI * i / 8
                val radius = if (i % 2 == 0) r else r * 0.62f
                val px = x + (radius * cos(angle)).toFloat()
                val py = y + (radius * sin(angle)).toFloat()
                if (i == 0) star.moveTo(px, py) else star.lineTo(px, py)
            }
            star.close()
            drawPath(star, ink, style = stroke)
            rotate(22.5f, Offset(x, y)) { drawCircle(ink, r * 0.25f, Offset(x, y), style = stroke) }
            x += step
        }
        y += step * 0.866f
        row++
    }
}

private fun DrawScope.waves(ink: Color) {
    val gap = 28.dp.toPx()
    val amp = 6.dp.toPx()
    var y = gap
    while (y < size.height) {
        val path = Path().apply {
            moveTo(0f, y)
            var x = 0f
            while (x < size.width) {
                quadraticTo(x + gap / 2, y - amp, x + gap, y)
                quadraticTo(x + gap * 1.5f, y + amp, x + gap * 2, y)
                x += gap * 2
            }
        }
        drawPath(path, ink, style = Stroke(1.5f.dp.toPx()))
        y += gap
    }
}

private fun DrawScope.dots(ink: Color) {
    val step = 22.dp.toPx()
    var y = step / 2
    var row = 0
    while (y < size.height) {
        var x = if (row % 2 == 0) step / 2 else step
        while (x < size.width) {
            drawCircle(ink, 1.6f.dp.toPx() * if ((x.toInt() + y.toInt()) % 3 == 0) 1.6f else 1f, Offset(x, y))
            x += step
        }
        y += step
        row++
    }
}

private fun decode(file: File): ImageBitmap? {
    if (!file.exists()) return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    var sample = 1
    while (bounds.outWidth / sample > 1600 || bounds.outHeight / sample > 1600) sample *= 2
    return BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
}
