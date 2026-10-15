package ir.ilam.inspection.ui.location

import ir.ilam.inspection.core.R
import ir.ilam.inspection.ui.theme.Tone
import ir.ilam.inspection.util.PersianNumbers
import java.util.Locale

/** How good an accuracy figure is, in words and a colour. */
data class AccuracyQuality(val label: Int, val tone: Tone)

fun accuracyQuality(metres: Double?): AccuracyQuality = when {
    metres == null -> AccuracyQuality(R.string.location_quality_unknown, Tone.NEUTRAL)
    metres <= 5.0 -> AccuracyQuality(R.string.location_quality_excellent, Tone.SUCCESS)
    metres <= 12.0 -> AccuracyQuality(R.string.location_quality_good, Tone.ACCENT)
    metres <= 25.0 -> AccuracyQuality(R.string.location_quality_fair, Tone.WARNING)
    else -> AccuracyQuality(R.string.location_quality_poor, Tone.DANGER)
}

/** Whole metres above ten, one decimal below — "±۴٫۲" but "±۱۸". */
fun formatMetres(metres: Double): String = PersianNumbers.toPersian(
    if (metres < 10) String.format(Locale.US, "%.1f", metres) else metres.toInt().toString()
)

fun formatCoordinates(latitude: Double, longitude: Double): String =
    PersianNumbers.toPersian(String.format(Locale.US, "%.6f , %.6f", latitude, longitude))
