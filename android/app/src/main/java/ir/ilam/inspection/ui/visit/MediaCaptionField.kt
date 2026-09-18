package ir.ilam.inspection.ui.visit

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import ir.ilam.inspection.R
import ir.ilam.inspection.ui.common.AppTextField
import ir.ilam.inspection.ui.common.AutoSave
import ir.ilam.inspection.ui.common.DropdownField

/**
 * The caption of one file. The standard captions come first, because a report
 * is easier to read when the same shot carries the same name everywhere; the
 * rest are described by the expert.
 */
@Composable
fun MediaCaptionField(
    caption: String?,
    standardCaptions: List<String>,
    onCaptionChange: (String) -> Unit
) {
    val custom = stringResource(R.string.media_caption_custom)
    val options = remember(standardCaptions, caption) {
        val current = caption?.takeIf { it.isNotBlank() && it in standardCaptions }
        (listOfNotNull(current) + standardCaptions).distinct() + custom
    }

    // A caption that is not one of the standard ones is the expert's own text.
    var freeText by remember(caption) {
        mutableStateOf(if (caption != null && caption !in standardCaptions) caption else "")
    }
    var writingOwn by remember(caption) {
        mutableStateOf(caption != null && caption.isNotBlank() && caption !in standardCaptions)
    }

    Column {
        DropdownField(
            label = stringResource(R.string.media_caption_pick),
            options = options,
            selected = when {
                writingOwn -> custom
                caption.isNullOrBlank() -> null
                else -> caption
            },
            optionLabel = { it },
            onSelect = { chosen ->
                if (chosen == custom) {
                    writingOwn = true
                } else {
                    writingOwn = false
                    onCaptionChange(chosen)
                }
            }
        )
        if (writingOwn) {
            AppTextField(
                label = stringResource(R.string.media_caption),
                value = freeText,
                onValueChange = { freeText = it }
            )
            AutoSave(freeText) { if (it.isNotBlank()) onCaptionChange(it) }
        }
    }
}
