package ir.ilam.inspection.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ir.ilam.inspection.BuildConfig
import ir.ilam.inspection.R
import ir.ilam.inspection.data.model.UserRole
import ir.ilam.inspection.ui.common.SectionCard
import ir.ilam.inspection.util.PersianNumbers

/**
 * What this app is, which build it is, and who it is powered by.
 *
 * The credit lives here and nowhere else. It is deliberately not in the
 * reports: those are official documents that leave for other organisations,
 * and a vendor line on a seizure report is not ours to add.
 */
@Composable
fun AboutCard() {
    SectionCard(title = stringResource(R.string.about_title)) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.about_description),
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = stringResource(
                    R.string.about_version,
                    PersianNumbers.toPersian(BuildConfig.VERSION_NAME)
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
            Text(
                text = stringResource(
                    R.string.about_role,
                    stringResource(
                        if (UserRole.isManager) R.string.role_manager else R.string.role_expert
                    )
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(R.string.about_powered_by),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 10.dp)
            )
        }
    }
}
