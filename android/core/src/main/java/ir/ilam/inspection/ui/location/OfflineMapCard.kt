package ir.ilam.inspection.ui.location

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.ilam.inspection.core.R
import ir.ilam.inspection.ui.common.ConfirmDeleteButton
import ir.ilam.inspection.ui.common.PrimaryButton
import ir.ilam.inspection.ui.common.SecondaryButton
import ir.ilam.inspection.ui.common.SectionCard
import ir.ilam.inspection.ui.common.StatusBadge
import ir.ilam.inspection.ui.theme.Spacing
import ir.ilam.inspection.ui.theme.Tavan
import ir.ilam.inspection.ui.theme.Tone
import ir.ilam.inspection.util.PersianNumbers
import ir.ilam.inspection.util.map.MapBundleException
import ir.ilam.inspection.util.map.MapDownload
import ir.ilam.inspection.util.map.OfflineMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.Locale

/**
 * Installs, updates and removes the offline Ilam map. Two ways in: from the
 * server (where the manager put the bundle map.yml builds), or from a file
 * that reached the phone some other way — a cable, a messenger, a memory card.
 */
@Composable
fun OfflineMapCard(serverAddress: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val installed by OfflineMap.installed.collectAsStateWithLifecycle(OfflineMap.current(context))
    var busy by remember { mutableStateOf<String?>(null) }
    var progress by remember { mutableStateOf<Float?>(null) }
    var message by remember { mutableStateOf<Pair<String, Boolean>?>(null) }

    val installingText = stringResource(R.string.offline_map_installing)
    val doneText = stringResource(R.string.offline_map_done)
    val badText = stringResource(R.string.offline_map_bad_file)
    val missingText = stringResource(R.string.offline_map_not_on_server)
    val unreachableText = stringResource(R.string.offline_map_unreachable)
    val noServerText = stringResource(R.string.offline_map_no_server)
    val downloadingText = stringResource(R.string.offline_map_downloading)
    val downloadingOfText = stringResource(R.string.offline_map_downloading_of)

    suspend fun install(open: () -> InputStream, cleanup: () -> Unit = {}) {
        busy = installingText
        progress = null
        try {
            val manifest = OfflineMap.install(context, open)
            message = doneText.format(PersianNumbers.toPersian(manifest.version)) to false
            cleanup()
        } catch (e: MapBundleException) {
            // A bad file is thrown away, so the next download starts clean.
            message = badText.format(e.reason.name.lowercase(Locale.US)) to true
            cleanup()
        } catch (e: IOException) {
            message = badText.format(e.javaClass.simpleName) to true
        } finally {
            busy = null
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) scope.launch {
            install({ context.contentResolver.openInputStream(uri) ?: throw IOException("unreadable") })
        }
    }

    fun download() {
        if (serverAddress.isBlank()) { message = noServerText to true; return }
        val target = File(context.cacheDir, "map-download.zip")
        scope.launch {
            busy = downloadingText.format(PersianNumbers.toPersian(0))
            message = null
            try {
                MapDownload.fetch(MapDownload.serverUrl(serverAddress), target) { done, total ->
                    busy = if (total == null) downloadingText.format(mb(done))
                    else downloadingOfText.format(mb(done), mb(total))
                    progress = total?.let { done.toFloat() / it }
                }
                install({ target.inputStream() }) { target.delete() }
            } catch (e: CancellationException) {
                busy = null
                throw e
            } catch (e: MapDownload.Failed) {
                busy = null
                message = (if (e.status == 404) missingText else unreachableText) to true
            } catch (e: IOException) {
                busy = null
                message = unreachableText to true
            }
        }
    }

    SectionCard(
        title = stringResource(R.string.offline_map_title),
        subtitle = stringResource(R.string.offline_map_hint),
        icon = Icons.Filled.Map,
        tone = Tone.INFO,
        modifier = modifier,
        trailing = {
            installed?.let { StatusBadge(PersianNumbers.toPersian(it.version), Tone.SUCCESS) }
            if (installed != null && busy == null) {
                ConfirmDeleteButton(itemName = stringResource(R.string.offline_map_title), onConfirm = {
                    scope.launch { OfflineMap.remove(context) }
                })
            }
        }
    ) {
        Column {
            val current = installed
            Text(
                if (current == null) stringResource(R.string.offline_map_none)
                else stringResource(R.string.offline_map_installed, PersianNumbers.toPersian(current.version),
                    mb(current.mapSize + current.geoSize)),
                style = MaterialTheme.typography.bodyMedium
            )
            busy?.let { text ->
                Text(text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = Spacing.sm))
                val p = progress
                if (p != null) LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth().padding(top = Spacing.xs))
                else LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = Spacing.xs))
            }
            message?.let { (text, error) ->
                Text(text, style = MaterialTheme.typography.bodyMedium,
                    color = if (error) Tavan.colors.danger.strong else Tavan.colors.success.strong,
                    modifier = Modifier.padding(top = Spacing.sm))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.padding(top = Spacing.md)) {
                PrimaryButton(
                    text = stringResource(R.string.offline_map_download),
                    onClick = { download() },
                    icon = Icons.Filled.CloudDownload,
                    enabled = busy == null,
                    modifier = Modifier.weight(1f)
                )
                SecondaryButton(
                    text = stringResource(R.string.offline_map_pick),
                    onClick = { picker.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")) },
                    icon = Icons.Filled.FolderOpen,
                    enabled = busy == null,
                    modifier = Modifier.weight(1f)
                )
            }
            Text(stringResource(R.string.offline_map_source), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(top = Spacing.sm))
        }
    }
}

/** Megabytes with one decimal, in Persian digits. */
private fun mb(bytes: Long): String = PersianNumbers.toPersian("%.1f".format(Locale.US, bytes / 1_048_576.0))
