package ir.ilam.inspection.ui.archive

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import ir.ilam.inspection.R
import ir.ilam.inspection.container
import ir.ilam.inspection.data.db.RewardPersonEntity
import ir.ilam.inspection.ui.common.AppTextField
import ir.ilam.inspection.ui.common.ChoiceRow
import ir.ilam.inspection.ui.common.SecondaryButton
import ir.ilam.inspection.ui.common.SectionCard
import ir.ilam.inspection.ui.common.StatusBadge
import ir.ilam.inspection.ui.common.ToneChip
import ir.ilam.inspection.ui.theme.Spacing
import ir.ilam.inspection.ui.theme.Tone
import ir.ilam.inspection.util.PersianNumbers
import kotlinx.coroutines.launch

private val KINDS = listOf("collection", "enforcement", "staff")

@Composable
private fun kindLabel(kind: String) = stringResource(when (kind) {
    "collection" -> R.string.rp_kind_collection
    "enforcement" -> R.string.rp_kind_enforcement
    else -> R.string.rp_kind_staff
})

@Composable
private fun orgLabel(org: String?) = when (org) {
    "faraja" -> stringResource(R.string.rp_org_faraja)
    "sepah" -> stringResource(R.string.rp_org_sepah)
    else -> ""
}

/**
 * For the expert of a crypto-mining case: who should share its rewards — the
 * company's collection crew, the police and IRGC officers, the holders of the
 * administrative posts. Sent up with the case; the manager confirms them in
 * the centre's reward form, and the names entered here are offered again on
 * the next case.
 */
@Composable
fun RewardPeopleSection(reportId: String) {
    val repository = LocalContext.current.container.contentRepository
    val people by remember(reportId) { repository.observeRewardPeople(reportId) }.collectAsState(initial = emptyList())
    val names by remember { repository.observeKnownNames() }.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var adding by remember { mutableStateOf(false) }
    SectionCard(
        title = stringResource(R.string.rp_title), subtitle = stringResource(R.string.rp_hint), icon = Icons.Filled.Groups,
        tone = Tone.INFO, trailing = { if (people.isNotEmpty()) StatusBadge(PersianNumbers.toPersian(people.size), Tone.INFO) }
    ) {
        Column {
            people.forEach { p ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(p.fullName, style = MaterialTheme.typography.bodyLarge)
                        Text(listOf(kindLabel(p.kind), p.post.orEmpty(), orgLabel(p.org), p.action.orEmpty()).filter { it.isNotBlank() }
                            .joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                    }
                    IconButton(onClick = { scope.launch { repository.removeRewardPerson(p) } }) {
                        Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.rp_remove), tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
            SecondaryButton(stringResource(R.string.rp_add), onClick = { adding = true }, icon = Icons.Filled.Add,
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm))
        }
    }
    if (adding) AddRewardPersonDialog(names, onDismiss = { adding = false }) { kind, name, post, org, action ->
        adding = false
        scope.launch {
            repository.saveRewardPerson(RewardPersonEntity(reportId = reportId, kind = kind, fullName = name, post = post.ifBlank { null },
                org = org, action = action.ifBlank { null }))
        }
    }
}

@Composable
private fun AddRewardPersonDialog(names: List<String>, onDismiss: () -> Unit, onSave: (String, String, String, String?, String) -> Unit) {
    var kind by remember { mutableStateOf("staff") }
    var name by remember { mutableStateOf("") }
    var post by remember { mutableStateOf("") }
    var org by remember { mutableStateOf("faraja") }
    var action by remember { mutableStateOf("") }
    val posts = stringArrayResource(if (kind == "staff") R.array.rp_staff_posts else R.array.rp_field_posts).toList()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.rp_add)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                ChoiceRow(stringResource(R.string.rp_kind), KINDS, kind, optionLabel = { kindLabel(it) }, onSelect = { kind = it })
                AppTextField(stringResource(R.string.rf_name), name, { name = it })
                Picks(name, names) { name = it }
                AppTextField(stringResource(R.string.rf_post), post, { post = it })
                Picks(post, posts) { post = it }
                if (kind == "enforcement") ChoiceRow(stringResource(R.string.rf_org), listOf("faraja", "sepah"), org,
                    optionLabel = { orgLabel(it) }, onSelect = { org = it })
                AppTextField(stringResource(R.string.rf_action), action, { action = it })
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(kind, name.trim(), post.trim(), if (kind == "enforcement") org else if (kind == "collection") "company" else null, action.trim()) },
                enabled = name.isNotBlank()) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}

/** Earlier values containing what was typed, to pick with a tap. */
@Composable
private fun Picks(typed: String, options: List<String>, onPick: (String) -> Unit) {
    if (typed in options) return
    val matches = options.filter { typed.isBlank() || it.contains(typed.trim()) }.take(8)
    if (matches.isEmpty()) return
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.horizontalScroll(rememberScrollState())) {
        matches.forEach { ToneChip(it, selected = false, tone = Tone.INFO, onClick = { onPick(it) }) }
    }
}
