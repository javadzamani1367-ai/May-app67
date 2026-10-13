package ir.ilam.inspection.ui.referrals

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDirection
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import androidx.activity.compose.rememberLauncherForActivityResult
import ir.ilam.inspection.R
import ir.ilam.inspection.container
import ir.ilam.inspection.data.model.Referral
import ir.ilam.inspection.ui.capture.ScanActivity
import ir.ilam.inspection.ui.common.AppCard
import ir.ilam.inspection.ui.common.ContainerViewModelFactory
import ir.ilam.inspection.ui.common.EmptyState
import ir.ilam.inspection.ui.common.HeaderAction
import ir.ilam.inspection.ui.common.StatusBadge
import ir.ilam.inspection.ui.common.TavanTopBar
import ir.ilam.inspection.ui.theme.Spacing
import ir.ilam.inspection.ui.theme.Tavan
import ir.ilam.inspection.util.PersianDate

/**
 * «ارجاع‌های من»: the field reports the office referred to this expert. Read
 * from the phone's own copy, so the list is there in a village with no signal;
 * refreshed from the server on opening and with the pull button. The QR on the
 * printed referral sheet opens a referral straight away.
 */
@Composable
fun ReferralsScreen(onBack: () -> Unit, onOpen: (String) -> Unit) {
    val container = LocalContext.current.container
    val viewModel: ReferralsViewModel = viewModel(
        factory = remember { ContainerViewModelFactory(container) { ReferralsViewModel(it) } }
    )
    val referrals by viewModel.referrals.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    val scanner = rememberLauncherForActivityResult(ScanContract()) { result ->
        if (result.contents != null) viewModel.scanned(result.contents, onOpen)
    }
    LaunchedEffect(Unit) { viewModel.refresh() }

    Column(modifier = Modifier.fillMaxSize()) {
        TavanTopBar(title = stringResource(R.string.referrals_title), onBack = onBack) {
            HeaderAction(Icons.Filled.QrCodeScanner, stringResource(R.string.referrals_scan), onClick = {
                scanner.launch(qrOptions())
            })
            HeaderAction(Icons.Filled.Refresh, stringResource(R.string.referrals_refresh), onClick = { viewModel.refresh() })
        }
        if (busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        notice?.let { (kind, detail) -> NoticeLine(kind, detail) }
        if (referrals.isEmpty() && !busy) {
            EmptyState(
                message = stringResource(R.string.referrals_empty),
                icon = Icons.AutoMirrored.Filled.Assignment
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(Spacing.screen),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                items(referrals, key = { it.id }) { referral -> ReferralCard(referral) { onOpen(referral.id) } }
            }
        }
    }
}

@Composable
private fun ReferralCard(referral: Referral, onClick: () -> Unit) {
    AppCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Column(modifier = Modifier.padding(Spacing.lg)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(
                    referral.trackingCode,
                    style = MaterialTheme.typography.titleMedium.copy(textDirection = TextDirection.Ltr),
                    modifier = Modifier.weight(1f)
                )
                ReferralStatusBadge(referral)
            }
            Text(referral.kindName, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            referral.address?.let { Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 2) }
            referral.dueAt?.let {
                Text(
                    stringResource(R.string.referrals_due, PersianDate.format(it)),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (referral.isOverdue()) Tavan.colors.danger.strong else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** One badge that says the one thing that matters about a referral now. */
@Composable
fun ReferralStatusBadge(referral: Referral) {
    val (text, tone) = referralState(referral)
    StatusBadge(text = stringResource(text), tone = tone, solid = referral.isOverdue())
}

@Composable
fun NoticeLine(kind: ReferralNotice, detail: String?) {
    val text = when (kind) {
        ReferralNotice.OFFLINE -> stringResource(R.string.referrals_offline)
        ReferralNotice.REFUSED -> stringResource(R.string.referrals_refused, detail.orEmpty())
        ReferralNotice.NOT_OURS -> stringResource(R.string.referrals_not_ours)
        ReferralNotice.NOT_A_REFERRAL -> stringResource(R.string.referrals_not_a_referral)
        ReferralNotice.SCAN_OFFLINE -> stringResource(R.string.referrals_scan_offline)
        ReferralNotice.RESULT_QUEUED -> stringResource(R.string.referral_result_queued)
        ReferralNotice.RESULT_SENT -> stringResource(R.string.referral_result_sent)
    }
    val tone = if (kind == ReferralNotice.RESULT_SENT) Tavan.colors.success else Tavan.colors.warning
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = tone.strong,
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.screen, vertical = Spacing.sm)
    )
}

/** Portrait, QR only, no beep; the same capture window the device scanner uses. */
private fun qrOptions(): ScanOptions = ScanOptions()
    .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
    .setBeepEnabled(false)
    .setOrientationLocked(true)
    .setCaptureActivity(ScanActivity::class.java)
