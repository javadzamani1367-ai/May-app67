package ir.ilam.inspection.ui.intake

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.ilam.inspection.R
import ir.ilam.inspection.data.model.ReportType
import ir.ilam.inspection.ui.common.icon
import ir.ilam.inspection.ui.common.reportTypeLabel
import ir.ilam.inspection.ui.theme.Tavan
import ir.ilam.inspection.ui.theme.colorForReportType

/**
 * The channel a report came in through, as six tiles rather than a dropdown:
 * all six are visible at once, each in the colour it will carry on its card,
 * and one tap picks one. It also shows which letter the tracking code will
 * start with, so the expert knows what code to expect.
 */
@Composable
fun ReportTypePicker(selected: ReportType?, onSelect: (ReportType) -> Unit, error: String?) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ReportType.entries.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { type ->
                    TypeTile(type, type == selected, { onSelect(type) }, Modifier.weight(1f))
                }
            }
        }
        if (error != null) {
            Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun TypeTile(type: ReportType, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val color = colorForReportType(type.code, Tavan.colors.dark)
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = if (selected) color.copy(alpha = if (Tavan.colors.dark) 0.24f else 0.1f) else MaterialTheme.colorScheme.surface,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) color else MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
    ) {
        Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(contentAlignment = Alignment.Center) {
                Icon(type.icon(), contentDescription = null, tint = color, modifier = Modifier.size(26.dp))
            }
            Column(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
                Text(
                    reportTypeLabel(type),
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = type.letter?.let { stringResource(R.string.intake_code_letter, it) }
                        ?: stringResource(R.string.intake_code_manual),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (selected) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
            }
        }
    }
}
