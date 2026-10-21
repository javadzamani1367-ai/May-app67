package ir.ilam.inspection.ui.history

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.ilam.inspection.R
import ir.ilam.inspection.sync.Readings
import ir.ilam.inspection.ui.common.PhaseLines
import ir.ilam.inspection.ui.common.StatusBadge
import ir.ilam.inspection.ui.common.phaseColors
import ir.ilam.inspection.ui.theme.Spacing
import ir.ilam.inspection.ui.theme.Tavan
import ir.ilam.inspection.ui.theme.Tone
import ir.ilam.inspection.util.PersianDate
import ir.ilam.inspection.util.PersianNumbers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt

private val PHASES = listOf("R", "S", "T")

/** A series, newest first: the chart (amps only), then every reading. */
@Composable
fun ReadingList(rows: List<Readings.Row>, part: String, image: suspend (String) -> File?) {
    if (part != Readings.THERMAL) PhaseLines(rows.reversed().map { listOf(it.r, it.s, it.t) }, Modifier.padding(vertical = Spacing.sm))
    rows.forEachIndexed { i, row ->
        if (part == Readings.THERMAL) ThermalReading(row, image) else AmpReading(row, rows.getOrNull(i + 1))
    }
}

/** One reading: when and by whom, the three phases and each one's change from the reading before. */
@Composable
fun AmpReading(row: Readings.Row, previous: Readings.Row?) {
    Column(
        modifier = Modifier.fillMaxWidth()
            .background(if (row.anomaly) Tavan.colors.danger.container else MaterialTheme.colorScheme.surface)
            .padding(vertical = Spacing.sm, horizontal = Spacing.xs)
    ) {
        Header(row)
        Row(modifier = Modifier.fillMaxWidth().padding(top = 2.dp)) {
            for (p in 0..2) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(PHASES[p] + ": " + number(row.phase(p)), style = MaterialTheme.typography.bodyLarge,
                        color = phaseColors()[p], fontWeight = FontWeight.Medium)
                    change(row.phase(p), previous?.phase(p))?.let {
                        Text(stringResource(R.string.hist_change, it), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        row.n?.let { Text(stringResource(R.string.hist_neutral, number(it)), style = MaterialTheme.typography.bodySmall) }
        row.anomalyNote?.let { Text(PersianNumbers.toPersian(it), style = MaterialTheme.typography.bodySmall, color = Tavan.colors.danger.strong) }
    }
    HorizontalDivider()
}

/** A thermal reading: its temperatures and, from the server, its picture. */
@Composable
fun ThermalReading(row: Readings.Row, image: suspend (String) -> File?) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.sm)) {
        Header(row)
        Text(stringResource(R.string.hist_temps, degrees(row.tempMax), degrees(row.tempBoxMax), degrees(row.tempBoxAvg)),
            style = MaterialTheme.typography.bodyLarge)
        row.fileId?.let { id ->
            val picture by produceState<ImageBitmap?>(null, id) { value = image(id)?.let { decode(it) } }
            picture?.let {
                Image(it, contentDescription = null, contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().height(200.dp).padding(top = Spacing.xs))
            }
        }
    }
    HorizontalDivider()
}

@Composable
private fun Header(row: Readings.Row) {
    Row {
        Column(modifier = Modifier.weight(1f)) {
            Text(PersianDate.formatWithTime(row.at), style = MaterialTheme.typography.labelLarge)
            Text(listOfNotNull(row.who, row.trackingCode).joinToString(" · "), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (row.anomaly) StatusBadge(stringResource(R.string.hist_abnormal), Tone.DANGER)
    }
}

private suspend fun decode(file: File): ImageBitmap? = withContext(Dispatchers.IO) {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    var sample = 1
    while (bounds.outWidth / sample > 2048) sample *= 2
    BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
}

private fun change(now: Double?, before: Double?): String? {
    if (now == null || before == null || before <= 0.0) return null
    val pct = ((now - before) / before * 100).roundToInt()
    return PersianNumbers.toPersian((if (pct > 0) "+" else "") + pct)
}

fun number(v: Double?): String = v?.let {
    PersianNumbers.toPersian(if (it % 1.0 == 0.0) it.toLong().toString() else String.format(Locale.US, "%.1f", it))
} ?: "—"

private fun degrees(v: Double?): String = if (v == null) "—" else number(v) + "°"
