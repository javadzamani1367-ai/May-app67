package ir.ilam.inspection.ui.centers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.LocalPolice
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import ir.ilam.inspection.R
import ir.ilam.inspection.sync.CenterAdmin
import ir.ilam.inspection.ui.common.AppTextField
import ir.ilam.inspection.ui.common.ChoiceRow
import ir.ilam.inspection.ui.common.NumberField
import ir.ilam.inspection.ui.common.SecondaryButton
import ir.ilam.inspection.ui.common.SectionCard
import ir.ilam.inspection.ui.common.ToneChip
import ir.ilam.inspection.ui.theme.Spacing
import ir.ilam.inspection.ui.theme.Tavan
import ir.ilam.inspection.ui.theme.Tone
import ir.ilam.inspection.util.PersianNumbers

private typealias Change = (CenterAdmin.Draft.() -> CenterAdmin.Draft) -> Unit

/**
 * One reward instruction: the letter, then a card for each reward. A card left
 * empty keeps that reward as the previous instruction set it.
 */
@Composable
internal fun CenterRuleEditor(d: CenterAdmin.Draft, rules: CenterAdmin.Rules, change: Change) {
    SectionCard(title = stringResource(R.string.cr_letter), icon = Icons.Filled.Mail) {
        Column {
            AppTextField(stringResource(R.string.cr_letter_no), d.letterNo, { v -> change { copy(letterNo = v) } })
            AppTextField(stringResource(R.string.cr_letter_id), d.letterId, { v -> change { copy(letterId = v) } })
            AppTextField(stringResource(R.string.cr_letter_date), d.letterDate, { v -> change { copy(letterDate = v) } })
            AppTextField(stringResource(R.string.cr_effective), d.effectiveFrom, { v -> change { copy(effectiveFrom = v) } })
            AppTextField(stringResource(R.string.cr_note), d.note, { v -> change { copy(note = v) } })
            Text(stringResource(R.string.cr_empty_section), style = MaterialTheme.typography.bodySmall)
        }
    }
    SectionCard(title = PersianNumbers.toPersian(1) + ". " + rules.names["finder"].orEmpty(), subtitle = stringResource(R.string.cr_finder_hint), icon = Icons.Filled.Search) {
        Column {
            ChoiceRow(stringResource(R.string.cr_mode), rules.modes.keys.toList(), d.finderMode, optionLabel = { rules.modes[it].orEmpty() },
                onSelect = { m -> change { copy(finderMode = m) } })
            Money(stringResource(R.string.cr_cap), d.finderCap) { v -> change { copy(finderCap = v) } }
            d.tiers.forEachIndexed { i, t ->
                Text(stringResource(R.string.cr_tier_n, PersianNumbers.toPersian(i + 1)), style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = Spacing.sm))
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Box1(Modifier.weight(1f)) { NumberField(stringResource(R.string.cr_from), t.from, { v -> change { copy(tiers = tiers.edit(i) { copy(from = v) }) } }) }
                    Box1(Modifier.weight(1f)) { NumberField(stringResource(R.string.cr_to), t.to, { v -> change { copy(tiers = tiers.edit(i) { copy(to = v) }) } }) }
                }
                Money(stringResource(R.string.cr_per_device), t.amount) { v -> change { copy(tiers = tiers.edit(i) { copy(amount = v) }) } }
            }
        }
    }
    SectionCard(title = PersianNumbers.toPersian(2) + ". " + rules.names["collection"].orEmpty(), subtitle = stringResource(R.string.cr_collection_hint), icon = Icons.Filled.Groups) {
        Column {
            Money(stringResource(R.string.cr_per_device), d.collectionRate) { v -> change { copy(collectionRate = v) } }
            ChoiceRow(stringResource(R.string.cr_count), rules.counts.keys.toList(), d.collectionCount, optionLabel = { rules.counts[it].orEmpty() },
                onSelect = { c -> change { copy(collectionCount = c) } })
            Money(stringResource(R.string.cr_cap), d.collectionCap) { v -> change { copy(collectionCap = v) } }
        }
    }
    SectionCard(title = PersianNumbers.toPersian(3) + ". " + rules.names["enforcement"].orEmpty(), subtitle = stringResource(R.string.cr_enforcement_hint), icon = Icons.Filled.LocalPolice) {
        Column {
            Money(stringResource(R.string.cr_per_device), d.enforcementRate) { v -> change { copy(enforcementRate = v) } }
            ChoiceRow(stringResource(R.string.cr_count), rules.counts.keys.toList(), d.enforcementCount, optionLabel = { rules.counts[it].orEmpty() },
                onSelect = { c -> change { copy(enforcementCount = c) } })
            Money(stringResource(R.string.cr_cap), d.enforcementCap) { v -> change { copy(enforcementCap = v) } }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.padding(vertical = Spacing.sm)) {
                rules.enforcers.forEach { (org, label) ->
                    ToneChip(label, org in d.orgs, Tone.INFO, onClick = { change { copy(orgs = if (org in orgs) orgs - org else orgs + org) } })
                }
            }
        }
    }
    SectionCard(title = PersianNumbers.toPersian(4) + ". " + rules.names["staff"].orEmpty(), subtitle = stringResource(R.string.cr_staff_hint), icon = Icons.Filled.Badge) {
        Column {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Box1(Modifier.weight(1f)) { NumberField(stringResource(R.string.cr_pct), d.staffPct, { v -> change { copy(staffPct = v) } }, decimal = true) }
                Box1(Modifier.weight(1f)) { NumberField(stringResource(R.string.cr_ceo_pct), d.ceoPct, { v -> change { copy(ceoPct = v) } }, decimal = true) }
                Box1(Modifier.weight(1f)) { NumberField(stringResource(R.string.cr_staff_pct), d.staffShare, { v -> change { copy(staffShare = v) } }, decimal = true) }
            }
            ChoiceRow(stringResource(R.string.cr_base), rules.bases.keys.toList(), d.base, optionLabel = { rules.bases[it].orEmpty() },
                onSelect = { b -> change { copy(base = b) } })
            Money(stringResource(R.string.cr_cap_optional), d.staffCap) { v -> change { copy(staffCap = v) } }
            Text(stringResource(R.string.cr_roles), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = Spacing.sm))
            d.roles.forEachIndexed { i, r ->
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Box1(Modifier.weight(2f)) { AppTextField(stringResource(R.string.rf_post), r.label, { v -> change { copy(roles = roles.edit(i) { copy(label = v) }) } }) }
                    Box1(Modifier.weight(1f)) { NumberField(stringResource(R.string.cr_weight), r.weight, { v -> change { copy(roles = roles.edit(i) { copy(weight = v) }) } }, decimal = true) }
                }
            }
            val sum = d.roles.sumOf { it.weight.toDoubleOrNull() ?: 0.0 }
            if (d.roles.isNotEmpty()) Text(stringResource(R.string.rf_pct_sum, PersianNumbers.toPersian(PersianNumbers.plain(sum))),
                color = if (kotlin.math.abs(sum - 100) > 0.01) Tavan.colors.warning.strong else Tavan.colors.success.strong,
                style = MaterialTheme.typography.labelLarge)
            SecondaryButton(stringResource(R.string.cr_add_role), onClick = { change { copy(roles = roles + CenterAdmin.Role()) } },
                icon = Icons.Filled.Add, modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm))
        }
    }
}

/** A rial amount: digits only, shown grouped under the field. */
@Composable
private fun Money(label: String, value: String, onChange: (String) -> Unit) {
    NumberField(label, value, onChange)
    value.toDoubleOrNull()?.takeIf { it > 0 }?.let {
        Text(PersianNumbers.grouped(it) + " " + stringResource(R.string.cr_rial), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun Box1(modifier: Modifier, content: @Composable () -> Unit) = Column(modifier = modifier) { content() }

private fun <T> List<T>.edit(index: Int, block: T.() -> T): List<T> = mapIndexed { i, item -> if (i == index) item.block() else item }
