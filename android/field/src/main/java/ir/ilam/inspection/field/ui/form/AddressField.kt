package ir.ilam.inspection.field.ui.form

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EditLocationAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.ilam.inspection.field.R
import ir.ilam.inspection.ui.common.AppTextField
import ir.ilam.inspection.ui.theme.Spacing
import ir.ilam.inspection.ui.theme.Tavan
import ir.ilam.inspection.util.map.OfflineAddress
import ir.ilam.inspection.util.map.OfflineMap
import kotlinx.coroutines.launch

/**
 * The address, drafted from the offline map: county, village or city,
 * neighbourhood and nearest named street. Filled in by itself the first time
 * a position arrives and the field is still empty; never over what the user
 * typed. The button drafts it again on request. Either way it stays an
 * ordinary field — many lanes in Ilam have no name on any map.
 */
@Composable
fun AddressField(value: String, latitude: Double?, longitude: Double?, onChange: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val installed by OfflineMap.installed.collectAsStateWithLifecycle(OfflineMap.current(context))
    var note by remember { mutableStateOf<String?>(null) }
    val noneText = stringResource(R.string.offline_address_none)
    val needMapText = stringResource(R.string.offline_address_need_map)

    LaunchedEffect(latitude, longitude, installed) {
        if (value.isBlank() && latitude != null && longitude != null && installed != null) {
            OfflineAddress.describe(context, latitude, longitude)?.let(onChange)
        }
    }

    AppTextField(
        label = stringResource(R.string.report_address),
        value = value,
        onValueChange = {
            note = null
            onChange(it)
        },
        singleLine = false,
        minLines = 2,
        trailingIcon = {
            IconButton(
                onClick = {
                    if (latitude == null || longitude == null) return@IconButton
                    if (installed == null) { note = needMapText; return@IconButton }
                    scope.launch {
                        val text = OfflineAddress.describe(context, latitude, longitude)
                        if (text == null) note = noneText else { note = null; onChange(text) }
                    }
                },
                enabled = latitude != null && longitude != null
            ) {
                Icon(Icons.Filled.EditLocationAlt, contentDescription = stringResource(R.string.offline_address_fill),
                    tint = Tavan.colors.info.strong)
            }
        }
    )
    note?.let {
        Text(it, style = MaterialTheme.typography.bodySmall, color = Tavan.colors.warning.strong,
            modifier = Modifier.padding(bottom = Spacing.sm))
    }
}
