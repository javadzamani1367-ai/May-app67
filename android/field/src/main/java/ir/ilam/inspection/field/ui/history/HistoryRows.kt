package ir.ilam.inspection.field.ui.history

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.ilam.inspection.field.R
import ir.ilam.inspection.field.data.History
import ir.ilam.inspection.field.data.HistoryRow
import ir.ilam.inspection.field.sync.HistoryCache
import ir.ilam.inspection.ui.common.PhaseLines
import ir.ilam.inspection.ui.common.phaseColors
import ir.ilam.inspection.ui.common.StatusBadge
import ir.ilam.inspection.ui.theme.Spacing
import ir.ilam.inspection.ui.theme.Tavan
import ir.ilam.inspection.ui.theme.Tone
import ir.ilam.inspection.util.PersianDate
import ir.ilam.inspection.util.PersianNumbers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.roundToInt

private val PHASES = listOf("R", "S", "T")

/** The three phases over time; oldest on the right, as the rows below read right to left. */
@Composable
fun PhaseChart(rows: List<HistoryRow>, modifier: Modifier = Modifier) {
    PhaseLines(rows.reversed().map { listOf(it.r, it.s, it.t) }, modifier)
}

/**
 * One reading: when, and each phase with its change from the reading before it.
 * A rise beyond the manager's limit — against the last reading or the average of
 * the last five — is marked, here as on the server.
 */
@Composable
fun AmpRow(row: HistoryRow, earlier: List<HistoryRow>, risePct: Int, riseMinA: Int) {
    val previous = earlier.firstOrNull()
    val rises = (0..2).map { p -> History.isRise(row.phase(p), earlier.mapNotNull { it.phase(p) }, risePct, riseMinA) }
    val abnormal = row.anomaly || rises.any { it }
    Column(
        modifier = Modifier.fillMaxWidth()
            .background(if (abnormal) Tavan.colors.danger.container else MaterialTheme.colorScheme.surface)
            .padding(vertical = Spacing.sm, horizontal = Spacing.xs)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(if (row.current) stringResource(R.string.history_this_visit) else PersianDate.formatWithTime(row.at),
                style = MaterialTheme.typography.labelLarge, fontWeight = if (row.current) FontWeight.Bold else null,
                modifier = Modifier.weight(1f))
            if (abnormal) StatusBadge(stringResource(R.string.history_abnormal), Tone.DANGER)
        }
        Row(modifier = Modifier.fillMaxWidth().padding(top = 2.dp)) {
            for (p in 0..2) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(PHASES[p] + ": " + amps(row.phase(p)), style = MaterialTheme.typography.bodyLarge,
                        color = phaseColors()[p], fontWeight = FontWeight.Medium)
                    History.changePct(row.phase(p), previous?.phase(p))?.let { pct ->
                        Text(stringResource(R.string.history_change, signed(pct)), style = MaterialTheme.typography.bodySmall,
                            color = if (rises[p]) Tavan.colors.danger.strong else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        row.n?.let { Text(stringResource(R.string.history_neutral, amps(it)), style = MaterialTheme.typography.bodySmall) }
    }
    HorizontalDivider()
}

/** A thermal reading: its temperatures, and its picture when none were typed. */
@Composable
fun ThermalRow(row: HistoryRow, cache: HistoryCache) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.sm)) {
        Text(PersianDate.formatWithTime(row.at), style = MaterialTheme.typography.labelLarge)
        if (row.hasTemperatures) {
            Text(stringResource(R.string.history_temps, degrees(row.tempMax), degrees(row.tempBoxMax), degrees(row.tempBoxAvg)),
                style = MaterialTheme.typography.bodyLarge)
        }
        if (!row.hasTemperatures) row.fileId?.let { id ->
            val picture by produceState<ImageBitmap?>(null, id) { value = cache.image(id)?.let { decode(it) } }
            picture?.let {
                Image(it, contentDescription = null, contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().height(200.dp).padding(top = Spacing.xs))
            } ?: Text(stringResource(R.string.history_no_picture), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    HorizontalDivider()
}

private suspend fun decode(file: java.io.File): ImageBitmap? = withContext(Dispatchers.IO) {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    var sample = 1
    while (bounds.outWidth / sample > MAX_SIDE * 2) sample *= 2
    BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
}

private fun amps(v: Double?): String = v?.let { PersianNumbers.toPersian(trim(it)) } ?: "—"

private fun degrees(v: Double?): String = v?.let { PersianNumbers.toPersian(trim(it)) + "°" } ?: "—"

private fun signed(pct: Double): String = PersianNumbers.toPersian((if (pct > 0) "+" else "") + pct.roundToInt())

private fun trim(v: Double): String = if (v % 1.0 == 0.0) v.toLong().toString() else String.format(Locale.US, "%.1f", v)

private const val MAX_SIDE = 1024
