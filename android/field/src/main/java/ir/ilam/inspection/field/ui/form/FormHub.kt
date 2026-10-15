package ir.ilam.inspection.field.ui.form

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.ilam.inspection.field.R
import ir.ilam.inspection.ui.common.AppCard
import ir.ilam.inspection.ui.common.BottomActionBar
import ir.ilam.inspection.ui.common.HeaderAction
import ir.ilam.inspection.ui.common.PrimaryButton
import ir.ilam.inspection.ui.common.StatusBadge
import ir.ilam.inspection.ui.common.TavanTopBar
import ir.ilam.inspection.ui.common.ToneProgress
import ir.ilam.inspection.ui.theme.Spacing
import ir.ilam.inspection.ui.theme.Tavan
import ir.ilam.inspection.ui.theme.Tone
import ir.ilam.inspection.util.PersianNumbers

/** Where a section of a form stands, as its tile shows it. */
enum class SectionStatus { DONE, MISSING, OPTIONAL, ATTENTION }

/** One tile of a form's hub: what it is, a line of what it holds, and whether it is done. */
data class HubSection(
    val key: String,
    val title: String,
    val summary: String,
    val icon: ImageVector,
    val tone: Tone,
    val status: SectionStatus
)

/**
 * A form as one page of tiles. Each part of the form is a tile that says
 * whether it is done and what it holds; a tap opens that part on a page of
 * its own. "Send" is pinned at the bottom, above the phone's own buttons, and
 * says in words what still stops it.
 */
@Composable
fun FormHub(
    title: String,
    sections: List<HubSection>,
    missing: List<String>,
    busy: Boolean,
    onOpen: (String) -> Unit,
    onFinish: () -> Unit,
    onDiscard: () -> Unit,
    onClose: () -> Unit,
    /** The form's item: whether leaving needs a question, and how to keep it. */
    editor: ItemEditor,
    scroll: ScrollState = rememberScrollState(),
    header: (@Composable ColumnScope.() -> Unit)? = null,
    footer: (@Composable ColumnScope.() -> Unit)? = null
) {
    var confirming by remember { mutableStateOf(false) }
    var leaving by remember { mutableStateOf(false) }
    // Back, by the arrow or the phone's own button: nothing entered → the empty
    // draft goes quietly; something entered → ask; already sent → just leave.
    val requestExit = {
        when {
            !editor.isDraft -> onClose()
            !editor.touched -> onDiscard()
            else -> leaving = true
        }
    }
    BackHandler(onBack = requestExit)
    Column(modifier = Modifier.fillMaxSize()) {
        TavanTopBar(
            title = title,
            subtitle = stringResource(R.string.form_autosaved),
            onBack = requestExit,
            actions = {
                HeaderAction(Icons.Filled.DeleteOutline, stringResource(R.string.form_discard), onClick = { confirming = true })
            }
        )
        Column(
            modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(scroll).padding(horizontal = Spacing.screen)
        ) {
            ProgressCard(sections)
            header?.invoke(this)
            SectionGrid(sections, onOpen)
            footer?.invoke(this)
            Spacer(Modifier.height(Spacing.lg))
        }
        BottomActionBar(note = { FinishNote(missing) }) {
            PrimaryButton(
                text = stringResource(R.string.form_finish),
                onClick = onFinish,
                enabled = missing.isEmpty() && !busy,
                busy = busy,
                icon = Icons.AutoMirrored.Filled.Send,
                tone = Tone.SUCCESS,
                modifier = Modifier.weight(1f)
            )
        }
    }
    if (confirming) DiscardDialog(onDismiss = { confirming = false }, onConfirm = { confirming = false; onDiscard() })
    if (leaving) {
        ExitDialog(
            onKeep = { leaving = false; editor.keepAndClose(onClose) },
            onDelete = { leaving = false; onDiscard() },
            onStay = { leaving = false }
        )
    }
}

/** How far the form is, in one bar: the first thing seen when it opens. */
@Composable
private fun ProgressCard(sections: List<HubSection>) {
    val done = sections.count { it.status == SectionStatus.DONE }
    val required = sections.count { it.status == SectionStatus.MISSING }
    val tone = if (required == 0) Tone.SUCCESS else Tone.ACCENT
    AppCard(modifier = Modifier.fillMaxWidth().padding(top = Spacing.md, bottom = 6.dp)) {
        Column(modifier = Modifier.padding(Spacing.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.hub_progress, PersianNumbers.toPersian(done), PersianNumbers.toPersian(sections.size)),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                if (required > 0) {
                    StatusBadge(stringResource(R.string.hub_required_left, PersianNumbers.toPersian(required)), Tone.DANGER)
                } else {
                    StatusBadge(stringResource(R.string.hub_ready_badge), Tone.SUCCESS, icon = Icons.Filled.Check)
                }
            }
            ToneProgress(
                fraction = if (sections.isEmpty()) 0f else done.toFloat() / sections.size,
                tone = tone,
                modifier = Modifier.padding(top = Spacing.sm)
            )
            Text(
                stringResource(R.string.hub_tap_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.sm)
            )
        }
    }
}

@Composable
private fun FinishNote(missing: List<String>) {
    Text(
        text = if (missing.isEmpty()) stringResource(R.string.hub_ready)
        else stringResource(R.string.hub_missing_note, missing.joinToString("، ")),
        style = MaterialTheme.typography.bodySmall,
        color = if (missing.isEmpty()) Tavan.colors.success.strong else Tavan.colors.danger.strong,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
    )
}

/**
 * One part of a form on a page of its own. The bottom bar always sits above
 * the phone's buttons and the keyboard; by default it simply returns to the
 * list, since everything is already saved as it is typed.
 */
@Composable
fun FormPage(
    title: String,
    onBack: () -> Unit,
    subtitle: String? = null,
    scroll: ScrollState = rememberScrollState(),
    above: (@Composable () -> Unit)? = null,
    bottom: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        TavanTopBar(title = title, subtitle = subtitle ?: stringResource(R.string.form_autosaved), onBack = onBack)
        above?.invoke()
        Column(
            modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(scroll)
                .padding(horizontal = Spacing.screen, vertical = Spacing.sm),
            content = content
        )
        BottomActionBar {
            if (bottom != null) {
                bottom()
            } else {
                PrimaryButton(
                    text = stringResource(R.string.hub_back_to_list),
                    onClick = onBack,
                    icon = Icons.Filled.Check,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
