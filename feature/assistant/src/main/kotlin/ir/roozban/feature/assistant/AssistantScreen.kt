package ir.roozban.feature.assistant

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.roozban.ai.tools.ActionOutput
import ir.roozban.ai.tools.ActionResult
import ir.roozban.ai.tools.Describe
import ir.roozban.ai.tools.Plan
import ir.roozban.ai.tools.PlannedAction
import ir.roozban.core.calendar.PersianDateFormatter
import ir.roozban.core.designsystem.components.AppCard
import ir.roozban.core.designsystem.components.EmptyState
import ir.roozban.core.designsystem.components.IconBadge
import ir.roozban.core.designsystem.components.RoozbanTopBar
import ir.roozban.core.designsystem.components.StatusPill
import ir.roozban.core.designsystem.theme.Roozban
import ir.roozban.core.domain.Access
import java.time.LocalDate
import ir.roozban.core.designsystem.R as DsR

private val SUGGESTIONS = listOf(
    "فردا ساعت ۹ صبح جلسه با مدیر",
    "کارهای امروزم چیه؟",
    "فردا برای ۴۵ دقیقه ورزش وقت خالی پیدا کن",
    "کارهای عقب‌افتاده رو بذار برای فردا",
    "عادت روزانه: کتاب خواندن ساعت ۱۰ شب",
)

@Composable
internal fun AssistantScreen(onBack: () -> Unit, onOpenModels: () -> Unit, viewModel: AssistantViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var input by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    LaunchedEffect(state.items.size, state.items.lastOrNull()?.text?.length) {
        if (state.items.isNotEmpty()) listState.animateScrollToItem(state.items.size - 1)
    }
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            RoozbanTopBar(
                title = {
                    Column {
                        Text("دستیار روزبان")
                        Text(
                            statusText(state),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(painterResource(DsR.drawable.ic_arrow_back), "بازگشت") } },
                actions = {
                    if (state.items.isNotEmpty()) IconButton(onClick = viewModel::clear) { Icon(painterResource(DsR.drawable.ic_delete), "پاک کردن گفتگو") }
                    IconButton(onClick = onOpenModels) { Icon(painterResource(DsR.drawable.ic_memory), "مدل‌ها") }
                },
            )
        },
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
            when {
                state.access != Access.FULL -> EmptyState(DsR.drawable.ic_assistant, "دستیار در نسخهٔ حرفه‌ای", "برای استفاده از دستیار، نسخهٔ حرفه‌ای را فعال کن.", Roozban.colors.focus, Modifier.weight(1f))
                state.status == EngineStatus.UNSUPPORTED -> EmptyState(
                    DsR.drawable.ic_memory, "این گوشی حافظهٔ کافی ندارد",
                    "دستیار آفلاین دست‌کم ۳ گیگابایت رم می‌خواهد. بقیهٔ برنامه، از جمله افزودن سریع با زبان فارسی، کامل کار می‌کند.",
                    Roozban.colors.warning, Modifier.weight(1f),
                )
                state.status == EngineStatus.NO_MODEL -> Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    EmptyState(
                        DsR.drawable.ic_assistant, "دستیار آفلاین",
                        "برای شروع یک مدل زبانی دانلود کن. همه‌چیز روی خود گوشی اجرا می‌شود و هیچ اطلاعاتی بیرون نمی‌رود.",
                        Roozban.colors.focus,
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onOpenModels) {
                        Icon(painterResource(DsR.drawable.ic_download), null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("انتخاب و دانلود مدل")
                    }
                }
                else -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (state.items.isEmpty()) item { Intro(onPick = { input = it }) }
                        items(state.items, key = { it.id }) { item ->
                            if (item.role == ChatRole.USER) UserBubble(item.text) else AnswerBubble(item, viewModel)
                        }
                    }
                    InputBar(
                        value = input,
                        onChange = { input = it },
                        busy = state.busy,
                        onSend = {
                            viewModel.send(input)
                            input = ""
                        },
                        onStop = viewModel::stop,
                    )
                }
            }
        }
    }
}

