package ir.roozban.learning

import ir.roozban.core.calendar.PersianDigits
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ln

/** A fact the app worked out itself; [key] identifies it so each night replaces the last. */
data class Insight(val key: String, val text: String, val confidence: Double)

/** Plain-Persian sentences about what the statistics show, only when there is enough data. */
object Insights {
    private const val MIN_ESTIMATES = 5
    private const val MIN_ACTIVITY_MINUTES = 300.0

    fun from(
        factors: EstimateFactors,
        hours: ProductiveHours,
        categoryLate: Map<String, Double>,
        overallLate: Double?,
        categoryNames: Map<String, String>,
    ): List<Insight> = buildList {
        hours.bestWindow(2)?.let { (start, share) ->
            if (hours.total >= MIN_ACTIVITY_MINUTES && share >= 0.2) {
                add(Insight("hours", "بیشتر کارهایت را بین ساعت ${fa(start)} و ${fa(start + 2)} انجام می‌دهی.", share.coerceAtMost(1.0)))
            }
        }
        estimateText(factors.global.value)?.let { if (factors.global.samples >= MIN_ESTIMATES) add(Insight("estimate", "کارهایت معمولاً $it.", confidence(factors.global.samples))) }
        factors.byCategory.forEach { (id, f) ->
            val name = categoryNames[id] ?: return@forEach
            if (f.samples >= MIN_ESTIMATES && abs(ln(f.value) - ln(factors.global.value)) > 0.2) {
                estimateText(f.value)?.let { add(Insight("estimate:$id", "کارهای «$name» معمولاً $it.", confidence(f.samples))) }
            }
        }
        if (overallLate != null) {
            categoryLate.forEach { (id, rate) ->
                val name = categoryNames[id] ?: return@forEach
                if (rate >= overallLate + 0.15 && rate >= 0.35) {
                    add(Insight("late:$id", "کارهای «$name» بیشتر از بقیه از موعدشان عقب می‌افتند.", rate))
                }
            }
        }
    }

    /** «۱٫۵ برابر تخمینت طول می‌کشند» or null when estimates are about right. */
    private fun estimateText(f: Double): String? = when {
        f >= 1.15 -> "${ratio(f)} برابر تخمینت طول می‌کشند"
        f <= 0.87 -> "زودتر از تخمینت تمام می‌شوند (حدود ${ratio(f)} برابر)"
        else -> null
    }

    private fun ratio(f: Double) = PersianDigits.toPersian(String.format(Locale.US, "%.1f", f)).replace('.', '٫')

    private fun fa(h: Int) = PersianDigits.format(h % 24)

    private fun confidence(samples: Int) = (samples / 20.0).coerceIn(0.2, 1.0)
}
