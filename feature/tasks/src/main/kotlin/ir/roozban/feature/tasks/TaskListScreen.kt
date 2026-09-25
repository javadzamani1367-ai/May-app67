package ir.roozban.feature.tasks

import ir.roozban.core.calendar.PersianDigits
import ir.roozban.core.designsystem.components.AppCard
import ir.roozban.core.designsystem.theme.priorityColor
import ir.roozban.core.designsystem.theme.Roozban
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Brush
import ir.roozban.core.designsystem.components.RoozbanTopBar
import ir.roozban.core.designsystem.components.RoozbanFab
import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.roozban.core.alarm.ReminderPermissions
import ir.roozban.core.designsystem.R as DsR
import ir.roozban.core.designsystem.theme.TagColors
import ir.roozban.core.model.Quadrant
import ir.roozban.core.model.ReminderKind

@Composable
internal fun TaskListRoute(
    mode: ListMode,
    onOpenSettings: () -> Unit,
    projectId: String? = null,
    onBack: (() -> Unit)? = null,
    onOpenAssistant: (() -> Unit)? = null,
    viewModel: TaskListViewModel = hiltViewModel(),
) {
    LaunchedEffect(mode, projectId) { viewModel.setMode(mode, projectId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val quickAdd by viewModel.quickAdd.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val undoLabel = stringResource(R.string.tasks_undo)
    val deletedLabel = stringResource(R.string.tasks_deleted)
    var sheetOpen by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }

    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is TaskListEvent.Message -> {
                    val result = snackbar.showSnackbar(
                        message = event.text,
                        actionLabel = if (event.undo != null) undoLabel else null,
                        duration = SnackbarDuration.Short,
                    )
                    if (result == SnackbarResult.ActionPerformed) event.undo?.let(viewModel::undo)
                }
            }
        }
    }

    TaskListScreen(
        mode = mode,
        state = state,
        snackbar = snackbar,
        onAdd = { sheetOpen = true },
        onToggle = viewModel::onToggleComplete,
        onOpen = { editingId = it },
        onOpenSettings = onOpenSettings,
        onBack = onBack,
        onOpenAssistant = onOpenAssistant,
    )

    if (sheetOpen) {
        QuickAddSheet(
            state = quickAdd,
            onTextChange = viewModel::onQuickAddTextChange,
            onSubmit = { if (viewModel.submitQuickAdd()) sheetOpen = false },
            onDismiss = {
                viewModel.dismissQuickAdd()
                sheetOpen = false
            },
        )
    }
    editingId?.let { id ->
        TaskEditorSheet(
            taskId = id,
            onClose = { editingId = null },
            onDeleted = { undo ->
                editingId = null
                viewModel.offerUndo(deletedLabel, undo)
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TaskListScreen(
    mode: ListMode,
    state: TaskListUiState,
    snackbar: SnackbarHostState,
    onAdd: () -> Unit,
    onToggle: (String) -> Unit,
    onOpen: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onBack: (() -> Unit)? = null,
    onOpenAssistant: (() -> Unit)? = null,
) {
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            RoozbanTopBar(
                title = {
                    Text(
                        when (mode) {
                            ListMode.TODAY -> stringResource(R.string.tasks_title_today)
                            ListMode.UPCOMING -> stringResource(R.string.tasks_title_upcoming)
                            ListMode.INBOX -> stringResource(R.string.tasks_title_inbox)
                            ListMode.PROJECT -> state.projectName.orEmpty()
                        },
                    )
                },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(painterResource(DsR.drawable.ic_arrow_back), stringResource(R.string.tasks_back))
                        }
                    }
                },
                actions = {
                    if (onOpenAssistant != null) {
                        IconButton(onClick = onOpenAssistant) {
                            Icon(painterResource(DsR.drawable.ic_assistant), stringResource(R.string.tasks_assistant), tint = Roozban.colors.focus.color)
                        }
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(painterResource(DsR.drawable.ic_settings), stringResource(R.string.tasks_settings))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            RoozbanFab(
                onClick = onAdd,
                icon = { Icon(painterResource(DsR.drawable.ic_add), contentDescription = null) },
                text = { Text(stringResource(R.string.tasks_add)) },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 96.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            state.header?.let { header ->
                item(key = "header") {
                    val today = state.sections.firstOrNull { it.kind == SectionKind.TODAY }?.tasks?.size ?: 0
                    val overdue = state.sections.firstOrNull { it.kind == SectionKind.OVERDUE }?.tasks?.size ?: 0
                    DateHero(header, today, overdue, state.completed.size)
                }
                item(key = "permissions") { ReminderPermissionBanner() }
            }
            state.sections.forEach { section ->
                section.title?.let { title ->
                    item(key = "section-${section.kind}-$title") { SectionTitle(title, section.kind, section.tasks.size) }
                }
                items(section.tasks, key = { it.id }) { task ->
                    TaskRow(task, onToggle = { onToggle(task.id) }, onClick = { onOpen(task.id) }, modifier = Modifier.animateItem())
                }
            }
            if (state.completed.isNotEmpty()) {
                item(key = "completed-title") { SectionTitle(stringResource(R.string.tasks_completed_today), null, state.completed.size, done = true) }
                items(state.completed, key = { "done-" + it.id }) { task ->
                    TaskRow(task, onToggle = { onToggle(task.id) }, onClick = { onOpen(task.id) }, modifier = Modifier.animateItem())
                }
            }
            if (state.isEmpty && state.mode == mode) item(key = "empty") { EmptyState(mode) }
        }
    }
}

/**
 * The day at a glance: a calm gradient card with the Jalali date, the other calendars, the
 * week strip and three counters (today, overdue, done).
 */
@Composable
private fun DateHero(header: TodayHeader, today: Int, overdue: Int, done: Int) {
    val scheme = MaterialTheme.colorScheme
    val gradient = Brush.linearGradient(listOf(scheme.primary, Roozban.colors.focus.color.copy(alpha = 0.92f)))
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(MaterialTheme.shapes.large)
            .background(gradient)
            .padding(horizontal = 18.dp, vertical = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = header.weekday,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = scheme.onPrimary,
                    modifier = Modifier.semantics { heading() },
                )
                Text(text = header.date, style = MaterialTheme.typography.titleLarge, color = scheme.onPrimary)
                Text(
                    text = header.secondaryDates,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onPrimary.copy(alpha = 0.8f),
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            header.week.forEach { day -> WeekDayCell(day) }
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HeroCounter(stringResource(R.string.hero_today), today, Modifier.weight(1f))
            HeroCounter(stringResource(R.string.hero_overdue), overdue, Modifier.weight(1f), alert = overdue > 0)
            HeroCounter(stringResource(R.string.hero_done), done, Modifier.weight(1f))
        }
    }
}

@Composable
private fun HeroCounter(label: String, value: Int, modifier: Modifier, alert: Boolean = false) {
    val onPrimary = MaterialTheme.colorScheme.onPrimary
    Column(
        modifier
            .clip(MaterialTheme.shapes.small)
            .background(if (alert) Roozban.colors.error.color.copy(alpha = 0.85f) else onPrimary.copy(alpha = 0.16f))
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(PersianDigits.format(value), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = onPrimary)
        Text(label, style = MaterialTheme.typography.labelSmall, color = onPrimary.copy(alpha = 0.9f))
    }
}

@Composable
private fun WeekDayCell(day: WeekDay) {
    val onPrimary = MaterialTheme.colorScheme.onPrimary
    val background by animateColorAsState(if (day.isToday) onPrimary else Color.Transparent, label = "weekDayBackground")
    val content = when {
        day.isToday -> MaterialTheme.colorScheme.primary
        day.isWeekend -> Color(0xFFFFD1CC)
        else -> onPrimary
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(day.label, style = MaterialTheme.typography.labelMedium, color = onPrimary.copy(alpha = 0.75f))
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier.size(34.dp).clip(CircleShape).background(background),
            contentAlignment = Alignment.Center,
        ) {
            Text(day.dayOfMonth, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = content)
        }
    }
}

