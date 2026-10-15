package ir.ilam.inspection.field.ui.report

import androidx.lifecycle.SavedStateHandle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ir.ilam.inspection.field.FieldContainer
import ir.ilam.inspection.field.data.FieldKind
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

    /**
     * What stops the report from being sent, as keys the screen words. The
     * server checks the same on arrival: description and position, and for a
     * burn report at least one piece of equipment.
     */
    fun missing(): List<MissingKey> {
        val current = item ?: return emptyList()
        return buildList {
            if (current.latitude == null || current.longitude == null) add(MissingKey.POSITION)
            if (current.description.isNullOrBlank()) add(MissingKey.DESCRIPTION)
            if (kind == FieldKind.BURN && !details.hasEquipment) add(MissingKey.EQUIPMENT)
        }
    }
}

enum class MissingKey { POSITION, DESCRIPTION, EQUIPMENT }
