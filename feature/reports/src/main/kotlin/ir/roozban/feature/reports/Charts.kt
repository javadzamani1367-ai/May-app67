package ir.roozban.feature.reports

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * Vertical bars, first value at the *start* edge: on the right in Persian, like the calendar.
 * [labels] are drawn under the bars (empty strings skip a label).
 */
@Composable
internal fun BarChart(
    values: List<Int>,
    labels: List<String>,
    color: Color,
    modifier: Modifier = Modifier,
    height: Dp = 140.dp,
    highlight: Int? = null,
    description: String = "",
) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val max = (values.maxOrNull() ?: 0).coerceAtLeast(1)
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val accent = MaterialTheme.colorScheme.tertiary
    Column(modifier.semantics { contentDescription = description }) {
        Canvas(Modifier.fillMaxWidth().height(height)) {
            val n = values.size.coerceAtLeast(1)
            val slot = size.width / n
            val barWidth = (slot * 0.6f).coerceAtMost(28.dp.toPx())
            val radius = CornerRadius(barWidth / 3, barWidth / 3)
            values.forEachIndexed { i, v ->
                val index = if (rtl) n - 1 - i else i
                val left = index * slot + (slot - barWidth) / 2
                drawRoundRect(track, Offset(left, 0f), Size(barWidth, size.height), radius)
                if (v > 0) {
                    val h = size.height * v / max
                    drawRoundRect(if (i == highlight) accent else color, Offset(left, size.height - h), Size(barWidth, h), radius)
                }
            }
        }
        if (labels.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            // Row follows the layout direction, so label i sits under bar i.
            Row(Modifier.fillMaxWidth()) {
                labels.forEach { label ->
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}

data class DonutSlice(val value: Float, val color: Color, val label: String, val valueLabel: String)

/** A ring of slices with a legend beside it. */
@Composable
internal fun DonutChart(slices: List<DonutSlice>, center: String, modifier: Modifier = Modifier, size: Dp = 132.dp) {
    val total = slices.sumOf { it.value.toDouble() }.toFloat().coerceAtLeast(0.0001f)
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(size), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(size)) {
                val stroke = 18.dp.toPx()
                val arc = Size(this.size.width - stroke, this.size.height - stroke)
                val topLeft = Offset(stroke / 2, stroke / 2)
                drawArc(track, 0f, 360f, false, topLeft, arc, style = Stroke(stroke))
                var start = -90f
                slices.forEach { s ->
                    val sweep = 360f * s.value / total
                    drawArc(s.color, start, (sweep - 1.5f).coerceAtLeast(0.5f), false, topLeft, arc, style = Stroke(stroke))
                    start += sweep
                }
            }
            Text(center, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.width(16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            slices.forEach { s ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Canvas(Modifier.size(10.dp)) { drawCircle(s.color) }
                    Spacer(Modifier.width(6.dp))
                    Text(s.label, style = MaterialTheme.typography.bodySmall, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                    Spacer(Modifier.width(6.dp))
                    Text(s.valueLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
