package ir.ilam.inspection.field.ui.thermal

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import ir.ilam.inspection.field.R
import ir.ilam.inspection.ui.common.AppTextField
import ir.ilam.inspection.ui.common.PrimaryButton
import ir.ilam.inspection.ui.common.SecondaryButton
import ir.ilam.inspection.ui.common.SectionCard
import ir.ilam.inspection.ui.theme.Spacing
import ir.ilam.inspection.ui.theme.Tavan
import ir.ilam.inspection.ui.theme.Tone
import ir.ilam.inspection.util.PersianDate
import ir.ilam.inspection.util.PersianNumbers

/**
 * Finds the HIKMICRO Viewer files taken during the route. The gallery is only
 * read: what is chosen is copied into the app byte for byte, and the files in
 * the gallery stay where they are.
 */
@Composable
fun HikmicroCard(viewModel: ThermalViewModel) {
    val context = LocalContext.current
    var access by remember { mutableStateOf(galleryAccess(context)) }
    var folder by remember { mutableStateOf(viewModel.folder) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        access = galleryAccess(context)
        if (access != GalleryAccess.NONE) viewModel.scan(context)
    }

    SectionCard(
        title = stringResource(R.string.thermal_files_title),
        subtitle = stringResource(R.string.thermal_files_hint),
        icon = Icons.Filled.PhotoLibrary,
        tone = Tone.ACCENT
    ) {
        Column {
            AppTextField(stringResource(R.string.thermal_folder), folder, {
                folder = it
                viewModel.setFolder(it)
            }, ltr = true)
            SecondaryButton(
                text = stringResource(R.string.thermal_scan),
                onClick = {
                    if (galleryAccess(context) == GalleryAccess.NONE) launcher.launch(galleryPermissions())
                    else viewModel.scan(context)
                },
                icon = Icons.Filled.Search,
                enabled = !viewModel.scanning && !viewModel.busy,
                modifier = Modifier.fillMaxWidth()
            )
            when (access) {
                GalleryAccess.PARTIAL -> Warning(stringResource(R.string.thermal_access_partial))
                GalleryAccess.NONE -> if (viewModel.found != null) Warning(stringResource(R.string.thermal_access_refused))
                GalleryAccess.FULL -> Unit
            }
            val found = viewModel.found
            if (found != null) {
                Text(
                    if (found.isEmpty()) stringResource(R.string.thermal_found_none)
                    else stringResource(R.string.thermal_found, PersianNumbers.toPersian(found.size)),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = Spacing.sm)
                )
                found.forEach { media ->
                    val checked = media.uri in viewModel.chosen
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable {
                            viewModel.chosen = if (checked) viewModel.chosen - media.uri else viewModel.chosen + media.uri
                        }
                    ) {
                        Checkbox(checked = checked, onCheckedChange = null, modifier = Modifier.padding(Spacing.sm))
                        if (media.video) {
                            Icon(Icons.Filled.Videocam, contentDescription = null, tint = Tavan.colors.info.strong,
                                modifier = Modifier.size(20.dp).padding(end = Spacing.xs))
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(media.name, style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Ltr))
                            Text(PersianDate.formatWithSeconds(media.takenAt), style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                if (found.isNotEmpty()) {
                    PrimaryButton(
                        text = stringResource(R.string.thermal_import, PersianNumbers.toPersian(viewModel.chosen.size)),
                        onClick = { viewModel.importChosen(context) },
                        icon = Icons.Filled.FileDownload,
                        enabled = viewModel.chosen.isNotEmpty(),
                        busy = viewModel.busy,
                        modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm)
                    )
                }
            }
            viewModel.summary?.let { s ->
                Text(
                    stringResource(R.string.thermal_import_summary, PersianNumbers.toPersian(s.added), PersianNumbers.toPersian(s.exifKept)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Tavan.colors.success.strong,
                    modifier = Modifier.padding(top = Spacing.sm)
                )
                if (s.exifDropped > 0) Warning(stringResource(R.string.thermal_exif_dropped, PersianNumbers.toPersian(s.exifDropped)))
                if (s.skipped > 0) Warning(stringResource(R.string.thermal_import_skipped, PersianNumbers.toPersian(s.skipped)))
            }
        }
    }
}

private enum class GalleryAccess { FULL, PARTIAL, NONE }

private fun galleryPermissions(): Array<String> = when {
    // Asking for the "selected photos" permission too lets Android 14 offer a
    // partial choice instead of falling back to a compatibility mode.
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> arrayOf(
        Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO,
        Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
    )
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
        arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
    else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
}

private fun galleryAccess(context: Context): GalleryAccess {
    fun has(permission: String) = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    return when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> when {
            has(Manifest.permission.READ_MEDIA_IMAGES) -> GalleryAccess.FULL
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
                has(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) -> GalleryAccess.PARTIAL
            else -> GalleryAccess.NONE
        }
        has(Manifest.permission.READ_EXTERNAL_STORAGE) -> GalleryAccess.FULL
        else -> GalleryAccess.NONE
    }
}
