package ir.ilam.inspection.field.ui.form

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ir.ilam.inspection.field.FieldContainer
import ir.ilam.inspection.field.data.FieldKind
import ir.ilam.inspection.field.data.FileRole
import ir.ilam.inspection.field.data.db.FieldFileEntity
import ir.ilam.inspection.field.data.db.FieldItemEntity
import ir.ilam.inspection.field.sync.FieldSyncWorker
import ir.ilam.inspection.util.Fix
import ir.ilam.inspection.util.PhotoStamp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * What every field form shares: one item, loaded or made as a draft, saved a
 * moment after each edit, with its files. A form adds its own details on top.
 */
abstract class ItemEditor(
    protected val container: FieldContainer,
    private val kind: FieldKind,
    existingId: String?
) : ViewModel() {

    var item by mutableStateOf<FieldItemEntity?>(null)
        private set
    var files by mutableStateOf<List<FieldFileEntity>>(emptyList())
        private set
    var busy by mutableStateOf(false)
        protected set

    private var saveJob: Job? = null

    init {
        viewModelScope.launch {
            val loaded = existingId?.let { container.drafts.item(it) } ?: container.drafts.create(kind)
            item = loaded
            files = container.drafts.files(loaded.id)
            onLoaded(loaded)
        }
    }

    /** Called once the item is in hand, for the form to read its payload. */
    protected abstract fun onLoaded(item: FieldItemEntity)

    /** Applies an edit now and writes it shortly after, so typing is not a write per key. */
    fun update(transform: (FieldItemEntity) -> FieldItemEntity) {
        val current = item ?: return
        item = transform(current)
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(SAVE_DELAY)
            item?.let { container.drafts.save(it) }
        }
    }

    protected suspend fun flush() {
        saveJob?.cancel()
        item?.let { item = container.drafts.save(it) }
    }

    fun setPosition(latitude: Double, longitude: Double, accuracy: Double?) =
        update { it.copy(latitude = latitude, longitude = longitude, accuracy = accuracy) }

    fun setMeasured(fix: Fix) = setPosition(fix.latitude, fix.longitude, fix.accuracy)

    fun newFile(extension: String): File = container.drafts.newFile(item?.id ?: "pending", extension)

    /**
     * A photo from the app's camera: the original is kept untouched as the
     * evidence, and a copy gets the date, time and position burned in. Both
     * carry the same capture time, which is how the screen pairs them.
     */
    fun addCameraPhoto(raw: File, stampCode: String) = withItem { current ->
        val now = System.currentTimeMillis()
        attach(raw, FileRole.PHOTO, MIME_JPEG, now)
        val stamped = newFile("jpg")
        val stamp = PhotoStamp(stampCode, container.account.userCode, now, current.latitude, current.longitude)
        if (container.mediaProcessor.processPhoto(raw, stamped, stamp)) {
            attach(stamped, FileRole.PHOTO_STAMPED, MIME_JPEG, now)
        }
    }

    /** Pictures from the gallery: kept as they are, re-encoded only if not JPEG or PNG. */
    fun importPhotos(context: Context, uris: List<Uri>) = withItem {
        uris.forEach { uri ->
            val mime = context.contentResolver.getType(uri) ?: MIME_JPEG
            val keep = mime == MIME_JPEG || mime == MIME_PNG
            val target = newFile(if (mime == MIME_PNG) "png" else "jpg")
            val copied = runCatching { copyPhoto(context, uri, target, keep) }.getOrDefault(false)
            if (copied) attach(target, FileRole.PHOTO, if (keep) mime else MIME_JPEG, System.currentTimeMillis())
        }
    }

    private fun copyPhoto(context: Context, uri: Uri, target: File, keep: Boolean): Boolean {
        val input = context.contentResolver.openInputStream(uri) ?: return false
        input.use {
            if (keep) {
                FileOutputStream(target).use { out -> it.copyTo(out) }
            } else {
                val bitmap = BitmapFactory.decodeStream(it) ?: return false
                FileOutputStream(target).use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out) }
                bitmap.recycle()
            }
        }
        return target.length() > 0
    }

    fun addVideo(file: File) = withItem { attach(file, FileRole.VIDEO, "video/mp4", System.currentTimeMillis()) }

    fun addAudio(file: File) = withItem { attach(file, FileRole.AUDIO, "audio/mp4", System.currentTimeMillis()) }

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

    fun discard(onDone: () -> Unit) {
        viewModelScope.launch {
            saveJob?.cancel()
            item?.let { container.drafts.discard(it) }
            onDone()
        }
    }

    private suspend fun attach(file: File, role: Int, mime: String, at: Long) {
        val current = item ?: return
        withContext(Dispatchers.IO) {
            container.drafts.attach(current.id, file, role, mime, at, current.latitude, current.longitude, current.accuracy)
        }
    }

    private fun withItem(block: suspend (FieldItemEntity) -> Unit) {
        val current = item ?: return
        viewModelScope.launch {
            busy = true
            withContext(Dispatchers.IO) { block(current) }
            files = container.drafts.files(current.id)
            busy = false
        }
    }

    companion object {
        const val MIME_JPEG = "image/jpeg"
        const val MIME_PNG = "image/png"
        private const val SAVE_DELAY = 400L
    }
}
