package ir.ilam.inspection.ui.visit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ir.ilam.inspection.R
import ir.ilam.inspection.data.model.ReportDetail
import ir.ilam.inspection.data.model.TechnicalInput
import ir.ilam.inspection.ui.theme.Tavan
import ir.ilam.inspection.util.PersianNumbers

/**
 * The visit, as the order the work happens in on site: find and fix the
 * position, inspect the supply and the meter, record what was found, document
 * it, complete who owns the place, and check everything before closing.
 */
enum class VisitStep(val title: Int, val short: Int) {
    LOCATION(R.string.step_location, R.string.step_short_location),
    TECHNICAL(R.string.step_technical, R.string.step_short_technical),
    DEVICES(R.string.step_devices, R.string.step_short_devices),
    MEDIA(R.string.step_media, R.string.step_short_media),
    OWNER(R.string.step_owner, R.string.step_short_owner),
    REVIEW(R.string.step_review, R.string.step_short_review)
}

/** Where a step stands, drawn on its marker. */
enum class StepMark { UPCOMING, DONE, NEEDS_WORK }

/**
 * Worked out from the case itself, never stored: a step is done when what it
 * collects is there, and "needs work" only for what closing the visit
 * actually requires — a visit that found no miners is not incomplete.
 */
fun stepMark(step: VisitStep, detail: ReportDetail): StepMark {
    val report = detail.report
    return when (step) {
        VisitStep.LOCATION ->
            if (report.latitude != null && report.longitude != null && !report.county.isNullOrBlank()) StepMark.DONE
            else StepMark.NEEDS_WORK
        VisitStep.TECHNICAL ->
            if (TechnicalInput.from(report).power().hasReading) StepMark.DONE else StepMark.NEEDS_WORK
        VisitStep.DEVICES ->
            if (detail.devices.isNotEmpty() || detail.attendees.isNotEmpty()) StepMark.DONE else StepMark.UPCOMING
        VisitStep.MEDIA ->
            if (detail.photos.isNotEmpty() && !report.description.isNullOrBlank()) StepMark.DONE else StepMark.NEEDS_WORK
        VisitStep.OWNER ->
            if (!report.ownerName.isNullOrBlank()) StepMark.DONE else StepMark.UPCOMING
        VisitStep.REVIEW -> StepMark.UPCOMING
    }
}

/**
 * The workflow across the top of the visit, on the navy header: every step,
 * which one this is, which are finished and which still need something. A
 * step is one tap away from any other.
 */
@Composable
fun WorkflowStepper(current: Int, detail: ReportDetail, onStep: (Int) -> Unit) {
    val colors = Tavan.colors
    val scroll = rememberScrollState()
    val density = LocalDensity.current
    // Keep the current step in view on a narrow phone.
    LaunchedEffect(current) {
        with(density) { scroll.animateScrollTo((current * STEP_WIDTH.toPx()).toInt().coerceAtMost(scroll.maxValue)) }
    }
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(scroll).padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top
    ) {
        VisitStep.entries.forEachIndexed { index, step ->
            val mark = if (index == current) null else stepMark(step, detail)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(STEP_WIDTH).clickable { onStep(index) }
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Connector(visible = index > 0, lit = index <= current, modifier = Modifier.weight(1f))
                    Marker(index = index, current = index == current, mark = mark)
                    Connector(visible = index < VisitStep.entries.size - 1, lit = index < current, modifier = Modifier.weight(1f))
                }
                Text(
                    text = stringResource(step.short),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (index == current) colors.onHeader else colors.onHeaderMuted,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.padding(top = 5.dp)
                )
            }
        }
    }
}

@Composable
private fun Marker(index: Int, current: Boolean, mark: StepMark?) {
    val colors = Tavan.colors
    val accent = colors.accent.strong
    val size = if (current) 34.dp else 28.dp
    val (fill, content) = when {
        current -> accent to Color.White
        mark == StepMark.DONE -> accent.copy(alpha = 0.9f) to Color.White
        mark == StepMark.NEEDS_WORK -> Color.Transparent to colors.warning.strong
        else -> Color.Transparent to colors.onHeaderMuted
    }
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(fill)
            .border(
                width = if (current) 3.dp else 1.5.dp,
                color = when {
                    current -> Color.White.copy(alpha = 0.85f)
                    mark == StepMark.NEEDS_WORK -> colors.warning.strong
                    mark == StepMark.DONE -> accent
                    else -> colors.onHeaderMuted.copy(alpha = 0.6f)
                },
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        when {
            !current && mark == StepMark.DONE ->
                Icon(Icons.Filled.Check, contentDescription = null, tint = content, modifier = Modifier.size(16.dp))
            !current && mark == StepMark.NEEDS_WORK ->
                Icon(Icons.Filled.PriorityHigh, contentDescription = null, tint = content, modifier = Modifier.size(15.dp))
            else -> Text(
                PersianNumbers.toPersian(index + 1),
                style = MaterialTheme.typography.labelLarge,
                color = content
            )
        }
    }
}

@Composable
private fun Connector(visible: Boolean, lit: Boolean, modifier: Modifier) {
    Box(
        modifier = modifier
            .height(2.dp)
            .background(
                when {
                    !visible -> Color.Transparent
                    lit -> Tavan.colors.accent.strong
                    else -> Tavan.colors.onHeaderMuted.copy(alpha = 0.35f)
                }
            )
    )
}

private val STEP_WIDTH = 76.dp
