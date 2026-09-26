package ir.roozban.feature.tools

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.roozban.core.designsystem.R as DsR
import ir.roozban.core.designsystem.components.AppCard
import ir.roozban.core.designsystem.components.AssistantAction
import ir.roozban.core.designsystem.components.IconBadge
import ir.roozban.core.designsystem.components.RoozbanTopBar
import ir.roozban.core.designsystem.theme.Role
import ir.roozban.core.designsystem.theme.Roozban

/** «ابزارهای کاربردی»: small tools next to the planner. */
@Composable
internal fun ToolsScreen(onOpenNotes: () -> Unit, onBack: () -> Unit) {
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            RoozbanTopBar(
                title = { Text("ابزارهای کاربردی") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(painterResource(DsR.drawable.ic_arrow_back), "بازگشت") } },
                actions = { AssistantAction() },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Tool(
                DsR.drawable.ic_mic,
                "تبدیل صدا به متن",
                "یادداشت بنویس یا بگو؛ هر قدر طولانی. متن را با صدای فارسی هم می‌شنوی. همه‌چیز روی خود گوشی.",
                Roozban.colors.focus,
                onOpenNotes,
            )
        }
    }
}

@Composable
private fun Tool(@DrawableRes icon: Int, title: String, subtitle: String, role: Role, onClick: () -> Unit) {
    AppCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon, role, size = 44.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(painterResource(DsR.drawable.ic_chevron_left), null, tint = MaterialTheme.colorScheme.outline)
        }
    }
}
