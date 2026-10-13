package ir.ilam.inspection.ui.referrals

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ir.ilam.inspection.R
import ir.ilam.inspection.container
import ir.ilam.inspection.data.model.Referral
import ir.ilam.inspection.data.model.ReferralPhoto
import ir.ilam.inspection.ui.common.ContainerViewModelFactory
import ir.ilam.inspection.ui.common.PrimaryButton
import ir.ilam.inspection.ui.common.SecondaryButton
import ir.ilam.inspection.ui.common.SectionCard
import ir.ilam.inspection.ui.common.TavanTopBar
import ir.ilam.inspection.ui.common.ValueRow
import ir.ilam.inspection.ui.location.MiniMap
import ir.ilam.inspection.ui.theme.Spacing
import ir.ilam.inspection.ui.theme.Tavan
import ir.ilam.inspection.ui.theme.Tone
import ir.ilam.inspection.util.PersianDate
import ir.ilam.inspection.util.Thumbnails
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * One referral: what the reporter saw, where, and the two ways to answer it —
 * a full «گزارش همکار» visit for crypto, or the light result form.
 */
@Composable
fun ReferralDetailScreen(id: String, onBack: () -> Unit, onOpenCase: (String) -> Unit) {
    val context = LocalContext.current
    val container = context.container
    val viewModel: ReferralsViewModel = viewModel(
        key = "referral-$id",
        factory = remember { ContainerViewModelFactory(container) { ReferralsViewModel(it) } }
    )
    val referral by remember(id) { viewModel.observe(id) }.collectAsState(initial = null)
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    var asking by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        TavanTopBar(title = referral?.trackingCode ?: stringResource(R.string.referrals_title), onBack = onBack)
        if (busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        val r = referral ?: return@Column
        notice?.let { (kind, detail) -> NoticeLine(kind, detail) }
        Column(
            modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screen)
        ) {
            SectionCard(title = r.kindName, icon = Icons.AutoMirrored.Filled.Assignment, tone = Tone.BRAND,
                trailing = { ReferralStatusBadge(r) }) {
                Column {
                    ValueRow(stringResource(R.string.referral_code), r.trackingCode, ltr = true, emphasize = true)
                    ValueRow(stringResource(R.string.referral_reported_at), PersianDate.formatWithTime(r.createdAt))
                    r.dueAt?.let { ValueRow(stringResource(R.string.referral_due_label), PersianDate.format(it)) }
                    ValueRow(stringResource(R.string.referral_reporter), r.reporter ?: stringResource(R.string.referral_reporter_hidden))
                    ValueRow(stringResource(R.string.referral_address), r.address)
                    ValueRow(stringResource(R.string.referral_plate), r.plate, ltr = true)
                    ValueRow(stringResource(R.string.referral_description), r.description)
                    outcomeLabel(r.resultOutcome)?.let { ValueRow(stringResource(R.string.referral_result), stringResource(it)) }
                }
            }
            if (r.latitude != null && r.longitude != null) LocationCard(r)
            if (r.details.isNotEmpty()) {
                SectionCard(title = stringResource(R.string.referral_details), icon = Icons.Filled.FactCheck, tone = Tone.INFO) {
                    Column { r.details.forEach { (label, value) -> ValueRow(label, value) } }
                }
            }
            if (r.photos.isNotEmpty()) PhotosCard(r.photos, viewModel)
            Actions(r, busy, onCreateCase = { viewModel.createCase(r.id, onOpenCase) }, onResult = { asking = true },
                onOpenCase = onOpenCase)
        }
    }

    val current = referral
    if (asking && current != null) {
        ResultDialog(
            onDismiss = { asking = false },
            onSave = { outcome, note ->
                asking = false
                viewModel.submitResult(current.id, outcome, note)
            }
        )
    }
}

@Composable
private fun Actions(r: Referral, busy: Boolean, onCreateCase: () -> Unit, onResult: () -> Unit, onOpenCase: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        when {
            r.pendingOutcome != null -> Text(stringResource(R.string.referral_result_queued),
                color = Tavan.colors.warning.strong, style = MaterialTheme.typography.bodyMedium)
            r.localReportId != null -> PrimaryButton(stringResource(R.string.referral_open_case), { onOpenCase(r.localReportId) },
                modifier = Modifier.fillMaxWidth())
            r.status == Referral.STATUS_REFERRED && r.isCrypto -> {
                PrimaryButton(stringResource(R.string.referral_make_case), onCreateCase, enabled = !busy,
                    modifier = Modifier.fillMaxWidth())
                Text(stringResource(R.string.referral_make_case_hint), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                // A visit that never happened (no access, nothing there) still needs an answer.
                SecondaryButton(stringResource(R.string.referral_result_without_case), onResult, enabled = !busy,
                    modifier = Modifier.fillMaxWidth())
            }
            r.status == Referral.STATUS_REFERRED -> PrimaryButton(stringResource(R.string.referral_record_result), onResult,
                enabled = !busy, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun LocationCard(r: Referral) {
    val context = LocalContext.current
    val lat = r.latitude ?: return
    val lon = r.longitude ?: return
    val navigate = {
        val uri = Uri.parse("geo:$lat,$lon?q=$lat,$lon")
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        Unit
    }
    SectionCard(title = stringResource(R.string.referral_location), icon = Icons.Filled.Place, tone = Tone.INFO) {
        Column {
            MiniMap(lat, lon, r.accuracy, Tavan.colors.info.strong, onClick = navigate,
                modifier = Modifier.fillMaxWidth().height(180.dp).clip(MaterialTheme.shapes.medium))
            SecondaryButton(stringResource(R.string.referral_navigate), navigate, icon = Icons.Filled.TravelExplore,
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm))
        }
    }
}

@Composable
private fun PhotosCard(photos: List<ReferralPhoto>, viewModel: ReferralsViewModel) {
    SectionCard(title = stringResource(R.string.referral_photos), icon = Icons.Filled.PhotoLibrary, tone = Tone.NEUTRAL) {
        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            photos.forEach { photo ->
                val bitmap by produceState<android.graphics.Bitmap?>(null, photo.id) {
                    value = withContext(Dispatchers.IO) { viewModel.photo(photo.id)?.let { Thumbnails.forPhoto(it, 480) } }
                }
                bitmap?.let {
                    Image(it.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop,
                        modifier = Modifier.size(140.dp).clip(MaterialTheme.shapes.medium))
                } ?: Text(stringResource(R.string.referral_photo_unavailable), style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.size(140.dp).padding(Spacing.sm))
            }
        }
    }
}
