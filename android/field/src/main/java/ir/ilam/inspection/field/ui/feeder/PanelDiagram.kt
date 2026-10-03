package ir.ilam.inspection.field.ui.feeder

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import ir.ilam.inspection.field.R
import ir.ilam.inspection.field.data.FeederState
import ir.ilam.inspection.ui.theme.Tavan

/**
 * The panel as the user stands in front of it: main switch on top, A front
 * right, B front left, and the two back feeders drawn above them, faded — C
 * behind B, D behind A. Laid out left to right on purpose: in a right-to-left
 * layout the boxes would be mirrored and "right" would be on the left.
 *
 * The box being filled in is highlighted; a tap jumps to that box's step.
 */
@Composable
fun PanelDiagram(step: FeederStep, states: List<FeederState?>, onPick: (FeederStep) -> Unit) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                stringResource(R.string.feeder_back_row),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Slot(FeederStep.C, R.string.feeder_c_short, step, states[2], back = true, onPick = onPick)
                Slot(FeederStep.D, R.string.feeder_d_short, step, states[3], back = true, onPick = onPick)
            }
            Row { Slot(FeederStep.MAIN, R.string.feeder_main_short, step, null, back = false, onPick = onPick) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Slot(FeederStep.B, R.string.feeder_b_short, step, states[1], back = false, onPick = onPick)
                Slot(FeederStep.A, R.string.feeder_a_short, step, states[0], back = false, onPick = onPick)
            }
            Text(
                stringResource(R.string.feeder_front_row),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun RowScope.Slot(
    target: FeederStep,
    label: Int,
    current: FeederStep,
    state: FeederState?,
    back: Boolean,
    onPick: (FeederStep) -> Unit
) {
    val active = target == current
    val tone = when {
        active -> Tavan.colors.accent
        state == FeederState.MEASURED -> Tavan.colors.success
        state == FeederState.NOT_MEASURED -> Tavan.colors.warning
        else -> Tavan.colors.neutral
    }
    Surface(
        onClick = { onPick(target) },
        color = if (active || state != null) tone.container else MaterialTheme.colorScheme.surface,
        border = BorderStroke(if (active) 3.dp else 1.dp, tone.strong.copy(alpha = if (back && !active) 0.5f else 1f)),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.weight(1f).height(if (back) 54.dp else 64.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                stringResource(label),
                style = MaterialTheme.typography.titleSmall,
                color = tone.onContainer.copy(alpha = if (back && !active) 0.7f else 1f),
                textAlign = TextAlign.Center
            )
        }
    }
}
