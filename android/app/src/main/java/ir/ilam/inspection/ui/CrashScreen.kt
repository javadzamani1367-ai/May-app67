package ir.ilam.inspection.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import ir.ilam.inspection.R
import ir.ilam.inspection.ui.common.AppCard
import ir.ilam.inspection.ui.common.PrimaryButton
import ir.ilam.inspection.ui.common.SecondaryButton
import ir.ilam.inspection.ui.common.ToneIcon
import ir.ilam.inspection.ui.theme.Tavan
import ir.ilam.inspection.ui.theme.Tone

/**
 * Shown once after a crash. The expert cannot read a stack trace, but they can
 * forward one, and that is the difference between "it closed" and a fix.
 */
@Composable
fun CrashScreen(report: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        ToneIcon(icon = Icons.Filled.BugReport, tone = Tone.DANGER, size = 56.dp)
        Text(
            text = stringResource(R.string.crash_title),
            style = MaterialTheme.typography.headlineSmall,
            color = Tavan.colors.danger.strong,
            modifier = Modifier.padding(top = 12.dp)
        )
        Text(
            text = stringResource(R.string.crash_hint),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(vertical = 12.dp)
        )
        AppCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = report,
                style = MaterialTheme.typography.bodySmall.copy(
                    // A stack trace is latin text; forcing it right to left
                    // makes it unreadable.
                    textDirection = TextDirection.Ltr
                ),
                modifier = Modifier
                    .padding(12.dp)
                    .horizontalScroll(rememberScrollState())
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PrimaryButton(
                text = stringResource(R.string.crash_share),
                onClick = { shareReport(context, report) },
                icon = Icons.Filled.Share,
                modifier = Modifier.weight(1f)
            )
            SecondaryButton(stringResource(R.string.crash_dismiss), onClick = onDismiss, modifier = Modifier.weight(1f))
        }
    }
}

private fun shareReport(context: Context, report: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.crash_title))
        putExtra(Intent.EXTRA_TEXT, report)
    }
    context.startActivity(
        Intent.createChooser(intent, context.getString(R.string.crash_share))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}
