package ir.ilam.inspection.ui.centers

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Factory
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Paid
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.ilam.inspection.R
import ir.ilam.inspection.sync.Centers
import ir.ilam.inspection.ui.common.AppTextField
import ir.ilam.inspection.ui.common.BarRow
import ir.ilam.inspection.ui.common.EmptyState
import ir.ilam.inspection.ui.common.GroupHeader
import ir.ilam.inspection.ui.common.HeaderAction
import ir.ilam.inspection.ui.common.KpiTile
import ir.ilam.inspection.ui.common.SecondaryButton
import ir.ilam.inspection.ui.common.SectionCard
import ir.ilam.inspection.ui.common.StatusBadge
import ir.ilam.inspection.ui.common.TavanTopBar
import ir.ilam.inspection.ui.common.ToneChip
import ir.ilam.inspection.ui.fielditems.ProblemLine
import ir.ilam.inspection.ui.theme.Spacing
import ir.ilam.inspection.ui.theme.Tavan
import ir.ilam.inspection.ui.theme.Tone
import ir.ilam.inspection.util.PersianDate
import ir.ilam.inspection.util.PersianNumbers

/**
 * A year of the comprehensive statistics: the figures and charts, then every
 * centre with its conflicts and warnings. Excel in the Tavanir form and Word of
 * the ticked columns; the manager also imports a file and opens the rewards.
 */
@Composable
fun CentersScreen(
    viewModel: CentersViewModel,
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    onImport: () -> Unit,
    onRewards: () -> Unit,
    onRules: () -> Unit = {},
    onSettings: () -> Unit = {}
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { if (state.page == null) viewModel.load() }
    var search by remember { mutableStateOf(state.search) }
    var exporting by remember { mutableStateOf<String?>(null) }
    val page = state.page
    exporting?.let { format ->
        if (page != null) CenterExportDialog(page, format, onDismiss = { exporting = null }) { f, columns ->
            exporting = null
            viewModel.export(context, f, columns)
        }
    }
    Column(modifier = Modifier.fillMaxSize()) {
        TavanTopBar(title = stringResource(R.string.ct_title), subtitle = stringResource(R.string.ct_year, PersianNumbers.toPersian(state.year)),
            onBack = onBack) {
            if (page != null) {
                HeaderAction(Icons.Filled.TableChart, stringResource(R.string.ct_excel), onClick = { exporting = "xlsx" })
                HeaderAction(Icons.Filled.Description, stringResource(R.string.ct_word), onClick = { exporting = "docx" })
            }
            if (page?.manage == true) {
                HeaderAction(Icons.Filled.UploadFile, stringResource(R.string.ct_import), onClick = onImport)
                HeaderAction(Icons.Filled.Paid, stringResource(R.string.ct_rewards), onClick = onRewards)
            }
        }
        if (state.busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        ProblemLine(state.problem)
        state.message?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(Spacing.sm)) }
        LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = Spacing.screen)) {
            item {
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()).padding(vertical = Spacing.xs),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    val now = PersianDate.today().year
                    (now downTo now - 5).forEach { y ->
                        ToneChip(PersianNumbers.toPersian(y), state.year == y, Tone.BRAND, onClick = { viewModel.setYear(y) })
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppTextField(stringResource(R.string.ct_search), search, { search = it }, modifier = Modifier.weight(1f),
                        onDone = { viewModel.setSearch(search) })
                    IconButton(onClick = { viewModel.setSearch(search) }) {
                        Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.ct_search), tint = MaterialTheme.colorScheme.primary)
                    }
                }
                // The manager's own pages, as on the web panel's statistics page.
                if (page?.manage == true) Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    modifier = Modifier.padding(vertical = Spacing.xs)) {
                    SecondaryButton(stringResource(R.string.cr_title), onClick = onRules, icon = Icons.Filled.Gavel, modifier = Modifier.weight(1f))
                    SecondaryButton(stringResource(R.string.cs_title), onClick = onSettings, icon = Icons.Filled.Tune, modifier = Modifier.weight(1f))
                }
                page?.charts?.let { ChartsBlock(it) }
            }
            val rows = page?.rows.orEmpty()
            if (page != null && rows.isEmpty() && !state.busy) {
                item { EmptyState(message = stringResource(R.string.ct_empty), icon = Icons.Filled.Factory) }
            }
            items(rows, key = { it.id }) { row -> RowCard(row, page?.columns.orEmpty()) { onOpen(row.id) } }
        }
    }
}

