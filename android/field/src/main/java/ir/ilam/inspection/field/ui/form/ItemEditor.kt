package ir.ilam.inspection.field.ui.form

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ir.ilam.inspection.field.FieldContainer
import ir.ilam.inspection.field.data.FieldKind
import ir.ilam.inspection.field.data.FileRole
import ir.ilam.inspection.field.data.SyncState
import ir.ilam.inspection.field.data.db.FieldFileEntity
import ir.ilam.inspection.field.data.db.FieldItemEntity
import ir.ilam.inspection.field.media.GalleryImport
import ir.ilam.inspection.field.media.ImportOutcome
import ir.ilam.inspection.field.sync.FieldSyncWorker
import ir.ilam.inspection.util.Fix
import ir.ilam.inspection.util.PhotoStamp
import ir.ilam.inspection.util.PlacedPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * What every field form shares: one item, loaded or made as a draft, saved a
 * moment after each edit, with its files. A form adds its own details on top.
 */
abstract class ItemEditor(
    protected val container: FieldContainer,
    private val kind: FieldKind,
    existingId: String?,
    /**
     * The screen's saved state. A new form remembers the draft it made here:
     * when Android closes the app in the background (a phone call, the camera,
     * another app) and the user comes back, the same draft opens again instead
     * of an empty new one.
     */
    private val state: SavedStateHandle? = null
) : ViewModel() {

    var item by mutableStateOf<FieldItemEntity?>(null)
        private set
    var files by mutableStateOf<List<FieldFileEntity>>(emptyList())
        private set
    var busy by mutableStateOf(false)
        protected set

    /** The address last drafted from the map, so a new position may replace it — but never the user's own words. */
    var autoAddress by mutableStateOf<String?>(null)

    /** Anything entered yet? A form left untouched is not worth a question, or a draft. */
    var touched by mutableStateOf(false)
        private set

    private var saveJob: Job? = null

    init {
        viewModelScope.launch {
            val id = existingId ?: state?.get<String>(KEY_DRAFT)
            val loaded = id?.let { container.drafts.item(it) } ?: container.drafts.create(kind)
            state?.set(KEY_DRAFT, loaded.id)
            item = loaded
            files = container.drafts.files(loaded.id)
            touched = loaded.updatedAt > loaded.createdAt || files.isNotEmpty()
            onLoaded(loaded)
        }
    }

    /** Called once the item is in hand, for the form to read its payload. */
    protected abstract fun onLoaded(item: FieldItemEntity)

    /** Applies an edit now and writes it shortly after, so typing is not a write per key. */
    fun update(transform: (FieldItemEntity) -> FieldItemEntity) {
        val current = item ?: return
        item = transform(current)
        touched = true
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(SAVE_DELAY)
            item?.let { container.drafts.save(it) }
        }
    }

    /**
     * Writes the pending edit at once: the app is going to the background and
     * may be closed there before the usual short delay is over.
     */
    fun saveNow() {
        if (saveJob?.isActive != true) return
        saveJob?.cancel()
        val current = item ?: return
        container.backgroundScope.launch { container.drafts.save(current) }
    }


    /** A draft can be thrown away; an item already sent or queued cannot. */
    val isDraft: Boolean
        get() = item?.syncState == SyncState.DRAFT.code

    protected suspend fun flush() {
        saveJob?.cancel()
        item?.let { item = container.drafts.save(it) }
    }

    /** A point recorded on the map, with its moment and the user's own position then. */
    fun setPlaced(point: PlacedPoint) = update {
        it.copy(
            latitude = point.latitude, longitude = point.longitude, accuracy = point.accuracy,
            positionAt = point.at, observerLat = point.observer?.latitude, observerLon = point.observer?.longitude,
            observerAccuracy = point.observer?.accuracy
        )
    }

    fun newFile(extension: String): File = container.drafts.newFile(item?.id ?: "pending", extension)

    /**
     * A photo from the app's camera: the original is kept untouched as the
     * evidence, and a copy gets the date, time and position burned in. Both
     * carry the same capture time, which is how the screen pairs them.
     * [onSaved] gets that time, for a form that ties the photo to one part of
     * itself — a pole of a feeder walk, whose own [place] the photo then carries.
     */
    fun addCameraPhoto(raw: File, stampCode: String, place: Fix? = null, onSaved: (Long) -> Unit = {}) {
        val now = System.currentTimeMillis()
        lastImport = null
        withItem(after = { onSaved(now) }) { current ->
            val at = place ?: current.latitude?.let { lat ->
                current.longitude?.let { lon -> Fix(lat, lon, current.accuracy ?: 0.0) }
            }
            attach(raw, FileRole.PHOTO, MIME_JPEG, now, at)
            val stamped = newFile("jpg")
            val stamp = PhotoStamp(stampCode, container.account.userCode, now, at?.latitude, at?.longitude)
            if (container.mediaProcessor.processPhoto(raw, stamped, stamp)) {
                attach(stamped, FileRole.PHOTO_STAMPED, MIME_JPEG, now, at)
            }
        }
    }

    /** The last gallery pick: how many came in, and why the others did not. */
    var lastImport by mutableStateOf<ImportOutcome?>(null)
        private set

    fun clearImportNote() {
        lastImport = null
    }

    /**
     * Photos and videos from the gallery: only from the last 24 hours, photos
     * re-encoded like the app's camera photos and videos shrunk to 720p and at
     * most 90 seconds. [onAdded] gets the capture times of those taken in, for
     * a form that ties them to one part of itself (a subscriber's evidence).
     */
    fun importGallery(context: Context, uris: List<Uri>, onAdded: (List<Long>) -> Unit = {}) {
        // The note is about this pick only: the last one's refusal must not linger over it.
        lastImport = null
        val added = mutableListOf<Long>()
        var old = 0
        var undated = 0
        var failed = 0
        withItem(after = {
            lastImport = ImportOutcome(added.size, old, undated, failed)
            onAdded(added)
        }) {
            uris.forEach { uri ->
                val video = context.contentResolver.getType(uri)?.startsWith("video/") == true
                val target = newFile(if (video) "mp4" else "jpg")
                val result = if (video) GalleryImport.video(context, uri, target) else GalleryImport.photo(context, uri, target)
                when (result) {
                    is GalleryImport.Result.Ok -> {
                        // Two captures taken in the same second still need two keys.
                        var at = result.takenAt
                        while (at in added) at++
                        attach(target, if (video) FileRole.VIDEO else FileRole.PHOTO, if (video) MIME_MP4 else MIME_JPEG, at)
                        added += at
                    }
                    is GalleryImport.Result.TooOld -> old++
                    GalleryImport.Result.NoDate -> undated++
                    GalleryImport.Result.Failed -> failed++
                }
                if (result !is GalleryImport.Result.Ok) target.delete()
            }
        }
    }

    /** A clip from the app's camera; [onSaved] gets its capture time, as for a photo. */
    fun addVideo(file: File, onSaved: (Long) -> Unit = {}) {
        val now = System.currentTimeMillis()
        lastImport = null
        withItem(after = { onSaved(now) }) { attach(file, FileRole.VIDEO, MIME_MP4, now) }
    }

    fun addAudio(file: File) = withItem(after = { lastImport = null }) { attach(file, FileRole.AUDIO, "audio/mp4", System.currentTimeMillis()) }

    /** Removes a capture: a camera photo goes together with its stamped copy. */
    fun remove(file: FieldFileEntity) = withItem {
        val photo = file.role == FileRole.PHOTO || file.role == FileRole.PHOTO_STAMPED
        val pair = if (photo) files.filter {
            (it.role == FileRole.PHOTO || it.role == FileRole.PHOTO_STAMPED) && it.capturedAt == file.capturedAt
        } else listOf(file)
        pair.forEach { container.drafts.removeFile(it) }
    }

    /** Reloads the item's files after a form changed them directly. */
    protected suspend fun reloadFiles() {
        item?.let { files = container.drafts.files(it.id) }
    }

    /** Last work a form does before its item joins the queue: sidecars, packed tracks. */
    protected open suspend fun beforeFinish() = Unit

    /** Saves, joins the send queue and asks for a send as soon as there is a connection. */
    fun finish(context: Context, onDone: () -> Unit) {
        viewModelScope.launch {
            busy = true
            flush()
            withContext(Dispatchers.IO) { beforeFinish() }
            flush()
            item?.let { container.drafts.finish(it) }
            FieldSyncWorker.runSoon(context)
            busy = false
            onDone()
        }
    }

    /** Leaves the form keeping the draft: whatever is pending is written first. */
    fun keepAndClose(onDone: () -> Unit) {
        viewModelScope.launch {
            flush()
            state?.remove<String>(KEY_DRAFT)
            onDone()
        }
    }

    fun discard(onDone: () -> Unit) {
        viewModelScope.launch {
            saveJob?.cancel()
            item?.let { container.drafts.discard(it) }
            onDone()
        }
    }

    private suspend fun attach(file: File, role: Int, mime: String, at: Long, place: Fix? = null) {
        val current = item ?: return
        withContext(Dispatchers.IO) {
            if (place != null) {
                container.drafts.attach(current.id, file, role, mime, at, place.latitude, place.longitude,
                    place.accuracy.takeIf { it > 0 })
            } else {
                container.drafts.attach(current.id, file, role, mime, at, current.latitude, current.longitude, current.accuracy)
            }
        }
    }

    private fun withItem(after: () -> Unit = {}, block: suspend (FieldItemEntity) -> Unit) {
        val current = item ?: return
        touched = true
        viewModelScope.launch {
            busy = true
            withContext(Dispatchers.IO) { block(current) }
            files = container.drafts.files(current.id)
            busy = false
            after()
        }
    }

    companion object {
        const val MIME_JPEG = "image/jpeg"
        const val MIME_PNG = "image/png"
        const val MIME_MP4 = "video/mp4"
        private const val SAVE_DELAY = 400L
        private const val KEY_DRAFT = "draft_id"
    }
}
