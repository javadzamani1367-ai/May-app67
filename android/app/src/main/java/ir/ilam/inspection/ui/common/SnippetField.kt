package ir.ilam.inspection.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.ilam.inspection.R
import ir.ilam.inspection.container
import ir.ilam.inspection.data.db.SnippetEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * A text field with a star over it. Tapping the star keeps what was typed;
 * tapping the arrow opens the kept phrases for this field and one tap fills
 * the box with it.
 *
 * The same descriptions, officers and positions come up visit after visit, and
 * a phone keyboard at a site is where a name quietly turns into an
 * abbreviation. Phrases are kept per [fieldKey], so a name never turns up in
 * the list of notes.
 */
@Composable
fun SnippetField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    fieldKey: String,
    modifier: Modifier = Modifier,
    multiline: Boolean = false,
    imeAction: ImeAction = ImeAction.Next
) {
    val repository = LocalContext.current.container.snippetRepository
    val scope = rememberCoroutineScope()
    val saved by remember(fieldKey) {
        repository.observe(fieldKey)
    }.collectAsStateWithLifecycle(initialValue = emptyList())

    var listOpen by remember { mutableStateOf(false) }
    val alreadySaved = saved.any { it.text == value.trim() }

    Column(modifier = modifier.fillMaxWidth()) {
        // Only the icons live above the box; the label stays where every other
        // field in the app puts it, inside the field itself.
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End
        ) {
            IconButton(
                onClick = { scope.launch { repository.save(fieldKey, value) } },
                enabled = value.isNotBlank() && !alreadySaved
            ) {
                Icon(
                    imageVector = if (alreadySaved) Icons.Filled.Star else Icons.Outlined.StarBorder,
                    contentDescription = stringResource(R.string.snippet_save)
                )
            }
            IconButton(onClick = { listOpen = !listOpen }, enabled = saved.isNotEmpty()) {
                Icon(
                    imageVector = if (listOpen) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = stringResource(R.string.snippet_open)
                )
            }
        }

        if (multiline) {
            MultilineField(label = label, value = value, onValueChange = onValueChange)
        } else {
            AppTextField(
                label = label,
                value = value,
                onValueChange = onValueChange,
                imeAction = imeAction
            )
        }

        if (listOpen) {
            SavedPhrases(
                saved = saved,
                onPick = {
                    onValueChange(it.text)
                    listOpen = false
                    scope.launch { repository.markUsed(it) }
                },
                onDelete = { scope.launch { repository.delete(it) } }
            )
        }
    }
}

/** The kept phrases for one field: tap to use, or remove what is no longer wanted. */
@Composable
private fun SavedPhrases(
    saved: List<SnippetEntity>,
    onPick: (SnippetEntity) -> Unit,
    onDelete: (SnippetEntity) -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
    ) {
        Column(
            // Bounded and scrollable: a long list must not push the field it
            // belongs to off the screen.
            modifier = Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState())
        ) {
            saved.forEachIndexed { index, snippet ->
                if (index > 0) HorizontalDivider()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = snippet.text,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onPick(snippet) }
                            .padding(12.dp)
                    )
                    ConfirmDeleteButton(itemName = snippet.text, onConfirm = { onDelete(snippet) })
                }
            }
        }
    }
}