/** The year's figures and charts; [full] adds the area and status charts and puts each chart in its own card. */
@Composable
internal fun ChartsBlock(charts: Centers.Charts, full: Boolean = true) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.padding(vertical = Spacing.xs)) {
        KpiTile(stringResource(R.string.ct_centers), PersianNumbers.toPersian(charts.centers), Icons.Filled.Factory, Tone.BRAND, Modifier.weight(1f))
        KpiTile(stringResource(R.string.ct_miners), PersianNumbers.grouped(charts.miners), Icons.Filled.Memory, Tone.INFO, Modifier.weight(1f))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.padding(vertical = Spacing.xs)) {
        KpiTile(stringResource(R.string.ct_kw), PersianNumbers.grouped(charts.kw), Icons.Filled.Bolt, Tone.ACCENT, Modifier.weight(1f))
        KpiTile(stringResource(R.string.ct_conflicts), PersianNumbers.toPersian(charts.conflicts), Icons.Filled.Warning,
            if (charts.conflicts > 0) Tone.WARNING else Tone.NEUTRAL, Modifier.weight(1f))
    }
    Bars(stringResource(R.string.ct_chart_months), charts.months, MaterialTheme.colorScheme.primary, money = false, card = full)
    if (full) {
        Bars(stringResource(R.string.ct_chart_areas), charts.areas.take(10), Tavan.colors.info.strong, money = false, card = true)
        Bars(stringResource(R.string.ct_chart_status), charts.status.take(10), Tavan.colors.warning.strong, money = false, card = true)
    }
    Bars(stringResource(R.string.ct_chart_money), charts.money, Tavan.colors.success.strong, money = true, card = full)
}

@Composable
private fun Bars(title: String, bars: List<Centers.Bar>, color: Color, money: Boolean, card: Boolean) {
    if (bars.isEmpty()) return
    val max = bars.maxOf { it.value }.coerceAtLeast(1.0)
    val content: @Composable () -> Unit = {
        Column {
            bars.forEach { bar ->
                BarRow(PersianNumbers.toPersian(bar.label), if (money) PersianNumbers.grouped(bar.value) else PersianNumbers.toPersian(PersianNumbers.plain(bar.value)),
                    (bar.value / max).toFloat(), color)
            }
        }
    }
    if (card) SectionCard(title = title, content = content) else {
        GroupHeader(title)
        content()
    }
}

@Composable
private fun RowCard(row: Centers.Row, columns: List<Centers.Column>, onOpen: () -> Unit) {
    val type = columns.associate { it.key to it.type }
    val v = row.values
    val title = (v["subscriber"] ?: v["subscription"] ?: v["tracking_code"])?.toString().orEmpty()
    val subtitle = listOfNotNull(
        v["county"]?.toString(),
        v["report_date"]?.let { CenterFormat.show("date", it) },
        v["miners"]?.let { stringResource(R.string.ct_miners_n, CenterFormat.show("int", it)) }
    ).joinToString(" · ")
    SectionCard(
        title = PersianNumbers.toPersian(v["row"]?.toString()) + " — " + title,
        subtitle = PersianNumbers.toPersian(subtitle), icon = Icons.Filled.Factory,
        tone = if (row.conflicts.isNotEmpty()) Tone.WARNING else Tone.BRAND,
        modifier = Modifier.clickable(onClick = onOpen),
        trailing = {
            if (row.conflicts.isNotEmpty()) StatusBadge(stringResource(R.string.ct_conflict_n, PersianNumbers.toPersian(row.conflicts.size)), Tone.WARNING)
            if (row.locked) StatusBadge(stringResource(R.string.ct_locked), Tone.NEUTRAL)
        }
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            listOf("subscription", "energy_amount", "collected_amount", "status").forEach { key ->
                val text = CenterFormat.show(type[key] ?: "text", v[key])
                if (text.isNotBlank()) Text(columns.firstOrNull { it.key == key }?.label.orEmpty() + ": " + text,
                    style = MaterialTheme.typography.bodyMedium)
            }
            if (row.warnings.isNotEmpty()) Text(row.warnings.joinToString(" · ") { it.text }, style = MaterialTheme.typography.bodySmall,
                color = Tavan.colors.danger.strong)
        }
    }
}
