package ir.ilam.inspection.field.ui.report

import androidx.lifecycle.SavedStateHandle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ir.ilam.inspection.field.FieldContainer
import ir.ilam.inspection.field.data.Crew
import ir.ilam.inspection.field.data.CrewSlot
import ir.ilam.inspection.field.data.FieldKind
import ir.ilam.inspection.field.data.Roster
import ir.ilam.inspection.field.data.Priority
import ir.ilam.inspection.field.data.ReportDetails
import ir.ilam.inspection.field.data.db.FieldItemEntity
import ir.ilam.inspection.field.ui.form.ItemEditor

/** A crypto-mining, illegal-power or burnt-equipment report: one item, its details in the payload. */
class ReportViewModel(container: FieldContainer, val kind: FieldKind, existingId: String?, state: SavedStateHandle? = null) :
    ItemEditor(container, kind, existingId, state) {

    var details by mutableStateOf(ReportDetails())
        private set

    override fun onLoaded(item: FieldItemEntity) {
        details = ReportDetails.fromJson(item.payload)
    }

    fun edit(transform: (ReportDetails) -> ReportDetails) {
        val next = transform(details)
        details = next
        update { it.copy(payload = next.toJson(kind).toString()) }
    }

    fun setDescription(text: String) = update { it.copy(description = text) }
    fun setAddress(text: String) = update { it.copy(address = text) }
    fun setPlate(text: String) = update { it.copy(plate = text) }
    fun setPriority(priority: Priority) = update { it.copy(priority = priority.code) }
    fun setCrew(crew: Crew) = edit { it.copy(crew = crew) }
    val roster: Roster get() = container.prefs.roster
    val hasCrew: Boolean get() = CrewSlot.hasCrew(kind)

    /**
     * What stops the report from being sent, as keys the screen words. The
     * server checks the same on arrival: description and position, and for a
     * burn report at least one piece of equipment.
     */
    fun missing(): List<MissingKey> {
        val current = item ?: return emptyList()
        return buildList {
            if (hasCrew && !details.crew.complete) add(MissingKey.CREW)
            if (current.latitude == null || current.longitude == null) add(MissingKey.POSITION)
            if (current.description.isNullOrBlank()) add(MissingKey.DESCRIPTION)
            if (kind == FieldKind.BURN && !details.hasEquipment) add(MissingKey.EQUIPMENT)
        }
    }
}

enum class MissingKey { CREW, POSITION, DESCRIPTION, EQUIPMENT }
