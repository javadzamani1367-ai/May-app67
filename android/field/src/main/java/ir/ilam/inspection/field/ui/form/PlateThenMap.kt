package ir.ilam.inspection.field.ui.form

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import ir.ilam.inspection.field.R
import ir.ilam.inspection.ui.common.AppTextField
import ir.ilam.inspection.ui.common.PrimaryButton
import ir.ilam.inspection.ui.common.SectionCard
import ir.ilam.inspection.ui.theme.Spacing
import ir.ilam.inspection.ui.theme.Tavan
import ir.ilam.inspection.ui.theme.Tone
import ir.ilam.inspection.util.PersianDate

/**
 * A panel's or a pole's plate, and straight after it, its position: the
 * plate is typed, «ثبت پلاک و موقعیت» opens the map on the user, and the
 * point is recorded standing at it. Until then the card says, in red, that
 * the position is still missing — and the page cannot be left without it:
 * leaving opens the map instead.
 */
@Composable
fun PlateThenMap(
    title: String,
    plateLabel: String,
    plate: String,
    onPlate: (String) -> Unit,
    hasPosition: Boolean,
    positionAt: Long?,
    onOpenMap: () -> Unit,
    plateError: String? = null,
    /** A pole may have lost its plate: then the map opens without one. */
    needsPlate: Boolean = true
) {
    SectionCard(title = title, icon = Icons.Filled.Badge, tone = if (hasPosition) Tone.BRAND else Tone.DANGER) {
        Column {
            // The plate typed, «done» on the keyboard brings the map straight up.
            AppTextField(plateLabel, plate, onPlate, ltr = true, error = plateError, imeAction = ImeAction.Done,
                onDone = { if (!needsPlate || plate.isNotBlank()) onOpenMap() })
            Text(
                when {
                    hasPosition && positionAt != null -> stringResource(R.string.plate_map_recorded, PersianDate.formatWithTime(positionAt))
                    hasPosition -> stringResource(R.string.plate_map_recorded_plain)
                    needsPlate -> stringResource(R.string.plate_map_missing)
                    else -> stringResource(R.string.plate_map_missing_optional)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (hasPosition) Tavan.colors.success.strong else Tavan.colors.danger.strong,
                modifier = Modifier.padding(top = Spacing.xs)
            )
            PrimaryButton(
                text = stringResource(if (hasPosition) R.string.plate_map_change else R.string.plate_map_record),
                onClick = onOpenMap,
                enabled = !needsPlate || plate.isNotBlank(),
                icon = Icons.Filled.Map,
                tone = if (hasPosition) Tone.BRAND else Tone.SUCCESS,
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm)
            )
        }
    }
}
