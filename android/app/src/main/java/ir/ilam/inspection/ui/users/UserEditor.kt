package ir.ilam.inspection.ui.users

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import ir.ilam.inspection.R
import ir.ilam.inspection.data.CountyCatalog
import ir.ilam.inspection.data.db.UserEntity
import ir.ilam.inspection.ui.common.AppTextField
import ir.ilam.inspection.ui.common.DropdownField
import ir.ilam.inspection.ui.common.NumberField
import ir.ilam.inspection.ui.common.SectionCard

/**
 * One expert's registration. The device code is typed in exactly as the
 * expert's phone displayed it, because that is what ties the account to one
 * installation — the code dies with the app, so a reinstall brings the expert
 * back here rather than carrying the old account along.
 */
@Composable
fun UserEditor(
    user: UserEntity,
    password: String,
    onChange: (UserEntity) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    // Read once: the catalog parses two resource arrays, and this composable
    // recomposes on every keystroke in the form.
    val counties = remember { CountyCatalog(context).defaults.map { it.name }.distinct() }

    SectionCard(title = stringResource(R.string.users_editor)) {
        Column {
            AppTextField(
                label = stringResource(R.string.users_full_name),
                value = user.fullName,
                onValueChange = { onChange(user.copy(fullName = it)) }
            )
            AppTextField(
                label = stringResource(R.string.users_user_code),
                value = user.userCode,
                onValueChange = { onChange(user.copy(userCode = it)) }
            )
            DropdownField(
                label = stringResource(R.string.users_county),
                options = counties,
                selected = user.county,
                optionLabel = { it },
                onSelect = { onChange(user.copy(county = it)) }
            )
            NumberField(
                label = stringResource(R.string.users_phone),
                value = user.phone.orEmpty(),
                onValueChange = { onChange(user.copy(phone = it)) }
            )
            AppTextField(
                label = stringResource(R.string.users_device_code),
                value = user.deviceCode.orEmpty(),
                onValueChange = { onChange(user.copy(deviceCode = it.uppercase())) }
            )
            AppTextField(
                label = stringResource(R.string.users_password),
                value = password,
                onValueChange = onPasswordChange
            )
            Text(
                text = stringResource(R.string.users_password_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            AppTextField(
                label = stringResource(R.string.users_note),
                value = user.note.orEmpty(),
                onValueChange = { onChange(user.copy(note = it)) },
                imeAction = ImeAction.Done
            )
            Text(
                text = stringResource(R.string.users_editor_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.action_cancel))
                }
                Button(onClick = onSave, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.action_save))
                }
            }
        }
    }
}
