package ir.ilam.inspection.ui.visit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ir.ilam.inspection.R

/**
 * The camera's controls, laid out the way every phone camera lays them out,
 * so nobody has to learn them: a large round shutter in the middle, video
 * beside it, close on the other side. The shutter is big enough for a glove.
 */
@Composable
fun CaptureControls(
    canTakePhoto: Boolean,
    canRecord: Boolean,
    recording: Boolean,
    onShutter: () -> Unit,
    onRecord: () -> Unit,
    onClose: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 28.dp, vertical = 22.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        LabelledControl(stringResource(R.string.action_back)) {
            IconButton(onClick = onClose, enabled = !recording, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Filled.Close, contentDescription = null, tint = Color.White, modifier = Modifier.size(30.dp))
            }
        }
        LabelledControl(stringResource(R.string.media_take_photo)) {
            Surface(
                onClick = onShutter,
                enabled = canTakePhoto,
                shape = CircleShape,
                color = if (canTakePhoto) Color.White else Color.White.copy(alpha = 0.3f),
                modifier = Modifier.size(80.dp).border(5.dp, Color.White.copy(alpha = 0.35f), CircleShape).padding(7.dp)
            ) {}
        }
        LabelledControl(stringResource(R.string.media_record_video)) {
            Surface(
                onClick = onRecord,
                enabled = canRecord,
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.14f),
                modifier = Modifier.size(58.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (recording) {
                        // A square while recording: the universal "stop".
                        Box(modifier = Modifier.size(22.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFFEF4444)))
                    } else {
                        Icon(
                            Icons.Filled.Videocam,
                            contentDescription = null,
                            tint = if (canRecord) Color(0xFFEF4444) else Color.White.copy(alpha = 0.3f),
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LabelledControl(label: String, control: @Composable () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        control()
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.8f),
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}
