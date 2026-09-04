package ir.ilam.inspection.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ir.ilam.inspection.R
import ir.ilam.inspection.util.PersianDate
import ir.ilam.inspection.util.PersianNumbers

/**
 * Dates are picked on the Jalali calendar; the value handed back is unix
 * milliseconds, which is the only form the database ever sees.
 */
@Composable
fun JalaliDatePickerDialog(
    initialMillis: Long,
    onDismiss: () -> Unit,
    onPicked: (Long) -> Unit
) {
    val initial = remember(initialMillis) { PersianDate.of(initialMillis) }
    var year by remember { mutableIntStateOf(initial.year) }
    var month by remember { mutableIntStateOf(initial.month) }
    var day by remember { mutableIntStateOf(initial.day) }

    val currentYear = remember { PersianDate.today().year }
    val years = remember(currentYear) { ((currentYear - 5)..(currentYear + 1)).toList() }
    val maxDay = PersianDate.monthLength(year, month)
    if (day > maxDay) day = maxDay

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.date_pick)) },
        text = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DropdownField(
                    label = stringResource(R.string.date_year),
                    options = years,
                    selected = year,
                    optionLabel = { PersianNumbers.toPersian(it) },
                    onSelect = { year = it },
                    modifier = Modifier.weight(1f)
                )
                DropdownField(
                    label = stringResource(R.string.date_month),
                    options = (1..12).toList(),
                    selected = month,
                    optionLabel = { PersianNumbers.toPersian(it) },
                    onSelect = { month = it },
                    modifier = Modifier.weight(1f)
                )
                DropdownField(
                    label = stringResource(R.string.date_day),
                    options = (1..maxDay).toList(),
                    selected = day,
                    optionLabel = { PersianNumbers.toPersian(it) },
                    onSelect = { day = it },
                    modifier = Modifier.weight(1f)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onPicked(PersianDate.toEpochMillis(year, month, day)) }) {
                Text(stringResource(R.string.action_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}
