package ir.ilam.inspection.ui.persons

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import ir.ilam.inspection.R
import ir.ilam.inspection.sync.Persons
import ir.ilam.inspection.ui.common.PrimaryButton
import ir.ilam.inspection.ui.theme.Spacing

private const val PROVINCE_HQ = "-:illegal_hq"
private const val INSPECT = 1
private const val REPORT = 2

/**
 * The person's areas and roles: each area folds open (those where the person
 * already works are open), with the role groups inside and, for the field app's
 * roles, the inspect and report permissions. Saving makes an account for every
 * new tick and closes the account of every tick taken away.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PersonTicks(detail: Persons.Detail, busy: Boolean, onSave: (Set<String>, Map<String, Int>) -> Unit) {
    val active = detail.accounts.filter { it.active }
    val ticks = remember(detail) { mutableStateMapOf<String, Boolean>().apply { active.forEach { put(key(it), true) } } }
    val perms = remember(detail) { mutableStateMapOf<String, Int>().apply { active.forEach { put(key(it), it.permissions) } } }
    val fieldKinds = detail.catalog.kinds.filter { it.field }.map { it.key }.toSet()
    val names = detail.catalog.kinds.associate { it.key to it.name }

    Column {
        TickRow(stringResource(R.string.ps_illegal_hq), ticks[PROVINCE_HQ] == true) { ticks[PROVINCE_HQ] = it }
        detail.catalog.areas.forEach { (code, label) ->
            var open by remember(detail) { mutableStateOf(active.any { it.area == code }) }
            val inArea = names.keys.filter { ticks["$code:$it"] == true }.mapNotNull { names[it] }
            Row(modifier = Modifier.fillMaxWidth().clickable { open = !open }.padding(vertical = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(label, style = MaterialTheme.typography.titleSmall)
                    if (inArea.isNotEmpty()) Text(inArea.joinToString("، "), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary)
                }
                Icon(if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null)
            }
            if (open) detail.catalog.groups.forEach { (title, kinds) ->
                Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow {
                    kinds.forEach { kind ->
                        val key = "$code:$kind"
                        TickRow(names[kind].orEmpty(), ticks[key] == true) { ticks[key] = it }
                        if (kind in fieldKinds && ticks[key] == true) {
                            val bits = perms[key] ?: (INSPECT or REPORT)
                            TickRow(stringResource(R.string.ps_perm_inspect), bits and INSPECT != 0) { on ->
                                perms[key] = if (on) bits or INSPECT else bits and INSPECT.inv()
                            }
                            TickRow(stringResource(R.string.ps_perm_report), bits and REPORT != 0) { on ->
                                perms[key] = if (on) bits or REPORT else bits and REPORT.inv()
                            }
                        }
                    }
                }
            }
        }
        Text(stringResource(R.string.ps_ticks_hint), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = Spacing.sm))
        PrimaryButton(stringResource(R.string.ps_save_roles), enabled = !busy, icon = Icons.Filled.Save,
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
            onClick = {
                val chosen = ticks.filterValues { it }.keys
                onSave(chosen, chosen.associateWith { perms[it] ?: (INSPECT or REPORT) })
            })
    }
}

private fun key(account: Persons.Account) = (account.area ?: "-") + ":" + account.kind

@Composable
private fun TickRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { onChange(!checked) }) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(end = Spacing.sm))
    }
}
