package ir.minerfinder.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import ir.minerfinder.ui.theme.Palette

@Composable
fun Header(title: String, subtitle: String? = null) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Palette.Navy)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = Color.White)
        if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Color(0xFFB8C7D9))
    }
}
