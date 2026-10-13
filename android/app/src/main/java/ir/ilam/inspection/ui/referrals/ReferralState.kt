package ir.ilam.inspection.ui.referrals

import ir.ilam.inspection.R
import ir.ilam.inspection.data.model.Referral
import ir.ilam.inspection.data.model.ReferralOutcome
import ir.ilam.inspection.ui.theme.Tone

/** The badge for a referral: string resource and tone. */
fun referralState(referral: Referral): Pair<Int, Tone> = when {
    referral.pendingOutcome != null -> R.string.referral_state_waiting_send to Tone.WARNING
    referral.status != Referral.STATUS_REFERRED -> R.string.referral_state_done to Tone.SUCCESS
    referral.isOverdue() -> R.string.referral_state_overdue to Tone.DANGER
    referral.localReportId != null -> R.string.referral_state_in_visit to Tone.INFO
    else -> R.string.referral_state_open to Tone.BRAND
}

/** The light form's choices, with their labels. */
val OUTCOME_LABELS = mapOf(
    ReferralOutcome.CONFIRMED to R.string.referral_outcome_confirmed,
    ReferralOutcome.NOT_FOUND to R.string.referral_outcome_not_found,
    ReferralOutcome.NO_ACCESS to R.string.referral_outcome_no_access,
    ReferralOutcome.OTHER to R.string.referral_outcome_other
)

/** What the server recorded, in words; "case" is the visit made from it. */
fun outcomeLabel(key: String?): Int? = when (key) {
    null -> null
    "case" -> R.string.referral_outcome_case
    else -> ReferralOutcome.of(key)?.let { OUTCOME_LABELS[it] }
}
