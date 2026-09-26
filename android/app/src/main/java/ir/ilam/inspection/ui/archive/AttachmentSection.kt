package ir.ilam.inspection.ui.archive

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Balance
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ElectricalServices
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.RequestQuote
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ir.ilam.inspection.R
import ir.ilam.inspection.container
import ir.ilam.inspection.data.db.AttachmentEntity
import ir.ilam.inspection.data.model.AttachmentCategory
import ir.ilam.inspection.data.model.ReportDetail
import ir.ilam.inspection.data.repo.SnippetFields
import ir.ilam.inspection.export.ShareUtil
import ir.ilam.inspection.ui.common.AddPanel
import ir.ilam.inspection.ui.common.AppTextField
import ir.ilam.inspection.ui.common.ConfirmDeleteButton
import ir.ilam.inspection.ui.common.DropdownField
import ir.ilam.inspection.ui.common.PrimaryButton
import ir.ilam.inspection.ui.common.SectionCard
import ir.ilam.inspection.ui.common.SnippetField
import ir.ilam.inspection.ui.common.StatusBadge
import ir.ilam.inspection.ui.common.ToneIcon
import ir.ilam.inspection.ui.common.attachmentCategoryLabel
import ir.ilam.inspection.ui.theme.Spacing
import ir.ilam.inspection.ui.theme.Tone
import ir.ilam.inspection.util.PersianDate
import ir.ilam.inspection.util.PersianNumbers

/**
 * Documents that arrive after the visit — miner logs, commission minutes,
 * bills, letters. They can still be added once the case is archived. Each
 * opens in whatever the phone reads that kind of file with.
 */
@Composable
fun AttachmentSection(detail: ReportDetail, viewModel: CaseDetailViewModel) {
    val context = LocalContext.current
    val files = context.container.fileStore
    var category by remember { mutableStateOf(AttachmentCategory.MINER_LOGS) }
    var title by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            viewModel.addAttachment(context, it, category, title, note)
            title = ""
            note = ""
        }
    }

    SectionCard(
        title = stringResource(R.string.attachments_title),
        subtitle = stringResource(R.string.attachments_hint),
        icon = Icons.Filled.FolderOpen,
        tone = Tone.BRAND,
        trailing = {
            if (detail.attachments.isNotEmpty()) {
                StatusBadge(PersianNumbers.toPersian(detail.attachments.size), tone = Tone.BRAND)
            }
        }
    ) {
        Column {
            if (detail.attachments.isEmpty()) {
                Text(
                    text = stringResource(R.string.attachments_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            detail.attachments.forEach { attachment ->
                AttachmentRow(
                    attachment = attachment,
                    onOpen = { ShareUtil.view(context, files.resolve(attachment.filePath)) },
                    onDelete = { viewModel.removeAttachment(attachment) }
                )
            }
            AddPanel(title = stringResource(R.string.attachment_new)) {
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
                PrimaryButton(
                    text = stringResource(R.string.attachment_pick),
                    onClick = { picker.launch(arrayOf("*/*")) },
                    icon = Icons.Filled.AttachFile,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun AttachmentRow(attachment: AttachmentEntity, onOpen: () -> Unit, onDelete: () -> Unit) {
    val category = AttachmentCategory.of(attachment.category)
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ToneIcon(icon = category.icon(), tone = Tone.BRAND, size = 38.dp)
        Column(modifier = Modifier.weight(1f).padding(horizontal = Spacing.md)) {
            Text(
                attachment.title?.takeIf { it.isNotBlank() } ?: attachmentCategoryLabel(category),
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                attachmentCategoryLabel(category) + " · " + PersianDate.format(attachment.addedAt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        ConfirmDeleteButton(itemName = attachment.title, onConfirm = onDelete)
    }
}

private fun AttachmentCategory.icon(): ImageVector = when (this) {
    AttachmentCategory.MINER_LOGS -> Icons.Filled.Terminal
    AttachmentCategory.MINER_ANALYSIS -> Icons.Filled.Analytics
    AttachmentCategory.COMMISSION_MINUTES -> Icons.Filled.Gavel
    AttachmentCategory.ENERGY_BILL -> Icons.Filled.ReceiptLong
    AttachmentCategory.DAMAGE_BILL -> Icons.Filled.RequestQuote
    AttachmentCategory.LETTER_SALES -> Icons.Filled.Mail
    AttachmentCategory.LETTER_LEGAL -> Icons.Filled.Balance
    AttachmentCategory.LETTER_COUNTY_POWER -> Icons.Filled.ElectricalServices
    AttachmentCategory.OTHER -> Icons.Filled.Description
}
