package ir.ilam.inspection.field.ui.line

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MarkunreadMailbox
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import ir.ilam.inspection.field.R
import ir.ilam.inspection.field.data.PoleReading
import ir.ilam.inspection.field.data.Subscriber
import ir.ilam.inspection.field.field
import ir.ilam.inspection.ui.common.AppCard
import ir.ilam.inspection.ui.common.NumberField
import ir.ilam.inspection.ui.common.SecondaryButton
import ir.ilam.inspection.ui.common.SectionCard
import ir.ilam.inspection.ui.common.StatusBadge
import ir.ilam.inspection.ui.theme.Spacing
import ir.ilam.inspection.ui.theme.Tavan
import ir.ilam.inspection.ui.theme.Tone
import ir.ilam.inspection.util.PersianNumbers

/**
 * The subscribers of a pole whose current was abnormal: one row each, and
 * «افزودن» while there are branches left without one — a pole with three
 * branches cannot have four abnormal subscribers.
 */
@Composable
fun PoleSubscribers(viewModel: LineViewModel, pole: PoleReading, onOpen: (String) -> Unit, onAdded: (String) -> Unit) {
    val prefs = LocalContext.current.field.prefs
    SectionCard(
        title = stringResource(R.string.sub_title),
        subtitle = stringResource(R.string.sub_hint, fa(prefs.abnormalSingleA), fa(prefs.abnormalThreeA)),
        icon = Icons.Filled.Home,
        tone = Tone.WARNING
    ) {
        Column {
            pole.subscribers.forEachIndexed { index, subscriber ->
                SubscriberRow(index + 1, subscriber, prefs.abnormalSingleA, prefs.abnormalThreeA) { onOpen(subscriber.id) }
            }
            val note = when {
                pole.branches == 0 -> stringResource(R.string.sub_need_branches)
                pole.subscribers.size > pole.branches -> stringResource(R.string.sub_over_limit, fa(pole.branches))
                pole.subscribers.size == pole.branches -> stringResource(R.string.sub_at_limit, fa(pole.branches))
                else -> null
            }
            note?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (pole.subscribers.size > pole.branches) Tavan.colors.danger.strong
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.sm)
                )
            }
            SecondaryButton(
                text = stringResource(R.string.sub_add),
                onClick = { viewModel.addSubscriber(pole.id)?.let(onAdded) },
                enabled = pole.subscribers.size < pole.branches,
                icon = Icons.Filled.PersonAdd,
                tone = Tone.WARNING,
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm)
            )
        }
    }
}

@Composable
private fun SubscriberRow(number: Int, subscriber: Subscriber, singleA: Int, threeA: Int, onClick: () -> Unit) {
    val complete = subscriber.hasPosition && subscriber.ampsComplete
    AppCard(onClick = onClick, modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(Spacing.md)) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.sub_row_title, fa(number), subscriber.name.ifBlank { "—" }),
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    stringResource(
                        if (subscriber.threePhase) R.string.sub_three_amps else R.string.sub_single_amps,
                        subscriber.values().joinToString(" / ") { fa(plainNumber(it)) }.ifBlank { "—" }
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            when {
                !complete -> StatusBadge(stringResource(R.string.sub_incomplete), Tone.DANGER)
                subscriber.abnormal(singleA, threeA) -> StatusBadge(stringResource(R.string.sub_abnormal), Tone.WARNING)
                else -> StatusBadge(stringResource(R.string.sub_below), Tone.NEUTRAL)
            }
        }
    }
}

/** Up to twenty ten-digit postal codes: a building of flats has one per flat. */
@Composable
fun PostalCodes(codes: List<String>, onChange: (List<String>) -> Unit) {
    val shown = codes.ifEmpty { listOf("") }
    SectionCard(
        title = stringResource(R.string.sub_postal_title),
        subtitle = stringResource(R.string.sub_postal_hint, fa(Subscriber.MAX_POSTAL_CODES)),
        icon = Icons.Filled.MarkunreadMailbox,
        tone = Tone.BRAND
    ) {
        Column {
            shown.forEachIndexed { index, code ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    NumberField(
                        label = stringResource(R.string.sub_postal_n, fa(index + 1)),
                        value = code,
                        onValueChange = { v -> onChange(shown.toMutableList().also { it[index] = v.take(POSTAL_LENGTH) }) },
                        error = if (Subscriber.validPostalCode(code)) null else stringResource(R.string.sub_postal_invalid),
                        modifier = Modifier.weight(1f)
                    )
                    if (shown.size > 1 || code.isNotEmpty()) {
                        IconButton(onClick = { onChange(shown.filterIndexed { i, _ -> i != index }) }) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.form_remove))
                        }
                    }
                }
            }
            SecondaryButton(
                text = stringResource(R.string.sub_postal_add),
                onClick = { onChange(shown + "") },
                enabled = shown.size < Subscriber.MAX_POSTAL_CODES && shown.last().isNotBlank(),
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm)
            )
        }
    }
}

/** "12", not "12.0". */
internal fun plainNumber(value: Double): String = if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()

private fun fa(value: Int) = PersianNumbers.toPersian(value)
private fun fa(value: String) = PersianNumbers.toPersian(value)
private const val POSTAL_LENGTH = 10
