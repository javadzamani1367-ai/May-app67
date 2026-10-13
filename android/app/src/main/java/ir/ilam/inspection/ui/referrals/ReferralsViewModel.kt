package ir.ilam.inspection.ui.referrals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ir.ilam.inspection.data.AppContainer
import ir.ilam.inspection.data.model.Referral
import ir.ilam.inspection.data.model.ReferralOutcome
import ir.ilam.inspection.data.repo.ReferralRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What the last action said, for one line on the screen. */
enum class ReferralNotice { OFFLINE, REFUSED, NOT_OURS, NOT_A_REFERRAL, SCAN_OFFLINE, RESULT_QUEUED, RESULT_SENT }

/** The referral list and one referral; both screens share this, keyed per screen. */
class ReferralsViewModel(container: AppContainer) : ViewModel() {

    private val repository = container.referralRepository

    val referrals: StateFlow<List<Referral>> = repository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _notice = MutableStateFlow<Pair<ReferralNotice, String?>?>(null)
    val notice: StateFlow<Pair<ReferralNotice, String?>?> = _notice.asStateFlow()

    fun observe(id: String) = repository.observe(id)

    fun dismiss() {
        _notice.value = null
    }

    fun refresh() = work {
        _notice.value = when (val r = repository.refresh()) {
            ReferralRepository.Refresh.Done -> null
            ReferralRepository.Refresh.Offline -> ReferralNotice.OFFLINE to null
            is ReferralRepository.Refresh.Refused -> ReferralNotice.REFUSED to r.message
        }
    }

    fun scanned(text: String?, onFound: (String) -> Unit) = work {
        when (val r = repository.lookup(text)) {
            is ReferralRepository.Lookup.Found -> onFound(r.id)
            ReferralRepository.Lookup.NotOurs -> _notice.value = ReferralNotice.NOT_OURS to null
            ReferralRepository.Lookup.NotAReferral -> _notice.value = ReferralNotice.NOT_A_REFERRAL to null
            ReferralRepository.Lookup.Offline -> _notice.value = ReferralNotice.SCAN_OFFLINE to null
        }
    }

    fun createCase(id: String, onCreated: (String) -> Unit) = work {
        repository.createCase(id)?.let(onCreated)
    }

    fun submitResult(id: String, outcome: ReferralOutcome, note: String) = work {
        val sent = repository.submitResult(id, outcome, note)
        _notice.value = (if (sent) ReferralNotice.RESULT_SENT else ReferralNotice.RESULT_QUEUED) to null
    }

    suspend fun photo(id: String) = repository.photo(id)

    private fun work(block: suspend () -> Unit) {
        viewModelScope.launch {
            _busy.value = true
            try {
                block()
            } finally {
                _busy.value = false
            }
        }
    }
}
