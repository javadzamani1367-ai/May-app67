package ir.roozban.feature.tasks.calendar

import ir.roozban.core.designsystem.components.RoozbanTopBar
import ir.roozban.core.designsystem.components.RoozbanFab
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import ir.roozban.core.ui.JalaliDatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.roozban.core.calendar.JalaliDate
import ir.roozban.core.calendar.PersianDateFormatter
import ir.roozban.core.calendar.PersianDigits
import ir.roozban.core.calendar.PersianNames
import ir.roozban.core.calendar.PersianWeek
import ir.roozban.core.designsystem.R as DsR
import ir.roozban.feature.tasks.R
import ir.roozban.feature.tasks.TaskEditorSheet
import ir.roozban.feature.tasks.quadrantColor
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.floor
import kotlin.math.roundToInt

private val HOUR_HEIGHT = 56.dp
private val TIME_COLUMN = 40.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CalendarScreen(
    onOpenSettings: () -> Unit,
    viewModel: CalendarViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var menu by remember { mutableStateOf(false) }
    var converting by remember { mutableStateOf(false) }
    var goingTo by remember { mutableStateOf(false) }
    var pickingCity by remember { mutableStateOf(false) }
    var eventDraft by remember { mutableStateOf<EventDraft?>(null) }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            RoozbanTopBar(
                title = {
                    Text(
                        state.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable { goingTo = true },
                    )
                },
                actions = {
                    // Right-to-left: «previous» (pointing right) comes first, «next» (pointing left) after it.
                    IconButton(onClick = viewModel::previous) {
                        Icon(painterResource(DsR.drawable.ic_chevron_right), stringResource(R.string.calendar_previous))
                    }
                    TextButton(onClick = viewModel::goToToday) { Text(stringResource(R.string.calendar_today)) }
                    IconButton(onClick = viewModel::next) {
                        Icon(painterResource(DsR.drawable.ic_chevron_left), stringResource(R.string.calendar_next))
                    }
                    Box {
                        IconButton(onClick = { menu = true }) {
                            Icon(painterResource(DsR.drawable.ic_more_vert), stringResource(R.string.calendar_more))
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.calendar_go_to)) },
                                leadingIcon = { Icon(painterResource(DsR.drawable.ic_calendar_month), null) },
                                onClick = { menu = false; goingTo = true },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.converter_title)) },
                                leadingIcon = { Icon(painterResource(DsR.drawable.ic_swap), null) },
                                onClick = { menu = false; converting = true },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.calendar_prayer_title)) },
                                leadingIcon = { Icon(painterResource(DsR.drawable.ic_mosque), null) },
                                onClick = { menu = false; pickingCity = true },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.tasks_settings)) },
                                leadingIcon = { Icon(painterResource(DsR.drawable.ic_settings), null) },
                                onClick = { menu = false; onOpenSettings() },
                            )
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            if (state.mode == CalendarMode.MONTH) {
                RoozbanFab(
                    onClick = { eventDraft = EventDraft(date = state.selected) },
                    icon = { Icon(painterResource(DsR.drawable.ic_event_star), null) },
                    text = { Text(stringResource(R.string.event_new)) },
                )
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ModeChip(CalendarMode.MONTH, R.string.calendar_month, state.mode, viewModel::setMode)
                ModeChip(CalendarMode.WEEK, R.string.calendar_week, state.mode, viewModel::setMode)
                ModeChip(CalendarMode.DAY, R.string.calendar_day, state.mode, viewModel::setMode)
            }
            when (state.mode) {
                CalendarMode.MONTH -> MonthView(
                    state = state,
                    onSelect = viewModel::select,
                    onOpenDay = viewModel::openDay,
                    onSwipe = { next -> if (next) viewModel.next() else viewModel.previous() },
                    onOpenTask = { editingId = it },
                    onOpenEvent = { event ->
                        val date = ir.roozban.core.domain.EventDates.next(event, state.today, state.hijriOffset)?.date ?: state.today
                        eventDraft = EventDraft.of(event, date)
                    },
                    onPickCity = { pickingCity = true },
                )
                else -> TimelineView(
                    days = state.days,
                    onOpenTask = { editingId = it },
                    onMove = viewModel::moveTask,
                )
            }
        }
    }

    editingId?.let { id ->
        TaskEditorSheet(taskId = id, onClose = { editingId = null }, onDeleted = { editingId = null })
    }
    eventDraft?.let { draft ->
        EventEditorSheet(
            initial = draft,
            today = state.today,
            hijriOffset = state.hijriOffset,
            onSave = { viewModel.saveEvent(it); eventDraft = null },
            onDelete = draft.id?.let { id ->
                {
                    viewModel.deleteEvent(id)
                    eventDraft = null
                }
            },
            onDismiss = { eventDraft = null },
        )
    }
    if (converting) {
        DateConverterDialog(
            today = state.today,
            hijriOffset = state.hijriOffset,
            onGoTo = { viewModel.goTo(it); converting = false },
            onDismiss = { converting = false },
        )
    }
    if (goingTo) {
        JalaliDatePickerDialog(
            initial = state.selected,
            today = state.today,
            onConfirm = { d -> if (d != null) viewModel.goTo(d); goingTo = false },
            onDismiss = { goingTo = false },
        )
    }
    if (pickingCity) {
        CityPickerDialog(current = state.city, onPick = { viewModel.setPrayerCity(it); pickingCity = false }, onDismiss = { pickingCity = false })
    }
}

