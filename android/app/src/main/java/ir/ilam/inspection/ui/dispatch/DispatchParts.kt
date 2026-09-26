package ir.ilam.inspection.ui.dispatch

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ElectricalServices
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.ilam.inspection.data.db.MediaEntity
import ir.ilam.inspection.data.model.DispatchUnit
import ir.ilam.inspection.ui.common.dispatchUnitLabel
import ir.ilam.inspection.ui.theme.Spacing
import ir.ilam.inspection.ui.theme.Tavan
import ir.ilam.inspection.ui.visit.MediaPreview
import ir.ilam.inspection.util.FileStore

fun DispatchUnit.icon(): ImageVector = when (this) {
    DispatchUnit.SALES -> Icons.Filled.Storefront
    DispatchUnit.SECURITY -> Icons.Filled.Shield
    DispatchUnit.LEGAL -> Icons.Filled.Gavel
    DispatchUnit.COUNTY_POWER -> Icons.Filled.ElectricalServices
}

/** The four receiving units as tiles, all visible, one tap to choose. */
@Composable
fun UnitPicker(selected: DispatchUnit, onSelect: (DispatchUnit) -> Unit) {
    val accent = Tavan.colors.accent
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.padding(top = Spacing.xs)) {
        DispatchUnit.entries.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                pair.forEach { unit ->
                    val isSelected = unit == selected
                    Surface(
                        onClick = { onSelect(unit) },
                        shape = MaterialTheme.shapes.medium,
                        color = if (isSelected) accent.container else MaterialTheme.colorScheme.surface,
                        border = BorderStroke(
                            if (isSelected) 2.dp else 1.dp,
                            if (isSelected) accent.strong else MaterialTheme.colorScheme.outlineVariant
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(unit.icon(), contentDescription = null, tint = if (isSelected) accent.strong else MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                dispatchUnitLabel(unit),
                                style = MaterialTheme.typography.labelLarge,
                                color = if (isSelected) accent.onContainer else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f).padding(horizontal = Spacing.sm),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

/** A photo or video to tick, as a picture with a tick in its corner. */
@Composable
fun SelectableMediaTile(
    media: MediaEntity,
    label: String,
    files: FileStore,
    checked: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = Tavan.colors.accent.strong
    val shape = MaterialTheme.shapes.medium
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(shape)
            .border(if (checked) 3.dp else 1.dp, if (checked) accent else MaterialTheme.colorScheme.outlineVariant, shape)
            .clickable(onClick = onToggle)
    ) {
        MediaPreview(media = media, files = files, edge = 300, modifier = Modifier.fillMaxSize())
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.6f))
                .padding(horizontal = 6.dp, vertical = 3.dp)
        )
        Icon(
            if (checked) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (checked) accent else Color.White,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(6.dp)
                .size(26.dp)
                .background(if (checked) Color.White else Color.Black.copy(alpha = 0.3f), MaterialTheme.shapes.extraLarge)
        )
    }
}
