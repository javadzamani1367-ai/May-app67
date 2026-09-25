package ir.roozban.feature.tasks

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import ir.roozban.feature.voice.VoiceButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import ir.roozban.core.designsystem.R as DsR
import ir.roozban.core.domain.Highlight
import ir.roozban.core.domain.HighlightKind

/**
 * Quick add: the keyboard opens immediately, the time expression is highlighted as you type,
 * and Enter saves. Target: a task captured in under five seconds.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun QuickAddSheet(
    state: QuickAddState,
    onTextChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit,
    onOpenVoiceModels: (() -> Unit)? = null,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    val colors = MaterialTheme.colorScheme
    val highlighter = remember(state.preview?.highlights, colors) {
        HighlightTransformation(state.preview?.highlights.orEmpty()) { kind ->
            when (kind) {
                HighlightKind.TIME -> SpanStyle(background = colors.primaryContainer, color = colors.onPrimaryContainer)
                HighlightKind.RECURRENCE -> SpanStyle(background = colors.tertiaryContainer, color = colors.onTertiaryContainer)
                HighlightKind.DURATION -> SpanStyle(background = colors.secondaryContainer, color = colors.onSecondaryContainer)
                HighlightKind.PRIORITY -> SpanStyle(background = colors.errorContainer, color = colors.onErrorContainer)
                HighlightKind.PROJECT, HighlightKind.LABEL -> SpanStyle(background = colors.surfaceVariant, color = colors.primary)
            }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp)
                .imePadding()
                .animateContentSize(),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextField(
                    value = state.text,
                    onValueChange = onTextChange,
                    modifier = Modifier.weight(1f).focusRequester(focusRequester),
                    placeholder = { Text(stringResource(R.string.quick_add_placeholder)) },
                    textStyle = MaterialTheme.typography.bodyLarge,
                    visualTransformation = highlighter,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onSubmit() }),
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                    shape = MaterialTheme.shapes.medium,
                )
                VoiceButton(
                    onText = { spoken -> onTextChange(if (state.text.isBlank()) spoken else state.text.trimEnd() + " " + spoken) },
                    onOpenModels = onOpenVoiceModels,
                )
                FilledIconButton(
                    onClick = onSubmit,
                    enabled = !state.preview?.title.isNullOrBlank(),
                    modifier = Modifier.padding(start = 8.dp),
                ) {
                    Icon(painterResource(DsR.drawable.ic_check), contentDescription = stringResource(R.string.quick_add_save))
                }
            }

            val preview = state.preview
            AnimatedVisibility(visible = preview != null) {
                if (preview != null) PreviewRow(preview)
            }
        }
    }
}

@Composable
private fun PreviewRow(preview: QuickAddPreview) {
    Column(Modifier.padding(top = 12.dp)) {
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (preview.chips.isEmpty()) {
                AssistChip(onClick = {}, enabled = false, label = { Text(stringResource(R.string.quick_add_no_time)) })
            }
            preview.chips.forEach { chip ->
                val icon = when (chip.kind) {
                    ChipKind.TIME -> DsR.drawable.ic_schedule
                    ChipKind.RECURRENCE -> DsR.drawable.ic_repeat
                    ChipKind.DURATION -> DsR.drawable.ic_timer
                    ChipKind.PRIORITY -> DsR.drawable.ic_flag
                    ChipKind.PROJECT -> DsR.drawable.ic_folder
                    ChipKind.LABEL -> DsR.drawable.ic_label
                }
                AssistChip(
                    onClick = {},
                    label = { Text(chip.label) },
                    leadingIcon = {
                        Icon(painterResource(icon), contentDescription = null, modifier = Modifier.padding(2.dp))
                    },
                    colors = AssistChipDefaults.assistChipColors(),
                )
            }
        }
        if (preview.title.isNotBlank()) {
            Text(
                text = stringResource(R.string.quick_add_title_label, preview.title),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        if (preview.needsReview) {
            Text(
                text = stringResource(R.string.quick_add_needs_review),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/** Colors the recognized spans in place. Characters are unchanged, so offsets map 1:1. */
private class HighlightTransformation(
    private val highlights: List<Highlight>,
    private val styleOf: (HighlightKind) -> SpanStyle,
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        if (highlights.isEmpty()) return TransformedText(text, OffsetMapping.Identity)
        val styled = buildAnnotatedString {
            append(text)
            highlights.forEach { h ->
                if (h.end <= text.length) addStyle(styleOf(h.kind), h.start, h.end)
            }
        }
        return TransformedText(styled, OffsetMapping.Identity)
    }
}
