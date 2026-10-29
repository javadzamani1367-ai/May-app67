package ir.minerfinder.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.content.IntentCompat
import androidx.lifecycle.lifecycleScope
import ir.minerfinder.R
import ir.minerfinder.graph
import ir.minerfinder.ui.common.Fa
import ir.minerfinder.ui.theme.MinerFinderTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MinerFinderTheme { AppRoot() }
        }
        if (savedInstanceState == null) handleShared(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShared(intent)
    }

    /** A signature file sent to the app from the share sheet (or opened from a file manager). */
    private fun handleShared(intent: Intent?) {
        val uri: Uri = when (intent?.action) {
            Intent.ACTION_SEND -> IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            Intent.ACTION_VIEW -> intent.data
            else -> null
        } ?: return
        lifecycleScope.launch {
            val message = runCatching { graph.signatures.import(uri) }.fold(
                { getString(R.string.signatures_imported, Fa.int(it)) },
                { getString(R.string.signatures_import_failed) },
            )
            Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
        }
    }
}
