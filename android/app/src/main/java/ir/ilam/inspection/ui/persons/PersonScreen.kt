package ir.ilam.inspection.ui.persons

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.ilam.inspection.R
import ir.ilam.inspection.sync.Persons
import ir.ilam.inspection.ui.common.AppTextField
import ir.ilam.inspection.ui.common.NumberField
import ir.ilam.inspection.ui.common.PrimaryButton
import ir.ilam.inspection.ui.common.SectionCard
import ir.ilam.inspection.ui.common.StatusBadge
import ir.ilam.inspection.ui.common.TavanTopBar
import ir.ilam.inspection.ui.common.ToneChip
import ir.ilam.inspection.ui.common.ValueRow
import ir.ilam.inspection.ui.fielditems.ProblemLine
import ir.ilam.inspection.ui.theme.Spacing
import ir.ilam.inspection.ui.theme.Tone
import ir.ilam.inspection.util.NationalId
import ir.ilam.inspection.util.PersianNumbers

/**
 * One person: details (national ID is what makes them one person), their areas
 * and roles, the install code of each app, and their work in each area. A new
 * person ([personId] null) starts with the details only.
 */
@Composable
fun PersonScreen(viewModel: PersonsViewModel, personId: String?, onBack: () -> Unit, onCreated: (String) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(personId) { if (personId != null) viewModel.open(personId) }
    val detail = state.detail?.takeIf { it.person.id == personId }
    Column(modifier = Modifier.fillMaxSize()) {
        TavanTopBar(title = detail?.person?.name ?: stringResource(R.string.ps_new),
            subtitle = detail?.person?.let { stringResource(R.string.ps_number, PersianNumbers.toPersian(it.number)) }, onBack = onBack)
        if (state.busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        ProblemLine(state.problem)
        Column(modifier = Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.screen)) {
            if (personId == null || detail != null) DetailsCard(detail?.person, state.busy) { fields ->
                viewModel.save(personId, fields) { saved -> if (personId == null) onCreated(saved) else viewModel.open(saved) }
            }
            if (detail != null) {
                SectionCard(title = stringResource(R.string.ps_roles), icon = Icons.Filled.Tune) {
                    PersonTicks(detail, state.busy) { ticks, perms -> viewModel.applyTicks(detail.person.id, ticks, perms) }
                }
                SectionCard(title = stringResource(R.string.ps_accounts), icon = Icons.Filled.Key) {
                    Column { detail.accounts.forEach { AccountRow(it) { viewModel.resetPassword(detail.person.id, it) } } }
                }
                SectionCard(title = stringResource(R.string.ps_devices), subtitle = stringResource(R.string.ps_devices_hint),
                    icon = Icons.Filled.PhoneAndroid) {
                    Column { detail.catalog.apps.forEach { (app, name) ->
                        DeviceRow(name, detail.devices[app].orEmpty()) { viewModel.setDevice(detail.person.id, app, it) }
                    } }
                }
                SectionCard(title = stringResource(R.string.ps_work), icon = Icons.Filled.Insights) {
                    Column { detail.work.forEach { WorkBlock(it) } }
                }
            }
        }
    }
    if (state.made.isNotEmpty()) PasswordsDialog(state.made, onDismiss = viewModel::passwordsShown)
}

@Composable
private fun DetailsCard(person: Persons.Person?, busy: Boolean, onSave: (Map<String, String>) -> Unit) {
    var name by remember(person) { mutableStateOf(person?.name.orEmpty()) }
    var nationalId by remember(person) { mutableStateOf(person?.nationalId.orEmpty()) }
    var phone by remember(person) { mutableStateOf(person?.phone.orEmpty()) }
    var home by remember(person) { mutableStateOf(person?.homeArea.orEmpty()) }
    var note by remember(person) { mutableStateOf(person?.note.orEmpty()) }
    val badId = nationalId.isNotBlank() && !NationalId.isValid(nationalId)
    SectionCard(title = stringResource(R.string.ps_details), icon = Icons.Filled.Badge) {
        Column {
            AppTextField(stringResource(R.string.reg_full_name), name, { name = it })
            NumberField(stringResource(R.string.reg_national_id), nationalId, { nationalId = it },
                error = if (badId) stringResource(R.string.reg_bad_national_id) else null)
            NumberField(stringResource(R.string.reg_phone), phone, { phone = it })
            NumberField(stringResource(R.string.ps_home_area), home, { home = it.take(3) })
            AppTextField(stringResource(R.string.ps_note), note, { note = it }, singleLine = false)
            PrimaryButton(stringResource(R.string.action_save), enabled = !busy && name.isNotBlank() && !badId, icon = Icons.Filled.Save,
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
                onClick = { onSave(mapOf("full_name" to name, "national_id" to NationalId.normalized(nationalId), "phone" to phone,
                    "home_area" to PersianNumbers.toLatin(home), "note" to note, "active" to "1",
                    "auto_bind" to if (person?.autoBind == true) "1" else "0")) })
        }
    }
}

@Composable
private fun AccountRow(account: Persons.Account, onReset: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xs)) {
        Column(modifier = Modifier.weight(1f)) {
            Text(account.areaLabel + " · " + account.kindName, style = MaterialTheme.typography.bodyMedium)
            Text(account.code, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (!account.active) StatusBadge(stringResource(R.string.ps_inactive), Tone.DANGER)
        else ToneChip(stringResource(R.string.ps_new_password), false, Tone.WARNING, onClick = onReset)
    }
}

@Composable
private fun DeviceRow(app: String, current: String, onSave: (String) -> Unit) {
    var code by remember(current) { mutableStateOf(current.chunked(4).joinToString("-")) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        AppTextField(app, code, { code = it }, modifier = Modifier.weight(1f), ltr = true)
        TextButton(onClick = { onSave(code) }, enabled = code.replace("-", "") != current) { Text(stringResource(R.string.action_save)) }
    }
}

@Composable
private fun WorkBlock(work: Persons.AreaWork) {
    Text(work.name + if (work.home) " · " + stringResource(R.string.ps_home_area) else "", style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(top = Spacing.sm))
    work.roles.forEach { role ->
        ValueRow(role.kindName, (role.figures.map { it.label + ": " + PersianNumbers.toPersian(it.value) } +
            role.scores.map { (kind, score) -> stringResource(R.string.ps_score, kind, score?.let { PersianNumbers.toPersian(it) } ?: "—") })
            .joinToString("، "))
    }
}

@Composable
private fun PasswordsDialog(made: List<Persons.Made>, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ps_passwords_title)) },
        text = {
            Column {
                Text(stringResource(R.string.ps_passwords_hint), style = MaterialTheme.typography.bodySmall)
                made.forEach { ValueRow(it.area + " · " + it.kind, it.code + "   " + it.password, ltr = true) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_confirm)) } }
    )
}
