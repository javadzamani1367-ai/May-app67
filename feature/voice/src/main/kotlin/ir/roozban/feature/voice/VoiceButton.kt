package ir.roozban.feature.voice

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.roozban.core.designsystem.theme.Roozban
import ir.roozban.core.designsystem.R as DsR

/**
 * Microphone button: tap to talk, it stops by itself after a pause (or tap again). The text goes
 * to [onText] for the user to check before saving. Without a speech model it offers
 * [onOpenModels] (or explains where to get one when null).
 */
@Composable
fun VoiceButton(
    onText: (String) -> Unit,
    onOpenModels: (() -> Unit)?,
    modifier: Modifier = Modifier,
    viewModel: VoiceViewModel = hiltViewModel(key = "voice"),
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    var askModel by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.toggle() else Toast.makeText(context, "برای ورودی صوتی اجازهٔ میکروفون لازم است.", Toast.LENGTH_LONG).show()
    }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is VoiceEvent.Text -> onText(event.text)
                is VoiceEvent.Problem -> Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                VoiceEvent.NeedsModel -> askModel = true
            }
        }
    }
    DisposableEffect(Unit) { onDispose { viewModel.cancel() } }

    val listening = state as? VoiceState.Listening
    val scale by animateFloatAsState(if (listening != null) 1f + 0.35f * listening.level else 1f, label = "level")
    val role = Roozban.colors.error
    Box(modifier.size(48.dp), contentAlignment = Alignment.Center) {
        if (listening != null) {
            Box(Modifier.size(40.dp).scale(scale).clip(CircleShape).background(role.container))
        }
        when (state) {
            VoiceState.Transcribing -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            else -> IconButton(onClick = {
                val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                if (granted || state != VoiceState.Idle) viewModel.toggle() else permission.launch(Manifest.permission.RECORD_AUDIO)
            }) {
                Icon(
                    painterResource(if (listening != null) DsR.drawable.ic_stop else DsR.drawable.ic_mic),
                    contentDescription = if (listening != null) "پایان صحبت" else "ورودی صوتی",
                    tint = if (listening != null) role.color else MaterialTheme.colorScheme.primary,
                )
            }
        }
    }

    if (askModel) {
        AlertDialog(
            onDismissRequest = { askModel = false },
            title = { Text("ورودی صوتی") },
            text = {
                Text(
                    if (onOpenModels != null) "برای تبدیل صدا به متن، یک بار مدل تشخیص گفتار را دانلود کن. همه‌چیز روی خود گوشی انجام می‌شود."
                    else "برای تبدیل صدا به متن، از «بیشتر ← دستیار هوشمند ← مدل‌ها» مدل تشخیص گفتار را دانلود کن.",
                )
            },
            confirmButton = {
                if (onOpenModels != null) {
                    TextButton(onClick = {
                        askModel = false
                        onOpenModels()
                    }) { Text("دانلود مدل") }
                } else {
                    TextButton(onClick = { askModel = false }) { Text("باشه") }
                }
            },
            dismissButton = if (onOpenModels != null) ({ TextButton(onClick = { askModel = false }) { Text("بعداً") } }) else null,
        )
    }
}
