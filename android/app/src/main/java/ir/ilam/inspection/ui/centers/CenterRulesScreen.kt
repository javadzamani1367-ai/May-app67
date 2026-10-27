package ir.ilam.inspection.ui.centers

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.ilam.inspection.R
import ir.ilam.inspection.ui.common.BottomActionBar
import ir.ilam.inspection.ui.common.EmptyState
import ir.ilam.inspection.ui.common.PrimaryButton
import ir.ilam.inspection.ui.common.SecondaryButton
import ir.ilam.inspection.ui.common.SectionCard
import ir.ilam.inspection.ui.common.TavanTopBar
import ir.ilam.inspection.ui.fielditems.ProblemLine
import ir.ilam.inspection.ui.theme.Spacing
import ir.ilam.inspection.ui.theme.Tavan
import ir.ilam.inspection.ui.theme.Tone
import ir.ilam.inspection.util.PersianNumbers

/**
 * The reward instructions, as on the web panel: each letter with its number,
 * id, date and effective date, setting all four rewards; tap one to correct it,
 * or write a new one, which starts from the latest so only changes are typed.
 */
@Composable
fun CenterRulesScreen(viewModel: CenterAdminViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.loadRules() }
    var deleting by remember { mutableStateOf<String?>(null) }
    val draft = state.draft
    BackHandler(enabled = draft != null) { viewModel.closeDraft() }
    Column(modifier = Modifier.fillMaxSize().imePadding()) {
        TavanTopBar(title = stringResource(R.string.cr_title),
            subtitle = stringResource(if (draft == null) R.string.cr_subtitle else if (draft.id == null) R.string.cr_new else R.string.cr_edit),
            onBack = { if (draft != null) viewModel.closeDraft() else onBack() })
        if (state.busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        ProblemLine(state.problem)
        state.message?.let { Text(stringResource(it), color = Tavan.colors.success.strong, modifier = Modifier.padding(horizontal = Spacing.screen)) }
        val rules = state.rules
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.screen)) {
            if (draft != null && rules != null) {
                CenterRuleEditor(draft, rules, viewModel::change)
                return@Column
            }
            Text(stringResource(R.string.cr_explain), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = Spacing.sm))
            if (rules != null && rules.rules.isEmpty()) EmptyState(message = stringResource(R.string.cr_empty), icon = Icons.Filled.Gavel)
            rules?.rules?.forEach { rule ->
                SectionCard(title = PersianNumbers.toPersian(rule.text), icon = Icons.Filled.Gavel, tone = Tone.BRAND,
                    subtitle = rule.kinds.mapNotNull { rules.names[it] }.joinToString(" · "),
                    modifier = Modifier.clickable { viewModel.edit(rule) },
                    trailing = {
                        IconButton(onClick = { deleting = rule.id }) {
                            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.cr_delete), tint = MaterialTheme.colorScheme.error)
                        }
                    }) {
                    Text(rule.note ?: stringResource(R.string.cr_tap_edit), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        BottomActionBar {
            if (draft != null) {
                SecondaryButton(stringResource(R.string.action_cancel), onClick = { viewModel.closeDraft() }, modifier = Modifier.weight(1f))
                PrimaryButton(stringResource(R.string.cr_save), onClick = { viewModel.saveDraft() }, icon = Icons.Filled.Save,
                    busy = state.busy, tone = Tone.SUCCESS, modifier = Modifier.weight(1f))
            } else {
                PrimaryButton(stringResource(R.string.cr_new), onClick = { viewModel.startNew() }, icon = Icons.Filled.Add,
                    enabled = rules != null, modifier = Modifier.fillMaxWidth())
            }
        }
    }
    deleting?.let { id ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.cr_delete)) },
            text = { Text(stringResource(R.string.cr_delete_confirm)) },
            confirmButton = { TextButton(onClick = { deleting = null; viewModel.delete(id) }) {
                Text(stringResource(R.string.cr_delete), color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.action_cancel)) } }
        )
    }
}
