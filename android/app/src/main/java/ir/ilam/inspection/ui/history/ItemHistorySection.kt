package ir.ilam.inspection.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import ir.ilam.inspection.R
import ir.ilam.inspection.container
import ir.ilam.inspection.sync.ApiResult
import ir.ilam.inspection.sync.Readings
import ir.ilam.inspection.sync.ServerApi
import ir.ilam.inspection.sync.ServerFiles
import ir.ilam.inspection.sync.historyImageRoute
import ir.ilam.inspection.sync.itemHistory
import ir.ilam.inspection.ui.common.SectionCard
import ir.ilam.inspection.ui.theme.Spacing
import ir.ilam.inspection.ui.theme.Tone
import ir.ilam.inspection.util.PersianNumbers
import java.io.File

/**
 * «سوابق و مقایسه» on an inspection's page: each panel and pole it read, with its
 * readings from this visit back. For the manager and the executors; the person who
 * recorded the inspection does not see this. Each plate folds open on a tap.
 */
@Composable
fun ItemHistorySection(itemId: String) {
    val container = LocalContext.current.container
    val groups by produceState<List<Readings.Group>?>(null, itemId) {
        val api = ServerApi(container.settingsRepository.current().syncTarget)
        val token = container.vault.serverToken()
        value = if (!api.configured || token == null) emptyList()
        else (api.itemHistory(token, itemId) as? ApiResult.Ok)?.value.orEmpty()
    }
    val loaded = groups ?: return
    SectionCard(title = stringResource(R.string.hist_item_title), subtitle = stringResource(R.string.hist_item_hint),
        icon = Icons.Filled.History, tone = Tone.INFO) {
        Column {
            if (loaded.isEmpty()) Text(stringResource(R.string.hist_item_empty), style = MaterialTheme.typography.bodyMedium)
            loaded.forEach { group ->
                var open by remember(group.label) { mutableStateOf(loaded.size <= 2) }
                Text(
                    PersianNumbers.toPersian(group.label) + " — " + stringResource(R.string.hist_count, PersianNumbers.toPersian(group.rows.size)),
                    style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth().clickable { open = !open }.padding(vertical = Spacing.sm)
                )
                if (open) ReadingList(group.rows, group.part) { id ->
                    val target = File(container.cacheDir, "history/$id.jpg")
                    val token = container.vault.serverToken()
                    target.takeIf {
                        it.exists() && it.length() > 0 || token != null &&
                            ServerFiles(container.settingsRepository.current().syncTarget, token).downloadRoute(historyImageRoute(id), it)
                    }
                }
            }
        }
    }
}
