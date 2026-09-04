package ir.ilam.inspection.ui.visit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ir.ilam.inspection.data.AppContainer
import ir.ilam.inspection.data.db.AttendeeEntity
import ir.ilam.inspection.data.db.DeviceEntity
import ir.ilam.inspection.data.db.MediaEntity
import ir.ilam.inspection.data.db.ReportEntity
import ir.ilam.inspection.data.model.AttendeeOrg
import ir.ilam.inspection.data.model.EntryMethod
import ir.ilam.inspection.data.model.MediaType
import ir.ilam.inspection.data.model.ReportDetail
import ir.ilam.inspection.util.Fix
import ir.ilam.inspection.util.PersianNumbers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

const val VISIT_STEP_COUNT = 5

/**
 * The five step field wizard. Every edit is written straight to the database —
 * an expert can be interrupted at any moment and must lose nothing.
 */
class VisitViewModel(private val container: AppContainer, private val reportId: String) : ViewModel() {

    private val reports = container.reportRepository
    private val content = container.contentRepository

    val detail: StateFlow<ReportDetail?> = reports.observeDetail(reportId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _step = MutableStateFlow(0)
    val step: StateFlow<Int> = _step.asStateFlow()

    private val _missing = MutableStateFlow<List<Int>>(emptyList())
    val missing: StateFlow<List<Int>> = _missing.asStateFlow()

    private val _finished = MutableStateFlow(false)
    val finished: StateFlow<Boolean> = _finished.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    fun goToStep(index: Int) {
        _step.value = index.coerceIn(0, VISIT_STEP_COUNT - 1)
    }

    fun next() = goToStep(_step.value + 1)
    fun previous() = goToStep(_step.value - 1)

    private fun edit(mutate: (ReportEntity) -> ReportEntity) {
        viewModelScope.launch { reports.edit(reportId) { mutate(it) } }
    }

    // ---- step 1: location -------------------------------------------------

    fun setLocationField(
        county: String? = null,
        district: String? = null,
        address: String? = null,
        postalCode: String? = null,
        fileNumber: String? = null,
        billNumber: String? = null,
        usageType: String? = null
    ) = edit { current ->
        current.copy(
            county = county ?: current.county,
            district = district ?: current.district,
            address = address ?: current.address,
            postalCode = postalCode ?: current.postalCode,
            fileNumber = fileNumber ?: current.fileNumber,
            billNumber = billNumber ?: current.billNumber,
            usageType = usageType ?: current.usageType
        )
    }

    /** Setting the subscription number is what turns a temporary code final. */
    fun setSubscriptionNumber(value: String) {
        viewModelScope.launch {
            reports.edit(reportId) { it.copy(subscriptionNumber = value.trim().ifBlank { null }) }
            reports.assignFinalCode(reportId)
        }
    }

    fun applyFix(fix: Fix) = edit {
        it.copy(latitude = fix.latitude, longitude = fix.longitude, gpsAccuracy = fix.accuracy)
    }

    // ---- step 2: owner ----------------------------------------------------

    fun setOwner(
        name: String? = null,
        nationalId: String? = null,
        phone: String? = null,
        relation: String? = null
    ) = edit { current ->
        current.copy(
            ownerName = name ?: current.ownerName,
            ownerNationalId = nationalId ?: current.ownerNationalId,
            ownerPhone = phone ?: current.ownerPhone,
            ownerRelation = relation ?: current.ownerRelation
        )
    }

    // ---- step 3: technical ------------------------------------------------

    fun setTechnical(
        meterAmperage: String? = null,
        measuredAmperage: String? = null,
        connectionType: String? = null,
        sealStatus: String? = null
    ) = edit { current ->
        current.copy(
            meterAmperage = meterAmperage?.let { PersianNumbers.parseDoubleOrNull(it) }
                ?: current.meterAmperage,
            measuredAmperage = measuredAmperage?.let { PersianNumbers.parseDoubleOrNull(it) }
                ?: current.measuredAmperage,
            connectionType = connectionType ?: current.connectionType,
            sealStatus = sealStatus ?: current.sealStatus
        )
    }

    // ---- step 4: devices and attendees ------------------------------------

    private val _deviceError = MutableStateFlow(false)
    val deviceError: StateFlow<Boolean> = _deviceError.asStateFlow()

    fun addDevice(model: String, serial: String, power: String, method: EntryMethod, note: String) {
        viewModelScope.launch {
            val added = content.addDevice(
                reportId = reportId,
                model = model,
                serial = serial,
                powerWatt = PersianNumbers.parseDoubleOrNull(power),
                entryMethod = method,
                note = note
            )
            _deviceError.value = !added
        }
    }

    fun clearDeviceError() {
        _deviceError.value = false
    }

    fun removeDevice(device: DeviceEntity) {
        viewModelScope.launch { content.removeDevice(device) }
    }

    fun addAttendee(org: AttendeeOrg, name: String, position: String, orgName: String) {
        viewModelScope.launch { content.addAttendee(reportId, org, name, position, orgName) }
    }

    fun removeAttendee(attendee: AttendeeEntity) {
        viewModelScope.launch { content.removeAttendee(attendee) }
    }

    // ---- step 5: media and narrative --------------------------------------

    fun addMedia(file: File, type: MediaType, capturedAt: Long) {
        viewModelScope.launch {
            val report = detail.value?.report
            content.addMedia(
                reportId = reportId,
                file = file,
                type = type,
                capturedAt = capturedAt,
                latitude = report?.latitude,
                longitude = report?.longitude
            )
        }
    }

    fun setCaption(media: MediaEntity, caption: String) {
        viewModelScope.launch { content.setCaption(media, caption) }
    }

    fun removeMedia(media: MediaEntity) {
        viewModelScope.launch { content.removeMedia(media) }
    }

    fun setNarrative(description: String? = null, actionsTaken: String? = null) = edit { current ->
        current.copy(
            description = description ?: current.description,
            actionsTaken = actionsTaken ?: current.actionsTaken
        )
    }

    // ---- completion -------------------------------------------------------

    fun finish() {
        _busy.value = true
        viewModelScope.launch {
            val missing = reports.markVisited(reportId)
            _missing.value = missing
            _busy.value = false
            if (missing.isEmpty()) _finished.value = true
        }
    }

    fun dismissMissing() {
        _missing.value = emptyList()
    }
}
