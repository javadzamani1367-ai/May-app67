package ir.ilam.inspection.field.ui.feeder

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ir.ilam.inspection.field.FieldContainer
import ir.ilam.inspection.field.data.FeederCheck
import ir.ilam.inspection.field.data.FeederPayload
import ir.ilam.inspection.field.data.FeederReading
import ir.ilam.inspection.field.data.FeederVerdict
import ir.ilam.inspection.field.data.FieldKind
import ir.ilam.inspection.field.data.PhaseReading
import ir.ilam.inspection.field.data.db.FieldItemEntity
import ir.ilam.inspection.field.ui.form.ItemEditor

/**
 * The steps, in the order the readings are taken: the panel itself (place,
 * plate, photos), the main switch, feeders A to D, then the check.
 */
enum class FeederStep { PANEL, MAIN, A, B, C, D, CHECK }

class FeederViewModel(container: FieldContainer, existingId: String?) :
    ItemEditor(container, FieldKind.FEEDER, existingId) {

    var payload by mutableStateOf(FeederPayload())
        private set
    var step by mutableStateOf(FeederStep.PANEL)

    private val tolerancePct get() = container.prefs.ampTolerancePct
    private val toleranceMinA get() = container.prefs.ampToleranceMinA

    override fun onLoaded(item: FieldItemEntity) {
        payload = FeederPayload.fromJson(item.payload)
    }

    fun setPlate(text: String) = update { it.copy(plate = text) }
    fun setAddress(text: String) = update { it.copy(address = text) }

    fun setMain(reading: PhaseReading) = edit { it.copy(main = reading) }

    fun setFeeder(index: Int, reading: FeederReading) =
        edit { it.copy(feeders = it.feeders.mapIndexed { i, f -> if (i == index) reading else f }) }

    fun setNote(text: String) = edit { it.copy(note = text) }

    fun verdict(): FeederVerdict = FeederCheck.verdict(payload.main, payload.feeders, tolerancePct, toleranceMinA)

    /**
     * What stops saving: the place and plate, every reading complete, and —
     * when every feeder was measured or marked absent — sums that agree with
     * the main switch. The server requires the plate and position too.
     */
    fun missing(): List<String> {
        val item = item ?: return emptyList()
        return buildList {
            if (item.latitude == null || item.longitude == null) add(KEY_POSITION)
            if (item.plate.isNullOrBlank()) add(KEY_PLATE)
            addAll(FeederCheck.missing(payload.main, payload.feeders))
            if (verdict().mismatched) add(KEY_MISMATCH)
        }
    }

    /** May the user move past [step]? Only the panel step and the check stop nothing on their own. */
    fun stepComplete(step: FeederStep): Boolean {
        val missing = FeederCheck.missing(payload.main, payload.feeders)
        return when (step) {
            FeederStep.PANEL -> item?.plate?.isNotBlank() == true && item?.latitude != null
            FeederStep.MAIN -> missing.none { it.startsWith("main") }
            FeederStep.A, FeederStep.B, FeederStep.C, FeederStep.D -> {
                val name = step.name
                missing.none { it.endsWith(":$name") }
            }
            FeederStep.CHECK -> true
        }
    }

    private fun edit(transform: (FeederPayload) -> FeederPayload) {
        val next = transform(payload)
        payload = next
        update { it.copy(payload = next.toJson(FeederCheck.verdict(next.main, next.feeders, tolerancePct, toleranceMinA),
            tolerancePct, toleranceMinA).toString()) }
    }

    val tolerance: Pair<Int, Int> get() = tolerancePct to toleranceMinA

    companion object {
        const val KEY_POSITION = "position"
        const val KEY_PLATE = "plate"
        const val KEY_MISMATCH = "mismatch"
    }
}
