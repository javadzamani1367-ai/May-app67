package ir.roozban.feature.tasks

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
import androidx.compose.material3.ExtendedFloatingActionButton
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
import androidx.compose.material3.TopAppBar
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
import ir.roozban.core.model.Quadrant
import ir.roozban.core.model.ReminderKind

@Composable
internal fun TaskListRoute(
    mode: ListMode,
    onOpenSettings: () -> Unit,
    viewModel: TaskListViewModel = hiltViewModel(),
) {
    LaunchedEffect(mode) { viewModel.setMode(mode) }
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
) {
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            when (mode) {
                                ListMode.TODAY -> R.string.tasks_title_today
                                ListMode.UPCOMING -> R.string.tasks_title_upcoming
                                ListMode.INBOX -> R.string.tasks_title_inbox
                            },
                        ),
                    )
                },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(painterResource(DsR.drawable.ic_settings), stringResource(R.string.tasks_settings))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
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
                item(key = "header") { DateHeader(header) }
                item(key = "week") { WeekStrip(header.week) }
                item(key = "permissions") { ReminderPermissionBanner() }
            }
            state.sections.forEach { section ->
                section.title?.let { title ->
                    item(key = "section-${section.kind}-$title") { SectionTitle(title, section.kind == SectionKind.OVERDUE) }
                }
                items(section.tasks, key = { it.id }) { task ->
                    TaskRow(task, onToggle = { onToggle(task.id) }, onClick = { onOpen(task.id) }, modifier = Modifier.animateItem())
                }
            }
            if (state.completed.isNotEmpty()) {
                item(key = "completed-title") { SectionTitle(stringResource(R.string.tasks_completed_today), false) }
                items(state.completed, key = { "done-" + it.id }) { task ->
                    TaskRow(task, onToggle = { onToggle(task.id) }, onClick = { onOpen(task.id) }, modifier = Modifier.animateItem())
                }
            }
            if (state.isEmpty && state.mode == mode) item(key = "empty") { EmptyState(mode) }
        }
    }
}

@Composable
private fun DateHeader(header: TodayHeader) {
    Column(Modifier.padding(horizontal = 4.dp)) {
        Text(
            text = header.weekday,
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.semantics { heading() },
        )
        Text(text = header.date, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(4.dp))
        Text(
            text = header.secondaryDates,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun WeekStrip(week: List<WeekDay>) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
        ) {
            week.forEach { day -> WeekDayCell(day) }
        }
    }
}

@Composable
private fun WeekDayCell(day: WeekDay) {
    val background by animateColorAsState(
        if (day.isToday) MaterialTheme.colorScheme.primary else Color.Transparent,
        label = "weekDayBackground",
    )
    val content = when {
        day.isToday -> MaterialTheme.colorScheme.onPrimary
        day.isWeekend -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurface
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(day.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier.size(36.dp).clip(CircleShape).background(background),
            contentAlignment = Alignment.Center,
        ) {
            Text(day.dayOfMonth, style = MaterialTheme.typography.titleMedium, color = content)
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
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painterResource(DsR.drawable.ic_warning),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onErrorContainer)
            }
            Spacer(Modifier.height(4.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
            TextButton(onClick = onAction, modifier = Modifier.align(Alignment.End)) { Text(action) }
        }
    }
}

@Composable
private fun SectionTitle(title: String, isOverdue: Boolean) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = if (isOverdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 12.dp, start = 4.dp).semantics { heading() },
    )
}

@Composable
internal fun quadrantColor(quadrant: Quadrant): Color = when (quadrant) {
    Quadrant.DO_FIRST -> MaterialTheme.colorScheme.error
    Quadrant.SCHEDULE -> MaterialTheme.colorScheme.primary
    Quadrant.DELEGATE -> MaterialTheme.colorScheme.tertiary
    Quadrant.ELIMINATE, Quadrant.NONE -> MaterialTheme.colorScheme.outline
}

@Composable
private fun TaskRow(task: TaskItem, onToggle: () -> Unit, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CompletionCircle(task.completed, quadrantColor(task.quadrant), onToggle)
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

@Composable
private fun TaskMeta(task: TaskItem) {
    val parts = buildList {
        task.dueLabel?.let { add(Triple(DsR.drawable.ic_schedule, it, task.overdue)) }
        task.recurrenceLabel?.let { add(Triple(DsR.drawable.ic_repeat, it, false)) }
        task.estimateLabel?.let { add(Triple(DsR.drawable.ic_timer, it, false)) }
    }
    if (parts.isEmpty() && task.reminder == null) return
    Row(
        modifier = Modifier.padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        parts.forEach { (icon, label, isError) ->
            val color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(icon), contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(3.dp))
                Text(label, style = MaterialTheme.typography.labelMedium, color = color, maxLines = 1)
            }
        }
        task.reminder?.let {
            Icon(
                painterResource(if (it == ReminderKind.ALARM) DsR.drawable.ic_alarm else DsR.drawable.ic_notifications),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

@Composable
private fun EmptyState(mode: ListMode) {
    val (title, body) = when (mode) {
        ListMode.TODAY -> R.string.empty_today_title to R.string.empty_today_body
        ListMode.UPCOMING -> R.string.empty_upcoming_title to R.string.empty_upcoming_body
        ListMode.INBOX -> R.string.empty_inbox_title to R.string.empty_inbox_body
    }
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 48.dp, start = 16.dp, end = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(title), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
