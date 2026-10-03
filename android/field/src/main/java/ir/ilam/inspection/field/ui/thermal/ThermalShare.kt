package ir.ilam.inspection.field.ui.thermal

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import ir.ilam.inspection.field.R
import ir.ilam.inspection.field.data.AssetType
import ir.ilam.inspection.field.data.db.FieldFileEntity
import ir.ilam.inspection.field.data.db.FieldItemEntity
import ir.ilam.inspection.field.thermal.ExportFile
import ir.ilam.inspection.field.thermal.Temperatures
import ir.ilam.inspection.field.thermal.ThermalExport
import ir.ilam.inspection.field.thermal.ThermalPayload
import ir.ilam.inspection.field.thermal.TrackPoint
import java.io.File

/**
 * The route and the files as KML (Google Earth) and CSV (a spreadsheet),
 * handed to the share sheet. Written to the cache: they are a view of the
 * item, rebuilt each time, never evidence in their own right.
 */
object ThermalShare {

    fun share(context: Context, item: FieldItemEntity, payload: ThermalPayload, track: List<TrackPoint>, originals: List<FieldFileEntity>) {
        val title = item.trackingCode ?: item.plate?.takeIf { it.isNotBlank() } ?: item.id.take(8)
        val files = originals.map { file ->
            val info = payload.files[file.id]
            ExportFile(
                id = file.id,
                name = info?.name.orEmpty().ifBlank { file.path?.substringAfterLast('/').orEmpty() },
                video = file.mime.startsWith("video"),
                takenAt = file.capturedAt ?: 0L,
                latitude = file.latitude,
                longitude = file.longitude,
                accuracy = file.accuracy,
                uncertain = file.locationUncertain,
                assetLabel = when (file.assetType) {
                    AssetType.POLE -> context.getString(R.string.thermal_asset_pole)
                    AssetType.PANEL -> context.getString(R.string.thermal_asset_panel)
                    else -> ""
                },
                plate = file.plate.orEmpty(),
                note = file.note.orEmpty(),
                sha256 = file.sha256,
                temperatures = info?.temperatures ?: Temperatures()
            )
        }
        val folder = File(context.cacheDir, EXPORT_FOLDER).apply { deleteRecursively(); mkdirs() }
        val safe = title.replace(Regex("[^A-Za-z0-9_-]"), "_")
        val kml = File(folder, "$safe.kml").apply { writeText(ThermalExport.kml(title, track, files)) }
        val csv = File(folder, "$safe.csv").apply { writeText(ThermalExport.csv(files)) }

        val authority = context.packageName + ".fileprovider"
        val uris = arrayListOf<Uri>(
            FileProvider.getUriForFile(context, authority, kml),
            FileProvider.getUriForFile(context, authority, csv)
        )
        val send = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "*/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            clipData = ClipData.newRawUri(null, uris[0]).apply { addItem(ClipData.Item(uris[1])) }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, context.getString(R.string.thermal_export)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private const val EXPORT_FOLDER = "export"
}
