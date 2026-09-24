package ir.roozban.app.entry

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import ir.roozban.app.R
import ir.roozban.core.designsystem.theme.RoozbanTheme
import ir.roozban.core.domain.SettingsRepository
import ir.roozban.feature.tasks.StandaloneQuickAdd
import javax.inject.Inject

/**
 * A translucent window with just the quick-add sheet. Opened by Share («اشتراک‌گذاری» from any
 * app), the home-screen widget, the Quick Settings tile and the launcher shortcut.
 */
@AndroidEntryPoint
class QuickAddActivity : AppCompatActivity() {

    @Inject lateinit var settings: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val shared = if (intent?.action == Intent.ACTION_SEND) {
            listOfNotNull(intent.getStringExtra(Intent.EXTRA_SUBJECT), intent.getStringExtra(Intent.EXTRA_TEXT))
                .joinToString(" ")
                .replace(Regex("\\s+"), " ")
                .trim()
        } else {
            ""
        }
        setContent {
            val userSettings by settings.settings.collectAsStateWithLifecycle(initialValue = null)
            RoozbanTheme(dynamicColor = userSettings?.dynamicColor == true) {
                StandaloneQuickAdd(
                    initialText = shared,
                    onDone = { saved ->
                        if (saved) Toast.makeText(this, R.string.quick_add_saved, Toast.LENGTH_SHORT).show()
                        finish()
                    },
                )
            }
        }
    }

    companion object {
        fun intent(context: android.content.Context): Intent =
            Intent(context, QuickAddActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    }
}
