package ir.ilam.inspection.field.ui.line

import androidx.lifecycle.SavedStateHandle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ir.ilam.inspection.field.FieldContainer
import ir.ilam.inspection.field.data.Crew
import ir.ilam.inspection.field.data.FieldKind
import ir.ilam.inspection.field.data.Pick
import ir.ilam.inspection.field.data.Roster
import ir.ilam.inspection.field.data.FileRole
import ir.ilam.inspection.field.data.LineAnalysis
import ir.ilam.inspection.field.data.LinePayload
import ir.ilam.inspection.field.data.LineVerdict
import ir.ilam.inspection.field.data.PhaseReading
import ir.ilam.inspection.field.data.PoleReading
import ir.ilam.inspection.field.data.Subscriber
import ir.ilam.inspection.field.data.db.FieldItemEntity
import ir.ilam.inspection.field.ui.form.ItemEditor
import ir.ilam.inspection.util.PlacedPoint

/**
 * Pole-by-pole amperage of one feeder: the crew, the panel it leaves (whose
 * position is the item's), then each pole as the user walks the line.
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
    fun setCrew(crew: Crew) = edit { it.copy(crew = crew) }
    fun setMvFeeder(pick: Pick?) = edit { it.copy(mvFeeder = pick) }
    val roster: Roster get() = container.prefs.roster

    fun pole(id: String): PoleReading? = payload.poles.firstOrNull { it.id == id }
    fun number(id: String): Int = payload.poles.indexOfFirst { it.id == id } + 1

    /** A new pole at the end of the walk; it is marked on the map on its page, standing at it. */
    fun addPole(): String {
        val pole = PoleReading()
        edit { it.copy(poles = it.poles + pole) }
        return pole.id
    }

    fun updatePole(id: String, transform: (PoleReading) -> PoleReading) =
        edit { p -> p.copy(poles = p.poles.map { if (it.id == id) transform(it) else it }) }

    /** A pole marked on the map, with the moment and where the user stood. */
    fun placePole(id: String, point: PlacedPoint) = updatePole(id) {
        it.copy(
            latitude = point.latitude, longitude = point.longitude, accuracy = point.accuracy, positionAt = point.at,
            observerLat = point.observer?.latitude, observerLon = point.observer?.longitude,
            observerAccuracy = point.observer?.accuracy
        )
    }

    fun removePole(id: String) {
        val pole = pole(id) ?: return
        val media = (pole.photos + pole.subscribers.flatMap { it.allMedia() }).toSet()
        edit { p -> p.copy(poles = p.poles.filterNot { it.id == id }) }
        dropFiles(media)
    }

    fun subscriber(poleId: String, id: String): Subscriber? = pole(poleId)?.subscribers?.firstOrNull { it.id == id }

    /** A new abnormal subscriber on the pole; `null` once every branch has one. */
    fun addSubscriber(poleId: String): String? {
        val pole = pole(poleId) ?: return null
        if (pole.subscribers.size >= pole.branches) return null
        val subscriber = Subscriber()
        updatePole(poleId) { it.copy(subscribers = it.subscribers + subscriber) }
        return subscriber.id
    }

    fun updateSubscriber(poleId: String, id: String, transform: (Subscriber) -> Subscriber) =
        updatePole(poleId) { p -> p.copy(subscribers = p.subscribers.map { if (it.id == id) transform(it) else it }) }

    /** The house marked on the map, with the moment and where the user stood. */
    fun placeSubscriber(poleId: String, id: String, point: PlacedPoint) = updateSubscriber(poleId, id) {
        it.copy(
            latitude = point.latitude, longitude = point.longitude, accuracy = point.accuracy, positionAt = point.at,
            observerLat = point.observer?.latitude, observerLon = point.observer?.longitude,
            observerAccuracy = point.observer?.accuracy
        )
    }

    fun removeSubscriber(poleId: String, id: String) {
        val media = subscriber(poleId, id)?.allMedia().orEmpty().toSet()
        updatePole(poleId) { p -> p.copy(subscribers = p.subscribers.filterNot { it.id == id }) }
        dropFiles(media)
    }

    /** The files of a removed pole or subscriber; [remove] takes a camera photo's stamped copy along. */
    private fun dropFiles(times: Set<Long>) {
        files.filter { it.capturedAt in times && it.role != FileRole.PHOTO_STAMPED }.forEach { remove(it) }
    }

    fun verdict(): LineVerdict = LineAnalysis.verdict(payload)

    /** What stops saving: the panel plate, the feeder, two measured poles, and every pole complete. */
    fun missing(): List<String> {
        val item = item ?: return emptyList()
        return buildList {
            if (!payload.crew.complete) add(KEY_CREW)
            if (item.plate.isNullOrBlank()) add(KEY_PLATE)
            if (item.latitude == null || item.longitude == null) add(KEY_PANEL_POSITION)
            addAll(LineAnalysis.missing(payload))
        }
    }

    private fun edit(transform: (LinePayload) -> LinePayload) {
        val next = transform(payload)
        payload = next
        // The item's own position is the panel's, recorded on its own page.
        update { it.copy(payload = next.toJson(LineAnalysis.verdict(next)).toString()) }
    }

    companion object {
        const val KEY_PLATE = "plate"
        const val KEY_CREW = "crew"
        const val KEY_PANEL_POSITION = "panel_position"
    }
}
