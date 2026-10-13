package ir.ilam.inspection.field.ui.form

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.ilam.inspection.field.R
import ir.ilam.inspection.ui.common.AppCard
import ir.ilam.inspection.ui.common.StatusBadge
import ir.ilam.inspection.ui.common.ToneIcon
import ir.ilam.inspection.ui.theme.Tavan
import ir.ilam.inspection.ui.theme.Tone

/**
 * The tiles of a hub two to a row, side by side, so the whole form is seen at
 * once without scrolling past one wide card after another. Both tiles of a
 * row take the height of the taller one; an odd last tile keeps half the row.
 */
@Composable
fun SectionGrid(sections: List<HubSection>, onOpen: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        sections.chunked(2).forEach { pair ->
            Row(
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                pair.forEach { section ->
                    SectionTile(section, onClick = { onOpen(section.key) }, modifier = Modifier.weight(1f).fillMaxHeight())
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** One part of the form: a square-ish tile big enough for a gloved thumb, its state as a bar along the top. */
@Composable
fun SectionTile(section: HubSection, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val stripe = when (section.status) {
        SectionStatus.DONE -> Tavan.colors.success.strong
        SectionStatus.MISSING -> Tavan.colors.danger.strong
        SectionStatus.ATTENTION -> Tavan.colors.warning.strong
        SectionStatus.OPTIONAL -> MaterialTheme.colorScheme.outlineVariant
    }
    AppCard(modifier = modifier, onClick = onClick) {
        Column(modifier = Modifier.fillMaxWidth().fillMaxHeight().heightIn(min = 132.dp)) {
            Box(modifier = Modifier.fillMaxWidth().height(4.dp).background(stripe))
            Column(modifier = Modifier.fillMaxWidth().weight(1f).padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ToneIcon(icon = section.icon, tone = section.tone, size = 42.dp)
                    Spacer(Modifier.weight(1f))
                    StatusMark(section.status)
                }
                Text(
                    section.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 10.dp)
                )
                Text(
                    section.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun StatusMark(status: SectionStatus) {
    when (status) {
        SectionStatus.DONE -> Icon(Icons.Filled.CheckCircle, contentDescription = stringResource(R.string.hub_done),
            tint = Tavan.colors.success.strong, modifier = Modifier.size(24.dp))
        SectionStatus.MISSING -> StatusBadge(stringResource(R.string.hub_required), Tone.DANGER)
        SectionStatus.ATTENTION -> StatusBadge(stringResource(R.string.hub_attention), Tone.WARNING)
        SectionStatus.OPTIONAL -> StatusBadge(stringResource(R.string.hub_optional), Tone.NEUTRAL)
    }
}
