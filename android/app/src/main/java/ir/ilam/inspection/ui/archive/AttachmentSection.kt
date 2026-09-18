package ir.ilam.inspection.ui.archive

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ir.ilam.inspection.R
import ir.ilam.inspection.data.model.AttachmentCategory
import ir.ilam.inspection.data.model.ReportDetail
import ir.ilam.inspection.ui.common.AppTextField
import ir.ilam.inspection.ui.common.ConfirmDeleteButton
import ir.ilam.inspection.ui.common.DropdownField
import ir.ilam.inspection.ui.common.SectionCard
import ir.ilam.inspection.ui.common.attachmentCategoryLabel
import ir.ilam.inspection.util.PersianDate
import ir.ilam.inspection.data.repo.SnippetFields
import ir.ilam.inspection.ui.common.SnippetField

/**
 * Documents that arrive after the visit — miner logs, commission minutes,
 * bills, letters. They can still be added once the case is archived.
 */
@Composable
fun AttachmentSection(detail: ReportDetail, viewModel: CaseDetailViewModel) {
    val context = LocalContext.current
    var category by remember { mutableStateOf(AttachmentCategory.MINER_LOGS) }
    var title by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            viewModel.addAttachment(context, it, category, title, note)
            title = ""
            note = ""
        }
    }

    SectionCard(title = stringResource(R.string.attachments_title)) {
        Column {
            if (detail.attachments.isEmpty()) {
                Text(
                    text = stringResource(R.string.attachments_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            detail.attachments.forEach { attachment ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = attachmentCategoryLabel(
                                AttachmentCategory.of(attachment.category)
                            ) + " - " + attachment.title.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = PersianDate.format(attachment.addedAt),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    ConfirmDeleteButton(
                        itemName = attachment.title,
                        onConfirm = { viewModel.removeAttachment(attachment) }
                    )
                }
            }
            DropdownField(
                label = stringResource(R.string.attachment_category),
                options = AttachmentCategory.entries.toList(),
                selected = category,
                optionLabel = { attachmentCategoryLabel(it) },
                onSelect = { category = it }
            )
            SnippetField(
                label = stringResource(R.string.attachment_title_field),
                value = title,
                onValueChange = { title = it },
                fieldKey = SnippetFields.ATTACHMENT_TITLE
            )
            AppTextField(stringResource(R.string.attachment_note), note, { note = it })
            Button(
                onClick = { picker.launch(arrayOf("*/*")) },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) {
                Text(stringResource(R.string.attachment_add))
            }
        }
    }
}