/** Shown when reminders cannot work reliably; each problem links to its fix. */
@Composable
private fun ReminderPermissionBanner() {
    val context = LocalContext.current
    var status by remember { mutableStateOf(ReminderPermissions.status(context)) }
    var askedOnce by rememberSaveable { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) {
        status = ReminderPermissions.status(context)
        onPauseOrDispose { }
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        status = ReminderPermissions.status(context)
    }
    when {
        !status.notifications -> Banner(
            title = stringResource(R.string.permission_notifications_title),
            body = stringResource(R.string.permission_notifications_body),
            action = stringResource(R.string.permission_notifications_action),
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !askedOnce) {
                askedOnce = true
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                context.startActivity(ReminderPermissions.notificationSettings(context))
            }
        }
        !status.exactAlarms -> Banner(
            title = stringResource(R.string.permission_exact_title),
            body = stringResource(R.string.permission_exact_body),
            action = stringResource(R.string.permission_open_settings),
        ) {
            context.startActivity(ReminderPermissions.exactAlarmSettings(context))
        }
    }
}

@Composable
private fun Banner(title: String, body: String, action: String, onAction: () -> Unit) {
    val role = Roozban.colors.warning
    AppCard(container = role.container, accent = role.color, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(DsR.drawable.ic_warning), contentDescription = null, tint = role.color, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = role.onContainer)
            }
            Spacer(Modifier.height(4.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium, color = role.onContainer)
            TextButton(onClick = onAction, modifier = Modifier.align(Alignment.End)) { Text(action, color = role.color, fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun SectionTitle(title: String, kind: SectionKind?, count: Int, done: Boolean = false) {
    val color = when {
        done -> Roozban.colors.completed.color
        kind == SectionKind.OVERDUE -> Roozban.colors.error.color
        kind == SectionKind.TODAY -> MaterialTheme.colorScheme.primary
        kind == SectionKind.NO_DATE -> Roozban.colors.info.color
        else -> MaterialTheme.colorScheme.secondary
    }
    Row(
        Modifier.padding(top = 14.dp, start = 4.dp, bottom = 2.dp).semantics { heading() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(width = 4.dp, height = 16.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(8.dp))
        Text(text = title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = color)
        Spacer(Modifier.width(8.dp))
        Text(
            PersianDigits.format(count),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.clip(CircleShape).background(color.copy(alpha = 0.12f)).padding(horizontal = 8.dp, vertical = 1.dp),
        )
    }
}

@Composable
internal fun quadrantColor(quadrant: Quadrant): Color = priorityColor(quadrant, MaterialTheme.colorScheme.outline)

@Composable
private fun TaskRow(task: TaskItem, onToggle: () -> Unit, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val priority = quadrantColor(task.quadrant)
    // State at a glance: done → soft teal card, overdue → red edge, important → its priority color edge.
    val accent = when {
        task.completed -> Roozban.colors.completed.color
        task.overdue -> Roozban.colors.error.color
        task.quadrant == Quadrant.NONE || task.quadrant == Quadrant.ELIMINATE -> null
        else -> priority
    }
    AppCard(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        accent = accent,
        container = if (task.completed) Roozban.colors.completed.container.copy(alpha = 0.55f) else MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CompletionCircle(task.completed, if (task.completed) Roozban.colors.completed.color else priority, onToggle)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textDecoration = if (task.completed) TextDecoration.LineThrough else null,
                    color = if (task.completed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                )
                TaskMeta(task)
            }
        }
    }
}

@Composable
private fun CompletionCircle(checked: Boolean, color: Color, onToggle: () -> Unit) {
    val fill by animateColorAsState(if (checked) color else Color.Transparent, label = "check")
    val description = stringResource(if (checked) R.string.tasks_reopen else R.string.tasks_complete)
    Box(
        modifier = Modifier
            .size(26.dp)
            .clip(CircleShape)
            .border(2.dp, color, CircleShape)
            .background(fill)
            .clickable(onClickLabel = description, onClick = onToggle),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            Icon(
                painterResource(DsR.drawable.ic_check),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.surface,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TaskMeta(task: TaskItem) {
    val parts = buildList {
        task.dueLabel?.let { add(Triple(DsR.drawable.ic_schedule, it, task.overdue)) }
        task.recurrenceLabel?.let { add(Triple(DsR.drawable.ic_repeat, it, false)) }
        task.estimateLabel?.let { add(Triple(DsR.drawable.ic_timer, it, false)) }
        task.subtaskProgress?.let { add(Triple(DsR.drawable.ic_checklist, it, false)) }
    }
    if (parts.isEmpty() && task.reminder == null && task.project == null && task.labels.isEmpty()) return
    FlowRow(
        modifier = Modifier.padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        task.project?.let { TagText(DsR.drawable.ic_folder, it.name, TagColors.color(it.color)) }
        parts.forEachIndexed { i, (icon, label, isError) ->
            val color = when {
                isError -> Roozban.colors.error.color
                i == 0 && icon == DsR.drawable.ic_schedule -> Roozban.colors.info.color
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            TagText(icon, label, color)
        }
        task.labels.forEach { TagText(DsR.drawable.ic_label, it.name, TagColors.color(it.color)) }
        task.reminder?.let {
            Icon(
                painterResource(if (it == ReminderKind.ALARM) DsR.drawable.ic_alarm else DsR.drawable.ic_notifications),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp).align(Alignment.CenterVertically),
            )
        }
    }
}

@Composable
private fun TagText(icon: Int, label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(icon), contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(3.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = color, maxLines = 1)
    }
}

@Composable
private fun EmptyState(mode: ListMode) {
    val (title, body) = when (mode) {
        ListMode.TODAY -> R.string.empty_today_title to R.string.empty_today_body
        ListMode.UPCOMING -> R.string.empty_upcoming_title to R.string.empty_upcoming_body
        ListMode.INBOX -> R.string.empty_inbox_title to R.string.empty_inbox_body
        ListMode.PROJECT -> R.string.empty_project_title to R.string.empty_project_body
    }
    val (icon, role) = when (mode) {
        ListMode.TODAY -> DsR.drawable.ic_today to Roozban.colors.success
        ListMode.UPCOMING -> DsR.drawable.ic_upcoming to Roozban.colors.info
        ListMode.INBOX -> DsR.drawable.ic_inbox to Roozban.colors.focus
        ListMode.PROJECT -> DsR.drawable.ic_folder to Roozban.colors.warning
    }
    ir.roozban.core.designsystem.components.EmptyState(icon, stringResource(title), stringResource(body), role)
}
