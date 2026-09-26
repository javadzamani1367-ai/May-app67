package ir.ilam.inspection.ui.visit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ir.ilam.inspection.data.AppContainer
import ir.ilam.inspection.data.db.AttendeeEntity
import ir.ilam.inspection.data.db.DeviceEntity
import ir.ilam.inspection.data.db.LocationFixEntity
import ir.ilam.inspection.data.db.MediaEntity
import ir.ilam.inspection.data.db.ReportEntity
import ir.ilam.inspection.data.model.AttendeeOrg
import ir.ilam.inspection.data.model.EntryMethod
import ir.ilam.inspection.data.model.LocationSource
import android.net.Uri
import ir.ilam.inspection.data.model.MediaCaptions
import ir.ilam.inspection.data.model.MediaType
import ir.ilam.inspection.data.model.ReportDetail
import ir.ilam.inspection.data.model.TechnicalInput
import ir.ilam.inspection.util.Fix
import ir.ilam.inspection.util.PersianNumbers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

/** Every step of the workflow, the final review included. */
val VISIT_STEP_COUNT = VisitStep.entries.size

/**
 * The field workflow — location, technical, devices, media, owner, review. Every edit is written straight to the database —
 * an expert can be interrupted at any moment and must lose nothing.
 */
class VisitViewModel(private val container: AppContainer, private val reportId: String) : ViewModel() {

    private val reports = container.reportRepository
    private val content = container.contentRepository
    private val media = VisitMediaHandler(container, reportId, viewModelScope)

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
        areaCode: String? = null,
        district: String? = null,
        address: String? = null,
        postalCode: String? = null,
        fileNumber: String? = null,
        billNumber: String? = null,
        usageType: String? = null
    ) = edit { current ->
        current.copy(
            county = county ?: current.county,
            areaCode = areaCode ?: current.areaCode,
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

    /** How and when the position was recorded, for the location card. */
    val locationFix: StateFlow<LocationFixEntity?> = container.database.locationFixDao().observe(reportId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun applyFix(fix: Fix, samples: Int = 1) {
        edit { it.copy(latitude = fix.latitude, longitude = fix.longitude, gpsAccuracy = fix.accuracy) }
        recordSource(LocationSource.SENSOR, samples)
    }

    /**
     * A coordinate the expert did not read off the sensor — picked on the map,
     * or typed in from a figure recorded elsewhere. No accuracy is written,
     * because inventing a metre figure would put a false precision into an
     * official report.
     */
    fun setCoordinates(latitude: Double, longitude: Double, source: LocationSource) {
        edit { it.copy(latitude = latitude, longitude = longitude, gpsAccuracy = null) }
        recordSource(source, samples = 0)
    }

    private fun recordSource(source: LocationSource, samples: Int) {
        viewModelScope.launch {
            container.database.locationFixDao().put(
                LocationFixEntity(reportId, System.currentTimeMillis(), source.code, samples)
            )
        }
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

    /** Step three writes as one block; the parsing and arithmetic live in
     * [TechnicalInput], so a change to the formula never touches this file. */
    fun setTechnical(input: TechnicalInput) = edit { input.applyTo(it) }

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

    /** Capture and import problems are reported by the media handler. */
    val mediaNotice: StateFlow<Int?> = media.notice

    fun clearMediaNotice() = media.clearNotice()

    fun reportPermissionRefused() = media.reportPermissionRefused()

    /** Capture and gallery import live in [VisitMediaHandler]. */
    fun storeCapturedPhoto(raw: File) = media.storeCapturedPhoto(raw)

    fun importFromGallery(uris: List<Uri>, onDone: (added: Int, rejected: Int) -> Unit) =
        media.importFromGallery(uris, onDone)

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
