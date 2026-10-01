package ir.ilam.inspection.ui.lock

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ir.ilam.inspection.core.R
import ir.ilam.inspection.ui.common.AppCard
import ir.ilam.inspection.ui.common.AppTextField
import ir.ilam.inspection.ui.common.BrandMark
import ir.ilam.inspection.ui.common.PrimaryButton
import ir.ilam.inspection.ui.common.SecondaryButton
import ir.ilam.inspection.ui.common.StatusBadge
import ir.ilam.inspection.ui.theme.Tavan
import ir.ilam.inspection.ui.theme.Tone

/** A line under the form: an error in red, a notice in green. */
data class LockMessage(val text: String, val error: Boolean)

/**
 * The entry gate every TavanKav app shares: the mark and name, which app this
 * is, the user code and password, the fingerprint when it may be offered, and
 * the server address — always reachable, because settings sit behind this
 * screen and a wrong address would otherwise have no way of being corrected.
 *
 * What differs between apps goes in [extra], under the form: the inspection
 * apps show the installation's code and the registration request there.
 */
@Composable
fun LockFrame(
    roleLabel: String,
    initialUserCode: String,
    busy: Boolean,
    message: LockMessage?,
    canUseFingerprint: Boolean,
    onFingerprint: () -> Unit,
    onSignIn: (userCode: String, password: String) -> Unit,
    onEdited: () -> Unit,
    onServerAddress: () -> Unit,
    extra: @Composable ColumnScope.() -> Unit = {}
) {
    var userCode by remember { mutableStateOf(initialUserCode) }
    var password by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Tavan.colors.header, Tavan.colors.headerDeep)))
            .windowInsetsPadding(WindowInsets.systemBars)
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        BrandHeader(roleLabel)

        AppCard(modifier = Modifier.fillMaxWidth().padding(top = 24.dp)) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(stringResource(R.string.lock_welcome), style = MaterialTheme.typography.titleLarge)
                if (busy) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
                }
                message?.let {
                    Text(
                        text = it.text,
                        color = if (it.error) Tavan.colors.danger.strong else Tavan.colors.success.strong,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                    )
                }
                AppTextField(
                    label = stringResource(R.string.lock_user_code),
                    value = userCode,
                    onValueChange = { userCode = it; onEdited() },
                    imeAction = ImeAction.Next,
                    ltr = true,
                    leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null) }
                )
                AppTextField(
                    label = stringResource(R.string.lock_enter_password),
                    value = password,
                    onValueChange = { password = it; onEdited() },
                    imeAction = ImeAction.Done,
                    password = true,
                    leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) }
                )
                PrimaryButton(
                    text = stringResource(R.string.lock_sign_in),
                    onClick = { onSignIn(userCode, password) },
                    busy = busy,
                    icon = Icons.AutoMirrored.Filled.Login,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                )
                if (canUseFingerprint) {
                    SecondaryButton(
                        text = stringResource(R.string.lock_biometric),
                        onClick = onFingerprint,
                        icon = Icons.Filled.Fingerprint,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    )
                }
            }
        }

        extra()

        TextButton(onClick = onServerAddress, enabled = !busy) {
            Icon(Icons.Filled.Dns, contentDescription = null, tint = Tavan.colors.onHeaderMuted)
            Text(
                stringResource(R.string.lock_server_address),
                color = Tavan.colors.onHeaderMuted,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}

/** The mark, the name in both scripts, and which of the apps this is. */
@Composable
private fun BrandHeader(roleLabel: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = 16.dp)) {
        BrandMark(size = 92.dp)
        Text(
            stringResource(R.string.brand_name),
            style = MaterialTheme.typography.displaySmall,
            color = Tavan.colors.onHeader,
            modifier = Modifier.padding(top = 14.dp)
        )
        Text(stringResource(R.string.brand_latin), style = MaterialTheme.typography.labelLarge, color = Tavan.colors.onHeaderMuted)
        Text(
            stringResource(R.string.brand_tagline),
            style = MaterialTheme.typography.bodySmall,
            color = Tavan.colors.onHeaderMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp)
        )
        StatusBadge(text = roleLabel, tone = Tone.ACCENT, solid = true, modifier = Modifier.padding(top = 10.dp))
    }
}
