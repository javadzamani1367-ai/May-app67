package ir.ilam.inspection.ui.intake

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ir.ilam.inspection.R
import ir.ilam.inspection.data.AppContainer
import ir.ilam.inspection.data.model.County
import ir.ilam.inspection.data.model.ReportType
import ir.ilam.inspection.data.repo.ReportRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class IntakeState(
    val type: ReportType? = null,
    val county: County? = null,
    val district: String = "",
    val address: String = "",
    val subscription: String = "",
    val manualTrackingCode: String = "",
    val reportDate: Long = System.currentTimeMillis(),
    val counties: List<County> = emptyList(),
    val typeError: Int? = null,
    val countyError: Int? = null,
    val addressError: Int? = null,
    val trackingError: Int? = null,
    val saving: Boolean = false,
    val createdId: String? = null
) {
    /** Only the Soragh system supplies its own code; the app makes the rest. */
    val needsManualCode: Boolean get() = type != null && !type.generatesCode
}

class IntakeViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(IntakeState())
    val state: StateFlow<IntakeState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val overrides = container.settingsRepository.settings.first().countyCodeOverrides
            _state.update { it.copy(counties = container.counties.withOverrides(overrides)) }
        }
    }

    fun setType(type: ReportType) = _state.update { it.copy(type = type, typeError = null) }
    fun setCounty(county: County) = _state.update { it.copy(county = county, countyError = null) }
    fun setDistrict(value: String) = _state.update { it.copy(district = value) }
    fun setAddress(value: String) = _state.update { it.copy(address = value, addressError = null) }
    fun setSubscription(value: String) = _state.update { it.copy(subscription = value) }
    fun setManualCode(value: String) = _state.update { it.copy(manualTrackingCode = value, trackingError = null) }
    fun setReportDate(millis: Long) = _state.update { it.copy(reportDate = millis) }

    fun submit() {
        val current = _state.value
        var invalid = false
        if (current.type == null) {
            _state.update { it.copy(typeError = R.string.intake_error_type) }
            invalid = true
        }
        if (current.county == null) {
            _state.update { it.copy(countyError = R.string.intake_error_county) }
            invalid = true
        }
        if (current.address.isBlank()) {
            _state.update { it.copy(addressError = R.string.intake_error_address) }
            invalid = true
        }
        if (current.needsManualCode && current.manualTrackingCode.isBlank()) {
            _state.update { it.copy(trackingError = R.string.intake_error_tracking) }
            invalid = true
        }
        if (invalid) return

        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val result = container.reportRepository.createIntake(
                type = current.type!!,
                countyIndex = current.county?.index,
                countyName = current.county?.name,
                fallbackAreaCode = current.county?.code ?: "01",
                district = current.district,
                address = current.address,
                subscription = current.subscription,
                reportDate = current.reportDate,
                manualTrackingCode = current.manualTrackingCode
            )
            result.fold(
                onSuccess = { id -> _state.update { it.copy(saving = false, createdId = id) } },
                onFailure = { error ->
                    val message = when (error.message) {
                        ReportRepository.DUPLICATE_CODE -> R.string.intake_error_duplicate_tracking
                        else -> R.string.intake_error_tracking
                    }
                    _state.update { it.copy(saving = false, trackingError = message) }
                }
            )
        }
    }
}
