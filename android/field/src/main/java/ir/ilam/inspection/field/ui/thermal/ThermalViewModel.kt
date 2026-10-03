package ir.ilam.inspection.field.ui.thermal

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import ir.ilam.inspection.field.FieldContainer
import ir.ilam.inspection.field.data.FieldDrafts
import ir.ilam.inspection.field.data.FieldKind
import ir.ilam.inspection.field.data.FileRole
import ir.ilam.inspection.field.data.db.FieldFileEntity
import ir.ilam.inspection.field.data.db.FieldItemEntity
import ir.ilam.inspection.field.thermal.FoundMedia
import ir.ilam.inspection.field.thermal.GalleryScanner
import ir.ilam.inspection.field.thermal.Temperatures
import ir.ilam.inspection.field.thermal.ThermalFileInfo
import ir.ilam.inspection.field.thermal.ThermalFinisher
import ir.ilam.inspection.field.thermal.ThermalImporter
import ir.ilam.inspection.field.thermal.ThermalPayload
import ir.ilam.inspection.field.thermal.TrackMatcher
import ir.ilam.inspection.field.thermal.TrackRecorder
import ir.ilam.inspection.field.ui.form.ItemEditor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** What the last import did, for one line on the screen. */
data class ImportSummary(val added: Int, val exifKept: Int, val exifDropped: Int, val skipped: Int)

/**
 * A thermal inspection: the route, the HIKMICRO files found for it, and what
 * each file shows — pole or panel, its plate, a note, its temperatures.
 */
