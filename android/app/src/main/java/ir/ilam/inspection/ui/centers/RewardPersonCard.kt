package ir.ilam.inspection.ui.centers

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import ir.ilam.inspection.R
import ir.ilam.inspection.sync.RewardForms
import ir.ilam.inspection.ui.common.AppTextField
import ir.ilam.inspection.ui.common.ChoiceRow
import ir.ilam.inspection.ui.common.NumberField
import ir.ilam.inspection.ui.common.SectionCard
import ir.ilam.inspection.ui.common.StatusBadge
import ir.ilam.inspection.ui.common.ToneChip
import ir.ilam.inspection.ui.theme.Spacing
import ir.ilam.inspection.ui.theme.Tone
import ir.ilam.inspection.util.PersianNumbers

/** Organisations each kind chooses between; the staff form has none. */
private fun orgsOf(kind: String) = when (kind) {
    "enforcement" -> listOf("faraja", "sepah")
    "collection" -> listOf("company", "other")
    else -> emptyList()
}

/**
 * One person of a reward form: the tick that decides whether they share, the
 * name chosen from the people the system knows (typing filters the list), the
 * post, organisation, action and percentage; and the share it comes to.
 */
@Composable
internal fun RewardPersonCard(
    person: RewardForms.Person, form: RewardForms.Form, editable: Boolean,
    onEdit: (RewardForms.Person.() -> RewardForms.Person) -> Unit, onRemove: () -> Unit
) {
    val source = form.sources[person.source].orEmpty()
    SectionCard(
        title = person.name.ifBlank { person.post.ifBlank { stringResource(R.string.rf_new_person) } },
        subtitle = (if (person.isNew) stringResource(R.string.rf_new) + " — " else "") + source,
        icon = Icons.Filled.Person, tone = if (person.on) Tone.SUCCESS else Tone.NEUTRAL,
        trailing = { if (person.on) StatusBadge(PersianNumbers.grouped(person.share), Tone.SUCCESS) }
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = person.on, onCheckedChange = { on -> onEdit { copy(on = on) } }, enabled = editable)
                Text(stringResource(R.string.rf_on), style = MaterialTheme.typography.bodyLarge)
            }
            if (!editable) {
                Text(listOf(person.post, form.orgs[person.org].orEmpty(), person.action).filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium)
                person.pct?.takeIf { !person.isCeo }?.let { Text(stringResource(R.string.rf_pct) + ": " + PersianNumbers.toPersian(PersianNumbers.plain(it)) + "٪") }
                return@Column
            }
            AppTextField(stringResource(R.string.rf_name), person.name, { v -> onEdit { copy(name = v) } })
            Suggestions(person.name, form.names) { v -> onEdit { copy(name = v) } }
            if (person.isCeo) {
                Text(stringResource(R.string.rf_ceo_share), style = MaterialTheme.typography.labelLarge)
            } else {
                AppTextField(stringResource(R.string.rf_post), person.post, { v -> onEdit { copy(post = v) } })
                Suggestions(person.post, form.posts) { v -> onEdit { copy(post = v) } }
            }
            val orgs = orgsOf(form.kind)
            if (orgs.isNotEmpty()) ChoiceRow(stringResource(R.string.rf_org), orgs, person.org.takeIf { it in orgs },
                optionLabel = { form.orgs[it].orEmpty() }, onSelect = { o -> onEdit { copy(org = o) } })
            AppTextField(stringResource(R.string.rf_action), person.action, { v -> onEdit { copy(action = v) } })
            if (form.kind == "staff" && !person.isCeo) {
                // The text is kept as typed, so «۱۲.» stays while the half is still coming.
                var pct by remember(person.key) { mutableStateOf(person.pct?.let { PersianNumbers.plain(it) }.orEmpty()) }
                NumberField(stringResource(R.string.rf_pct), pct, { v ->
                    pct = v
                    onEdit { copy(pct = v.toDoubleOrNull() ?: 0.0) }
                }, decimal = true)
            }
            if (person.source == "manager") TextButton(onClick = onRemove) {
                Text(stringResource(R.string.rf_remove), color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

/** The known values that contain what was typed, as chips; none once the value is one of them. */
@Composable
private fun Suggestions(typed: String, options: List<String>, onPick: (String) -> Unit) {
    if (typed in options) return
    val needle = typed.trim()
    val matches = options.filter { needle.isEmpty() || it.contains(needle) }.take(8)
    if (matches.isEmpty()) return
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        modifier = Modifier.horizontalScroll(rememberScrollState()).padding(bottom = Spacing.xs)) {
        matches.forEach { ToneChip(it, selected = false, tone = Tone.INFO, onClick = { onPick(it) }) }
    }
}
