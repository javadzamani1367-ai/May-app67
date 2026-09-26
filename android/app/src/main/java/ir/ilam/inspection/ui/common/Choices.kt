package ir.ilam.inspection.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ir.ilam.inspection.R
import ir.ilam.inspection.ui.theme.Spacing
import ir.ilam.inspection.ui.theme.Tavan
import ir.ilam.inspection.ui.theme.Tone

/**
 * A choice made by tapping, not by opening a menu. Step three is filled in
 * standing at a meter, and a chip the size of a thumb beats a dropdown that
 * has to be opened, scrolled and dismissed.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> ChoiceRow(
    label: String,
    options: List<T>,
    selected: T?,
    optionLabel: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    /** A choice that means trouble — "no meter, taken from the network" — can say so. */
    toneOf: (T) -> Tone = { Tone.ACCENT }
) {
    Column(modifier = modifier.fillMaxWidth().padding(vertical = Spacing.sm)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            options.forEach { option ->
                ToneChip(
                    text = optionLabel(option),
                    selected = option == selected,
                    tone = toneOf(option),
                    onClick = { onSelect(option) }
                )
            }
        }
    }
}

/** One tappable chip that fills with its tone when selected, with a tick so colour is not the only cue. */
@Composable
fun ToneChip(
    text: String,
    selected: Boolean,
    tone: Tone,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selectedIcon: ImageVector = Icons.Filled.Check
) {
    val colors = Tavan.colors.of(tone)
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(text, style = MaterialTheme.typography.labelLarge) },
        leadingIcon = if (selected) {
            { Icon(selectedIcon, contentDescription = null, modifier = Modifier.size(18.dp)) }
        } else null,
        shape = MaterialTheme.shapes.small,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surface,
            labelColor = MaterialTheme.colorScheme.onSurface,
            selectedContainerColor = colors.container,
            selectedLabelColor = colors.onContainer,
            selectedLeadingIconColor = colors.strong
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = MaterialTheme.colorScheme.outlineVariant,
            selectedBorderColor = colors.strong,
            borderWidth = 1.dp,
            selectedBorderWidth = 1.5.dp
        ),
        modifier = modifier.heightIn(min = 44.dp)
    )
}

/**
 * A question the report has to answer with بله, خیر, or honestly nothing.
 * [yesTone] is the colour of a "yes": for "is the meter tampered with?" a yes
 * is a finding, for "is the seal intact?" it is the normal answer.
 */
@Composable
fun YesNoRow(
    question: String,
    answer: Boolean?,
    onAnswer: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    yesTone: Tone = Tone.SUCCESS,
    noTone: Tone = Tone.WARNING
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        Text(
            text = question,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        ToneChip(
            text = stringResource(R.string.answer_yes),
            selected = answer == true,
            tone = yesTone,
            onClick = { onAnswer(true) }
        )
        ToneChip(
            text = stringResource(R.string.answer_no),
            selected = answer == false,
            tone = noTone,
            onClick = { onAnswer(false) },
            selectedIcon = Icons.Filled.Close
        )
    }
}
