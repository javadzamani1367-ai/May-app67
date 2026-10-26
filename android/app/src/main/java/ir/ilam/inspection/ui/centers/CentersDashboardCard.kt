package ir.ilam.inspection.ui.centers

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import ir.ilam.inspection.R
import ir.ilam.inspection.container
import ir.ilam.inspection.sync.ApiResult
import ir.ilam.inspection.sync.Centers
import ir.ilam.inspection.sync.ServerApi
import ir.ilam.inspection.sync.centers
import ir.ilam.inspection.ui.common.SectionCard
import ir.ilam.inspection.util.PersianDate
import ir.ilam.inspection.util.PersianNumbers

/**
 * This year's comprehensive centre statistics on the manager's dashboard: the
 * figures and the charts, live from the server; nothing when offline. A tap
 * opens the full statistics.
 */
@Composable
fun CentersDashboardCard(onOpen: () -> Unit) {
    val container = LocalContext.current.container
    val year = PersianDate.today().year
    val charts by produceState<Centers.Charts?>(null) {
        val api = ServerApi(container.settingsRepository.current().syncTarget)
        val token = container.vault.serverToken()
        value = if (!api.configured || token == null) null
        else (api.centers(token, year, "", charts = true) as? ApiResult.Ok)?.value?.charts
    }
    val loaded = charts ?: return
    SectionCard(title = stringResource(R.string.ct_title), subtitle = stringResource(R.string.ct_year, PersianNumbers.toPersian(year)),
        modifier = Modifier.clickable(onClick = onOpen)) {
        Column { ChartsBlock(loaded, full = false) }
    }
}
