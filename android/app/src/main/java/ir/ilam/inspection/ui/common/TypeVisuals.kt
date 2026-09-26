package ir.ilam.inspection.ui.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CorporateFare
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.ui.graphics.vector.ImageVector
import ir.ilam.inspection.data.model.ReportType

/** What each channel a report comes in through looks like, next to its colour. */
fun ReportType.icon(): ImageVector = when (this) {
    ReportType.SORAGH -> Icons.Filled.Dns
    ReportType.SYSTEM_121 -> Icons.Filled.SupportAgent
    ReportType.PUBLIC -> Icons.Filled.Campaign
    ReportType.COLLEAGUE -> Icons.Filled.Badge
    ReportType.TAVANIR -> Icons.Filled.CorporateFare
    ReportType.FIELD -> Icons.Filled.TravelExplore
}
