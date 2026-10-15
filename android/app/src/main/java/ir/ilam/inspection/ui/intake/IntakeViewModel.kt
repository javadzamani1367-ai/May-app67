package ir.ilam.inspection.ui.intake

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ir.ilam.inspection.R
import ir.ilam.inspection.data.AppContainer
import ir.ilam.inspection.data.CountyCatalog
import ir.ilam.inspection.data.model.County
import ir.ilam.inspection.data.model.ReportType
import ir.ilam.inspection.data.repo.ReportRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

class IntakeViewModel(
    private val container: AppContainer,
    /**
     * The typed fields, kept in the screen's saved state: when Android closes
     * the app in the background (a call, a message) the form comes back as it
     * was, not empty.
     */
    private val saved: SavedStateHandle? = null
) : ViewModel() {

    private val _state = MutableStateFlow(IntakeState())
    val state: StateFlow<IntakeState> = _state.asStateFlow()

    init {
        val counties = container.counties.defaults
        _state.update { current ->
            val s = saved ?: return@update current.copy(counties = counties)
            current.copy(
                counties = counties,
                type = s.get<Int>(K_TYPE)?.let { code -> ReportType.entries.firstOrNull { it.code == code } },
                county = s.get<Int>(K_COUNTY)?.let { index -> counties.firstOrNull { it.index == index } },
                district = s[K_DISTRICT] ?: "",
                address = s[K_ADDRESS] ?: "",
                subscription = s[K_SUBSCRIPTION] ?: "",
                manualTrackingCode = s[K_CODE] ?: "",
                reportDate = s[K_DATE] ?: current.reportDate
            )
        }
    }

    /** Anything typed or chosen: leaving then asks first. */
    val dirty: Boolean
        get() = _state.value.let {
            it.type != null || it.county != null || it.district.isNotBlank() || it.address.isNotBlank() ||
                it.subscription.isNotBlank() || it.manualTrackingCode.isNotBlank()
        }

    private fun keep(key: String, value: Any?) {
        saved?.set(key, value)
    }

    fun setType(type: ReportType) {
        keep(K_TYPE, type.code)
        _state.update { it.copy(type = type, typeError = null) }
    }
    fun setCounty(county: County) {
        keep(K_COUNTY, county.index)
        _state.update { it.copy(county = county, countyError = null) }
    }
    fun setDistrict(value: String) {
        keep(K_DISTRICT, value)
        _state.update { it.copy(district = value) }
    }
    fun setAddress(value: String) {
        keep(K_ADDRESS, value)
        _state.update { it.copy(address = value, addressError = null) }
    }
    fun setSubscription(value: String) {
        keep(K_SUBSCRIPTION, value)
        _state.update { it.copy(subscription = value) }
    }
    fun setManualCode(value: String) {
        keep(K_CODE, value)
        _state.update { it.copy(manualTrackingCode = value, trackingError = null) }
    }
    fun setReportDate(millis: Long) {
        keep(K_DATE, millis)
        _state.update { it.copy(reportDate = millis) }
    }

    private companion object {
        const val K_TYPE = "type"
        const val K_COUNTY = "county"
        const val K_DISTRICT = "district"
        const val K_ADDRESS = "address"
        const val K_SUBSCRIPTION = "subscription"
        const val K_CODE = "code"
        const val K_DATE = "date"
    }

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
                countyName = current.county?.name,
                areaCode = current.county?.code ?: CountyCatalog.FALLBACK_CODE,
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