class ThermalViewModel(container: FieldContainer, existingId: String?) :
    ItemEditor(container, FieldKind.THERMAL, existingId) {

    var payload by mutableStateOf(ThermalPayload())
        private set
    var found by mutableStateOf<List<FoundMedia>?>(null)
        private set
    var chosen by mutableStateOf<Set<String>>(emptySet())
    var summary by mutableStateOf<ImportSummary?>(null)
        private set
    var scanning by mutableStateOf(false)
        private set

    val folder: String get() = container.prefs.thermalFolder
    fun setFolder(value: String) { container.prefs.thermalFolder = value }

    override fun onLoaded(item: FieldItemEntity) {
        payload = ThermalPayload.fromJson(item.payload)
    }

    private fun trackFile(id: String): File = TrackRecorder.file(container.files.resolve(FieldDrafts.FOLDER), id)

    /** The thermal originals, newest last: the list the user assigns. */
    val originals: List<FieldFileEntity> get() = files.filter { it.role == FileRole.THERMAL }.sortedBy { it.capturedAt }

    fun startRoute(context: Context) {
        val current = item ?: return
        if (payload.startedAt == null) setPayload(payload.copy(startedAt = System.currentTimeMillis(), endedAt = null))
        else setPayload(payload.copy(endedAt = null))
        TrackRecorder.start(context, current.id)
    }

    fun stopRoute(context: Context) {
        val current = item ?: return
        TrackRecorder.stop(context)
        viewModelScope.launch {
            val points = withContext(Dispatchers.IO) { TrackRecorder.read(trackFile(current.id)) }
            setPayload(payload.copy(endedAt = System.currentTimeMillis(), points = points.size,
                clockSkewSeconds = TrackMatcher.clockSkewSeconds(points)))
        }
    }

    /** Looks in the gallery for HIKMICRO files from this session, two minutes either side. */
    fun scan(context: Context) {
        val start = payload.startedAt ?: return
        viewModelScope.launch {
            scanning = true
            val known = payload.files.values.map { it.name }.toSet()
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    GalleryScanner(context).scan(folder, start - TrackMatcher.MAX_GAP_MILLIS,
                        (payload.endedAt ?: System.currentTimeMillis()) + TrackMatcher.MAX_GAP_MILLIS)
                }.getOrDefault(emptyList())
            }.filter { it.name !in known }
            found = result
            chosen = result.map { it.uri }.toSet()
            scanning = false
        }
    }

    /** Brings the chosen files in, each placed on the route by its capture time. */
    fun importChosen(context: Context) {
        val current = item ?: return
        val picked = found.orEmpty().filter { it.uri in chosen }
        if (picked.isEmpty()) return
        viewModelScope.launch {
            busy = true
            val importer = ThermalImporter(context, container.drafts)
            var added = 0; var kept = 0; var dropped = 0; var skipped = 0
            val names = mutableMapOf<String, ThermalFileInfo>()
            withContext(Dispatchers.IO) {
                val track = TrackRecorder.read(trackFile(current.id))
                picked.forEach { media ->
                    val result = importer.import(current.id, media, TrackMatcher.locate(track, media.takenAt))
                    val original = result.original
                    if (original == null) skipped++ else {
                        added++
                        names[original.id] = ThermalFileInfo(media.name)
                    }
                    if (result.exifKept) kept++
                    if (result.exifDropped) dropped++
                }
            }
            setPayload(payload.copy(files = payload.files + names))
            reloadFiles()
            found = found.orEmpty().filter { it.uri !in chosen }
            chosen = emptySet()
            summary = ImportSummary(added, kept, dropped, skipped)
            busy = false
        }
    }

    fun assign(ids: Set<String>, assetType: Int, plate: String, note: String) {
        if (ids.isEmpty() || plate.isBlank()) return
        viewModelScope.launch {
            container.drafts.assign(ids.toList(), assetType, plate, note)
            reloadFiles()
            // The item carries the first plate, so the office can find it by plate.
            if (item?.plate.isNullOrBlank()) update { it.copy(plate = plate.trim()) }
        }
    }

    /** Removes a thermal file with its EXIF copy, so a later scan can offer it again. */
    fun removeThermal(file: FieldFileEntity) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                files.filter { (it.role == FileRole.THERMAL || it.role == FileRole.THERMAL_EXIF) && it.capturedAt == file.capturedAt }
                    .forEach { container.drafts.removeFile(it) }
            }
            setPayload(payload.copy(files = payload.files - file.id))
            reloadFiles()
        }
    }

    fun setTemperatures(fileId: String, temperatures: Temperatures) {
        val info = payload.files[fileId] ?: ThermalFileInfo()
        setPayload(payload.copy(files = payload.files + (fileId to info.copy(temperatures = temperatures))))
    }

    fun setAddress(text: String) = update { it.copy(address = text) }

    /** Nothing may be finished while the route is still recording, or with a file nobody assigned. */
    fun missing(recording: Boolean): List<String> = buildList {
        if (recording) add(KEY_RECORDING)
        if (originals.isEmpty()) add(KEY_NO_FILES)
        originals.count { it.assetType == null || it.plate.isNullOrBlank() }.takeIf { it > 0 }?.let { add("$KEY_UNASSIGNED:$it") }
    }

    override suspend fun beforeFinish() {
        val current = item ?: return
        // The item sits where its first located file was, for the office's map.
        val anchor = originals.firstOrNull { it.latitude != null && !it.locationUncertain } ?: originals.firstOrNull { it.latitude != null }
        if (anchor != null && current.latitude == null) {
            update { it.copy(latitude = anchor.latitude, longitude = anchor.longitude, accuracy = anchor.accuracy) }
        }
        ThermalFinisher(container.drafts).prepare(item ?: current, payload, container.account.userCode,
            container.account.deviceCode, trackFile(current.id))
    }

    fun trackPoints() = item?.let { TrackRecorder.read(trackFile(it.id)) }.orEmpty()

    private fun setPayload(next: ThermalPayload) {
        payload = next
        update { it.copy(payload = next.toJson().toString()) }
    }

    companion object {
        const val KEY_RECORDING = "recording"
        const val KEY_NO_FILES = "no_files"
        const val KEY_UNASSIGNED = "unassigned"
    }
}