@Composable
private fun ModeChip(mode: CalendarMode, label: Int, current: CalendarMode, onSelect: (CalendarMode) -> Unit) {
    FilterChip(selected = mode == current, onClick = { onSelect(mode) }, label = { Text(stringResource(label)) })
}

// ---------------------------------------------------------------- day / week timeline

/** State of a long-press drag, in root coordinates. */
private class DragState {
    var task by mutableStateOf<CalendarTask?>(null)
    var pointer by mutableStateOf(Offset.Zero)

    /** Distance from the pointer to the top of the dragged block, so the block does not jump. */
    var grab = 0f
}

@Composable
private fun TimelineView(days: List<CalendarDay>, onOpenTask: (String) -> Unit, onMove: (String, LocalDate, LocalTime) -> Unit) {
    val density = LocalDensity.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val hourPx = with(density) { HOUR_HEIGHT.toPx() }
    val timeColumnPx = with(density) { TIME_COLUMN.toPx() }
    val scroll = rememberScrollState(initial = (8 * hourPx).roundToInt())
    val drag = remember { DragState() }
    // Drag callbacks outlive recompositions; always read the latest days.
    val currentDays by rememberUpdatedState(days)
    var gridBounds by remember { mutableStateOf(Rect.Zero) }
    var containerOrigin by remember { mutableStateOf(Offset.Zero) }

    var now by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            now = LocalTime.now()
        }
    }

    /** Where a drop at [top] (root y of the block's top edge) and [x] lands: (day, time) or null. */
    fun target(x: Float, top: Float): Pair<LocalDate, LocalTime>? {
        if (top < gridBounds.top - hourPx / 2 || x !in gridBounds.left..gridBounds.right) return null
        val shown = currentDays
        val dayWidth = (gridBounds.width - timeColumnPx) / shown.size
        val fromStart = if (rtl) gridBounds.right - x - timeColumnPx else x - gridBounds.left - timeColumnPx
        val index = floor(fromStart / dayWidth).toInt().coerceIn(0, shown.size - 1)
        val minutes = (top - gridBounds.top + scroll.value) / hourPx * 60f
        return shown[index].date to CalendarBuilder.snapTime(minutes)
    }

    fun startDrag(task: CalendarTask, coords: LayoutCoordinates?, start: Offset, grabBlockTop: Boolean) {
        val root = coords?.localToRoot(start) ?: return
        drag.task = task
        drag.pointer = root
        drag.grab = if (grabBlockTop) start.y else 0f
    }

    fun endDrag() {
        val task = drag.task ?: return
        target(drag.pointer.x, drag.pointer.y - drag.grab)?.let { (date, time) -> onMove(task.id, date, time) }
        drag.task = null
    }

    Box(Modifier.fillMaxSize().onGloballyPositioned { containerOrigin = it.positionInRoot() }) {
        Column(Modifier.fillMaxSize()) {
            // Day headers
            Row(Modifier.fillMaxWidth()) {
                Spacer(Modifier.width(TIME_COLUMN))
                days.forEach { day -> DayHeader(day, Modifier.weight(1f)) }
            }
            // All-day tasks: long-press and drag onto the grid to give them a time.
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                Text(
                    stringResource(R.string.calendar_all_day),
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(TIME_COLUMN),
                    textAlign = TextAlign.Center,
                )
                days.forEach { day ->
                    Column(Modifier.weight(1f).padding(horizontal = 1.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        day.allDay.forEach { task ->
                            var coords by remember { mutableStateOf<LayoutCoordinates?>(null) }
                            TaskChip(
                                task = task,
                                dimmed = drag.task?.id == task.id,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .onGloballyPositioned { coords = it }
                                    .pointerInput(task.id) {
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = { startDrag(task, coords, it, grabBlockTop = false) },
                                            onDrag = { change, amount ->
                                                change.consume()
                                                drag.pointer += amount
                                            },
                                            onDragEnd = { endDrag() },
                                            onDragCancel = { drag.task = null },
                                        )
                                    }
                                    .clickable { onOpenTask(task.id) },
                            )
                        }
                    }
                }
            }
            HorizontalDivider()
            // Hour grid
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .onGloballyPositioned { gridBounds = it.boundsInRoot() }
                    .verticalScroll(scroll),
            ) {
                Row(Modifier.fillMaxWidth().height(HOUR_HEIGHT * 24)) {
                    Column(Modifier.width(TIME_COLUMN)) {
                        for (h in 0 until 24) {
                            Text(
                                PersianDigits.format2(h),
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.height(HOUR_HEIGHT).fillMaxWidth(),
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                    days.forEach { day ->
                        DayColumn(
                            day = day,
                            now = now,
                            draggingId = drag.task?.id,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            onOpenTask = onOpenTask,
                            onDragStart = { task, coords, start -> startDrag(task, coords, start, grabBlockTop = true) },
                            onDrag = { drag.pointer += it },
                            onDragEnd = { endDrag() },
                            onDragCancel = { drag.task = null },
                        )
                    }
                }
            }
        }

        // The dragged task follows the finger, labelled with the time it would get.
        drag.task?.let { task ->
            val landing = target(drag.pointer.x, drag.pointer.y - drag.grab)
            val topLeft = drag.pointer - containerOrigin - Offset(0f, drag.grab)
            Surface(
                color = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(8.dp),
                shadowElevation = 6.dp,
                modifier = Modifier.offset { IntOffset(topLeft.x.roundToInt() - 60, topLeft.y.roundToInt()) }.width(120.dp),
            ) {
                Column(Modifier.padding(6.dp)) {
                    Text(task.title, color = MaterialTheme.colorScheme.onPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp)
                    Text(
                        landing?.let { (date, time) -> PersianDateFormatter.relativeDateTime(date.atTime(time), LocalDate.now()) } ?: "—",
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontSize = 11.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun DayHeader(day: CalendarDay, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(modifier.padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            PersianNames.WEEKDAYS_SHORT[PersianWeek.indexOf(day.date.dayOfWeek)],
            style = MaterialTheme.typography.labelSmall,
            color = if (day.isHoliday) holidayColor() else colors.onSurfaceVariant,
        )
        Box(
            Modifier.size(28.dp).clip(CircleShape).background(if (day.isToday) colors.primary else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                day.jalaliDay,
                style = MaterialTheme.typography.titleSmall,
                color = when {
                    day.isToday -> colors.onPrimary
                    day.isHoliday -> holidayColor()
                    else -> colors.onSurface
                },
            )
        }
        day.holidayNames.firstOrNull()?.let {
            Text(it, fontSize = 8.sp, color = holidayColor(), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun DayColumn(
    day: CalendarDay,
    now: LocalTime,
    draggingId: String?,
    modifier: Modifier,
    onOpenTask: (String) -> Unit,
    onDragStart: (CalendarTask, LayoutCoordinates?, Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    BoxWithConstraints(
        modifier
            .background(if (day.isHoliday) holidayColor().copy(alpha = 0.06f) else Color.Transparent)
            .border(0.5.dp, colors.outlineVariant.copy(alpha = 0.5f)),
    ) {
        val laneWidth = maxWidth
        for (h in 1 until 24) {
            HorizontalDivider(Modifier.offset(y = HOUR_HEIGHT * h), color = colors.outlineVariant.copy(alpha = 0.5f))
        }
        day.timed.filter { it.start != null }.forEach { task ->
            val start = task.start!!
            val top = HOUR_HEIGHT * (start.toSecondOfDay() / 3600f)
            val height = (HOUR_HEIGHT * (task.durationMinutes / 60f)).coerceAtLeast(20.dp)
            val width = laneWidth / task.lanes
            var coords by remember(task.id) { mutableStateOf<LayoutCoordinates?>(null) }
            TaskChip(
                task = task,
                dimmed = draggingId == task.id,
                modifier = Modifier
                    .offset(x = width * task.lane, y = top)
                    .width(width)
                    .height(height)
                    .padding(1.dp)
                    .onGloballyPositioned { coords = it }
                    .pointerInput(task.id) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { onDragStart(task, coords, it) },
                            onDrag = { change, amount ->
                                change.consume()
                                onDrag(amount)
                            },
                            onDragEnd = onDragEnd,
                            onDragCancel = onDragCancel,
                        )
                    }
                    .clickable { onOpenTask(task.id) },
            )
        }
        if (day.isToday) {
            Box(
                Modifier
                    .offset(y = HOUR_HEIGHT * (now.toSecondOfDay() / 3600f))
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(colors.error),
            )
        }
    }
}

@Composable
private fun TaskChip(task: CalendarTask, dimmed: Boolean, modifier: Modifier) {
    // Plain tasks take the accent so the timeline is never grey.
    val color = if (task.quadrant == ir.roozban.core.model.Quadrant.NONE || task.quadrant == ir.roozban.core.model.Quadrant.ELIMINATE) {
        MaterialTheme.colorScheme.primary
    } else {
        quadrantColor(task.quadrant)
    }
    Box(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = if (dimmed) 0.08f else 0.16f))
            .border(1.dp, color.copy(alpha = if (dimmed) 0.25f else 0.7f), RoundedCornerShape(8.dp))
            .padding(horizontal = 3.dp, vertical = 1.dp),
    ) {
        Text(task.title, fontSize = 10.sp, lineHeight = 12.sp, maxLines = 3, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface)
    }
}
