package ir.ilam.inspection.ui.persons

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
import androidx.compose.material.icons.filled.AppRegistration
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.ilam.inspection.R
import ir.ilam.inspection.sync.Persons
import ir.ilam.inspection.ui.common.AppTextField
import ir.ilam.inspection.ui.common.HeaderAction
import ir.ilam.inspection.ui.common.SectionCard
import ir.ilam.inspection.ui.common.StatusBadge
import ir.ilam.inspection.ui.common.TavanTopBar
import ir.ilam.inspection.ui.common.ToneChip
import ir.ilam.inspection.ui.fielditems.ProblemLine
import ir.ilam.inspection.ui.theme.Spacing
import ir.ilam.inspection.ui.theme.Tone
import ir.ilam.inspection.util.PersianNumbers

/**
 * The manager's people, in order rather than one long list: an area at a time,
 * inside it the role groups (executors, experts, units, field and crew), inside
 * those the names. A person working in two areas shows in each with the role
 * they have there. Search covers names, national IDs and account codes.
 */
@Composable
fun PersonsScreen(viewModel: PersonsViewModel, onBack: () -> Unit, onOpen: (String) -> Unit, onNew: () -> Unit, onRequests: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.load() }
    val directory = state.directory
    Column(modifier = Modifier.fillMaxSize()) {
        TavanTopBar(title = stringResource(R.string.ps_title), subtitle = stringResource(R.string.ps_subtitle), onBack = onBack) {
            HeaderAction(Icons.Filled.AppRegistration, stringResource(R.string.ps_requests), onRequests)
            HeaderAction(Icons.Filled.PersonAdd, stringResource(R.string.ps_new), onNew)
        }
        if (state.busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        ProblemLine(state.problem)
        Column(modifier = Modifier.padding(horizontal = Spacing.screen)) {
            AppTextField(stringResource(R.string.ps_search), state.query, viewModel::search)
            if (state.requests.isNotEmpty()) {
                Text(stringResource(R.string.ps_requests_waiting, PersianNumbers.toPersian(state.requests.size)),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.clickable(onClick = onRequests).padding(vertical = Spacing.xs))
            }
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()).padding(vertical = Spacing.xs),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                ToneChip(stringResource(R.string.ps_all_areas), state.area == null, Tone.BRAND, onClick = { viewModel.setArea(null) })
                directory?.catalog?.areas.orEmpty().forEach { (code, name) ->
                    ToneChip(name, state.area == code, Tone.BRAND, onClick = { viewModel.setArea(code) })
                }
            }
        }
        LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = Spacing.screen)) {
            items(directory?.areas.orEmpty(), key = { it.code }) { block -> AreaCard(block, onOpen) }
            if (directory?.province.orEmpty().isNotEmpty()) item {
                SectionCard(title = stringResource(R.string.ps_province), icon = Icons.Filled.Groups) {
                    Column { directory?.province.orEmpty().forEach { PersonRow(it, onOpen) } }
                }
            }
            if (directory?.idle.orEmpty().isNotEmpty()) item {
                SectionCard(title = stringResource(R.string.ps_idle), icon = Icons.Filled.Groups, tone = Tone.NEUTRAL) {
                    Column { directory?.idle.orEmpty().forEach { PersonRow(it, onOpen) } }
                }
            }
        }
    }
}

@Composable
private fun AreaCard(block: Persons.AreaBlock, onOpen: (String) -> Unit) {
    SectionCard(title = block.name, icon = Icons.Filled.Groups,
        trailing = { StatusBadge(PersianNumbers.toPersian(block.count), Tone.BRAND) }) {
        Column {
            block.groups.forEach { group ->
                Text(group.title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = Spacing.sm))
                group.rows.forEach { PersonRow(it, onOpen) }
            }
        }
    }
}

@Composable
private fun PersonRow(row: Persons.Row, onOpen: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(enabled = row.personId != null) { row.personId?.let(onOpen) }
            .padding(vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(row.name, style = MaterialTheme.typography.bodyLarge)
            Text(listOfNotNull(row.kindName.ifBlank { null }, row.code.ifBlank { null }).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (!row.active) StatusBadge(stringResource(R.string.ps_inactive), Tone.DANGER)
    }
}

/** The request queue: register each install to its person (matched by national ID), or refuse it. */
@Composable
fun RequestsScreen(viewModel: PersonsViewModel, onBack: () -> Unit, onPerson: (String) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.load() }
    Column(modifier = Modifier.fillMaxSize()) {
        TavanTopBar(title = stringResource(R.string.ps_requests), subtitle = stringResource(R.string.ps_requests_hint), onBack = onBack)
        if (state.busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        ProblemLine(state.problem)
        LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = Spacing.screen)) {
            items(state.requests, key = { it.id }) { request -> RequestCard(request, state.busy, viewModel, onPerson) }
        }
    }
}

@Composable
private fun RequestCard(request: Persons.Request, busy: Boolean, viewModel: PersonsViewModel, onPerson: (String) -> Unit) {
    SectionCard(title = request.name ?: "—", subtitle = request.appName, icon = Icons.Filled.AppRegistration) {
        Column {
            Text(stringResource(R.string.ps_request_line, PersianNumbers.toPersian(request.nationalId ?: "—"),
                request.deviceCode.chunked(4).joinToString("-")), style = MaterialTheme.typography.bodySmall)
            request.note?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            Text(request.personName?.let { stringResource(R.string.ps_request_known, it) } ?: stringResource(R.string.ps_request_new),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.padding(top = Spacing.sm)) {
                ToneChip(stringResource(R.string.ps_request_register), false, Tone.SUCCESS,
                    onClick = { if (!busy) viewModel.decide(request, register = true, onPerson = onPerson) })
                ToneChip(stringResource(R.string.ps_request_reject), false, Tone.DANGER,
                    onClick = { if (!busy) viewModel.decide(request, register = false, onPerson = {}) })
            }
        }
    }
}