private fun statusText(state: AssistantUiState): String = when (state.status) {
    EngineStatus.UNSUPPORTED -> "پشتیبانی نمی‌شود"
    EngineStatus.NO_MODEL -> "مدلی نصب نیست"
    EngineStatus.LOADING -> "در حال آماده‌سازی ${state.modelName.orEmpty()}…"
    EngineStatus.READY -> "آماده · ${state.modelName.orEmpty()} · آفلاین"
    EngineStatus.FAILED -> "خطا در بارگذاری مدل"
    EngineStatus.IDLE -> state.modelName?.let { "$it · آفلاین" }.orEmpty()
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Intro(onPick: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        IconBadge(DsR.drawable.ic_assistant, Roozban.colors.focus, size = 64.dp)
        Spacer(Modifier.height(12.dp))
        Text("چه کاری برات انجام بدم؟", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(
            "کار بساز، جابه‌جا کن، تیک بزن، وقت خالی پیدا کن یا عادت ثبت کن.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SUGGESTIONS.forEach { s ->
                Text(
                    s,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Roozban.colors.focus.onContainer,
                    modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(Roozban.colors.focus.container)
                        .clickable { onPick(s) }.padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun UserBubble(text: String) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
        Text(
            text,
            color = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.widthIn(max = 320.dp)
                .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomEnd = 18.dp, bottomStart = 4.dp))
                .background(MaterialTheme.colorScheme.primary)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun AnswerBubble(item: ChatItem, viewModel: AssistantViewModel) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        AppCard(modifier = Modifier.widthIn(max = 340.dp), accent = if (item.error) Roozban.colors.error.color else null) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (item.streaming && item.text.isEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("در حال فکر کردن…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else if (item.text.isNotEmpty()) {
                    Text(item.text, color = if (item.error) Roozban.colors.error.color else MaterialTheme.colorScheme.onSurface)
                }
                item.results.forEach { ResultRow(it) }
                item.problems.forEach { ProblemRow(it) }
                item.pending?.let { Confirmation(it, onYes = { viewModel.confirm(item.id) }, onNo = { viewModel.reject(item.id) }) }
                if (item.canUndo) {
                    TextButton(onClick = { viewModel.undo(item.id) }, contentPadding = PaddingValues(horizontal = 8.dp)) {
                        Icon(painterResource(DsR.drawable.ic_undo), null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("برگردان")
                    }
                } else if (item.undone) {
                    StatusPill("برگردانده شد", Roozban.colors.info)
                }
            }
        }
    }
}

@Composable
private fun ResultRow(result: ActionResult) {
    val role = if (result.ok) Roozban.colors.completed else Roozban.colors.error
    Row(verticalAlignment = Alignment.Top) {
        Icon(
            painterResource(if (result.ok) DsR.drawable.ic_check else DsR.drawable.ic_warning), null,
            tint = role.color, modifier = Modifier.size(18.dp).padding(top = 2.dp),
        )
        Spacer(Modifier.width(8.dp))
        Column {
            Text(result.message, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            when (val out = result.output) {
                is ActionOutput.TaskList -> {
                    val today = LocalDate.now()
                    out.tasks.take(MAX_LISTED).forEach { t ->
                        Text(
                            "• ${t.title}" + (t.due?.let { " — ${Describe.due(it, today)}" } ?: ""),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (out.tasks.size > MAX_LISTED) Text("و ${out.tasks.size - MAX_LISTED} کار دیگر", style = MaterialTheme.typography.bodySmall)
                }
                is ActionOutput.FreeSlots -> Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                    out.starts.forEach { StatusPill(PersianDateFormatter.time(it), Roozban.colors.success) }
                }
                null -> Unit
            }
        }
    }
}

@Composable
private fun ProblemRow(action: PlannedAction) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(painterResource(DsR.drawable.ic_warning), null, tint = Roozban.colors.warning.color, modifier = Modifier.size(18.dp).padding(top = 2.dp))
        Spacer(Modifier.width(8.dp))
        Column {
            Text(action.problem ?: action.summary, style = MaterialTheme.typography.bodyMedium)
            action.candidates.forEach { Text("• ${it.title}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun Confirmation(plan: Plan, onYes: () -> Unit, onNo: () -> Unit) {
    val role = Roozban.colors.warning
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(role.container).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("این کارها را انجام بدهم؟", fontWeight = FontWeight.Bold, color = role.onContainer)
        plan.actions.filter { it.runnable }.forEach { Text("• ${it.summary}", style = MaterialTheme.typography.bodyMedium, color = role.onContainer) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onYes) { Text("بله، انجام بده") }
            OutlinedButton(onClick = onNo) { Text("نه") }
        }
    }
}

@Composable
private fun InputBar(value: String, onChange: (String) -> Unit, busy: Boolean, onSend: () -> Unit, onStop: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer).navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text("مثلاً: پنجشنبه عصر خرید میوه") },
            maxLines = 4,
            shape = RoundedCornerShape(24.dp),
        )
        Spacer(Modifier.width(8.dp))
        if (busy) {
            FilledIconButton(onClick = onStop, shape = CircleShape) { Icon(painterResource(DsR.drawable.ic_stop), "توقف") }
        } else {
            FilledIconButton(onClick = onSend, enabled = value.isNotBlank(), shape = CircleShape) { Icon(painterResource(DsR.drawable.ic_send), "ارسال") }
        }
    }
}

private const val MAX_LISTED = 8
