package ir.minerfinder.ui.common

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** The few icons the core icon set lacks, drawn as strokes on a 24 grid. */
object AppIcons {
    private fun stroke(name: String, block: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit) =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round, pathBuilder = block)
        }.build()

    /** Spectrum bars. */
    val Wave: ImageVector = stroke("wave") {
        moveTo(4f, 14f); lineTo(4f, 18f)
        moveTo(8f, 9f); lineTo(8f, 18f)
        moveTo(12f, 4f); lineTo(12f, 18f)
        moveTo(16f, 10f); lineTo(16f, 18f)
        moveTo(20f, 13f); lineTo(20f, 18f)
    }

    /** Compass needle in a circle: sweep / direction. */
    val Compass: ImageVector = stroke("compass") {
        moveTo(12f, 3f); arcToRelative(9f, 9f, 0f, true, true, 0f, 18f); arcToRelative(9f, 9f, 0f, true, true, 0f, -18f)
        moveTo(15f, 9f); lineTo(13f, 13f); lineTo(9f, 15f); lineTo(11f, 11f); close()
    }

    /** Folded map. */
    val Map: ImageVector = stroke("map") {
        moveTo(3f, 6f); lineTo(9f, 4f); lineTo(15f, 6f); lineTo(21f, 4f); lineTo(21f, 18f); lineTo(15f, 20f)
        lineTo(9f, 18f); lineTo(3f, 20f); close()
        moveTo(9f, 4f); lineTo(9f, 18f)
        moveTo(15f, 6f); lineTo(15f, 20f)
    }

    /** Stacked cards: library. */
    val Library: ImageVector = stroke("library") {
        moveTo(5f, 4f); lineTo(19f, 4f)
        moveTo(4f, 8f); lineTo(20f, 8f); lineTo(20f, 20f); lineTo(4f, 20f); close()
        moveTo(8f, 14f); lineTo(16f, 14f)
    }

    /** Horseshoe magnet. */
    val Magnet: ImageVector = stroke("magnet") {
        moveTo(6f, 4f); lineTo(6f, 12f); arcToRelative(6f, 6f, 0f, false, false, 12f, 0f); lineTo(18f, 4f)
        moveTo(6f, 8f); lineTo(9f, 8f)
        moveTo(15f, 8f); lineTo(18f, 8f)
    }
}
