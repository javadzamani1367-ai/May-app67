package ir.ilam.inspection.field.ui.line

import androidx.lifecycle.SavedStateHandle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ir.ilam.inspection.field.FieldContainer
import ir.ilam.inspection.field.data.FieldKind
import ir.ilam.inspection.field.data.FileRole
import ir.ilam.inspection.field.data.LineAnalysis
import ir.ilam.inspection.field.data.LinePayload
import ir.ilam.inspection.field.data.LineVerdict
import ir.ilam.inspection.field.data.PhaseReading
import ir.ilam.inspection.field.data.PoleReading
import ir.ilam.inspection.field.data.db.FieldItemEntity
import ir.ilam.inspection.field.ui.form.ItemEditor
import ir.ilam.inspection.util.Fix

/**
 * Pole-by-pole amperage of one feeder: the panel it leaves, then each pole
 * as the user walks the line. The item's own position follows the first
 * located pole, so the office's map shows where the walk began.
 */
class LineViewModel(container: FieldContainer, existingId: String?, state: SavedStateHandle? = null) :
    ItemEditor(container, FieldKind.FEEDER_LINE, existingId, state) {

    var payload by mutableStateOf(LinePayload())
        private set

    override fun onLoaded(item: FieldItemEntity) {
        payload = LinePayload.fromJson(item.payload)
    }

    fun setPlate(text: String) = update { it.copy(plate = text) }
    fun setAddress(text: String) = update { it.copy(address = text) }
    fun setFeeder(key: String) = edit { it.copy(feeder = key) }
    fun setFeederName(text: String) = edit { it.copy(feederName = text) }
    fun setNetwork(key: String) = edit { it.copy(network = key) }
    fun setStart(reading: PhaseReading) = edit { it.copy(start = reading) }
    fun setNote(text: String) = edit { it.copy(note = text) }

    fun pole(id: String): PoleReading? = payload.poles.firstOrNull { it.id == id }
    fun number(id: String): Int = payload.poles.indexOfFirst { it.id == id } + 1

    /**
     * A new pole at the end of the walk. If the phone already has a live fix,
     * the pole is located at once — the user is standing at it — and can
     * re-take or move it on its page.
     */
    fun addPole(here: Fix?): String {
        val pole = PoleReading(
            latitude = here?.latitude,
            longitude = here?.longitude,
            accuracy = here?.accuracy,
            positionAt = here?.let { System.currentTimeMillis() }
        )
        edit { it.copy(poles = it.poles + pole) }
        return pole.id
    }

    fun updatePole(id: String, transform: (PoleReading) -> PoleReading) =
        edit { p -> p.copy(poles = p.poles.map { if (it.id == id) transform(it) else it }) }

    fun locatePole(id: String, latitude: Double, longitude: Double, accuracy: Double?) = updatePole(id) {
        it.copy(latitude = latitude, longitude = longitude, accuracy = accuracy, positionAt = System.currentTimeMillis())
    }

    fun removePole(id: String) {
        val photos = pole(id)?.photos.orEmpty().toSet()
        edit { p -> p.copy(poles = p.poles.filterNot { it.id == id }) }
        files.filter { it.capturedAt in photos && it.role == FileRole.PHOTO }.forEach { remove(it) }
    }

    fun verdict(): LineVerdict = LineAnalysis.verdict(payload)

    /** What stops saving: the panel plate, the feeder, two measured poles, and every pole complete. */
    fun missing(): List<String> {
        val item = item ?: return emptyList()
        return buildList {
            if (item.plate.isNullOrBlank()) add(KEY_PLATE)
            addAll(LineAnalysis.missing(payload))
        }
    }

    private fun edit(transform: (LinePayload) -> LinePayload) {
        val next = transform(payload)
        payload = next
        val first = next.poles.firstOrNull { it.hasPosition }
        update {
            it.copy(
                payload = next.toJson(LineAnalysis.verdict(next)).toString(),
                latitude = first?.latitude,
                longitude = first?.longitude,
                accuracy = first?.accuracy
            )
        }
    }

    companion object {
        const val KEY_PLATE = "plate"
    }
}
