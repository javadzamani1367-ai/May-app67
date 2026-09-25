package ir.roozban.feature.tasks.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.roozban.core.calendar.JalaliDate
import ir.roozban.core.calendar.Occasion
import ir.roozban.core.calendar.PersianDateFormatter
import ir.roozban.core.calendar.PersianDigits
import ir.roozban.core.calendar.PersianNames
import ir.roozban.core.calendar.toJalali
import ir.roozban.core.designsystem.R as DsR
import ir.roozban.core.designsystem.icons.eventIcon
import ir.roozban.core.designsystem.icons.eventKindName
import ir.roozban.core.designsystem.theme.CalendarColors
import ir.roozban.core.designsystem.theme.TagColors
import ir.roozban.core.domain.EventOccurrence
import ir.roozban.core.model.EventKind
import ir.roozban.core.model.PersonalEvent
import ir.roozban.feature.tasks.R
import ir.roozban.feature.tasks.quadrantColor
import java.time.LocalDate

/** Holiday red; the light variant on dark themes (detected from the text color, since backgrounds may be see-through). */
@Composable
internal fun holidayColor(): Color =
    if (MaterialTheme.colorScheme.onSurface.isLight()) CalendarColors.holidayDark else CalendarColors.holidayLight

private fun Color.isLight(): Boolean = (0.299f * red + 0.587f * green + 0.114f * blue) > 0.5f

/** Month grid with swipe navigation, and below it the selected day, the month's occasions and the user's own. */
@Composable
internal fun MonthView(
    state: CalendarUiState,
    onSelect: (LocalDate) -> Unit,
    onOpenDay: (LocalDate) -> Unit,
    onSwipe: (next: Boolean) -> Unit,
    onOpenTask: (String) -> Unit,
    onOpenEvent: (PersonalEvent) -> Unit,
    onPickCity: () -> Unit,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val swipe by rememberUpdatedState(onSwipe)
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.pointerInput(Unit) {
                var total = 0f
                detectHorizontalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = {
                        // Right-to-left: the next month sits to the left, so a rightward swipe brings it in.
                        if (total > SWIPE_PX) swipe(true) else if (total < -SWIPE_PX) swipe(false)
                    },
                    onHorizontalDrag = { _, amount -> total += amount },
                )
            },
        ) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                PersianNames.WEEKDAYS_SHORT.forEachIndexed { i, label ->
                    Text(
                        label,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (i == 6) holidayColor() else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            state.days.chunked(7).forEach { week ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                    week.forEach { day ->
                        MonthCell(day, day.date == state.selected, Modifier.weight(1f)) {
                            if (day.date == state.selected) onOpenDay(day.date) else onSelect(day.date)
                        }
                    }
                }
            }
        }
        val tabs = listOf(R.string.calendar_tab_day, R.string.calendar_tab_occasions, R.string.calendar_tab_mine)
        PrimaryTabRow(selectedTabIndex = tab, containerColor = Color.Transparent) {
            tabs.forEachIndexed { i, label ->
                Tab(selected = tab == i, onClick = { tab = i }, text = { Text(stringResource(label), maxLines = 1) })
            }
        }
        when (tab) {
            0 -> DayTab(state, onOpenTask, onOpenEvent, onPickCity)
            1 -> OccasionsTab(state, onSelect)
            else -> MineTab(state, onOpenEvent)
        }
    }
}

@Composable
private fun MonthCell(day: CalendarDay, isSelected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val holiday = holidayColor()
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = modifier
            .aspectRatio(0.86f)
            .padding(2.dp)
            .clip(shape)
            .background(
                when {
                    isSelected -> colors.primaryContainer
                    day.isHoliday && day.inMonth -> holiday.copy(alpha = 0.07f)
                    else -> Color.Transparent
                },
            )
            .then(if (day.isToday) Modifier.border(2.dp, colors.primary, shape) else Modifier)
            .clickable(onClick = onClick)
            .padding(top = 3.dp, bottom = 3.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val alpha = if (day.inMonth) 1f else 0.3f
        Text(
            day.jalaliDay,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = if (day.isToday) FontWeight.Bold else FontWeight.Medium,
            color = (if (day.isHoliday) holiday else colors.onSurface).copy(alpha = alpha),
        )
        val secondary = listOfNotNull(day.gregorianDay, day.hijriDay).joinToString("  ")
        if (secondary.isNotEmpty()) {
            Text(secondary, fontSize = 9.sp, color = colors.onSurfaceVariant.copy(alpha = alpha), maxLines = 1)
        }
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
            day.events.firstOrNull()?.let {
                Icon(painterResource(eventIcon(it.event.kind)), null, tint = TagColors.color(it.event.color).copy(alpha = alpha), modifier = Modifier.size(11.dp))
            }
            if (day.occasions.isNotEmpty()) Box(Modifier.size(5.dp).clip(CircleShape).background(colors.tertiary.copy(alpha = alpha)))
            val tasks = day.allDay + day.timed
            tasks.take(2).forEach { Box(Modifier.size(5.dp).clip(CircleShape).background(quadrantColor(it.quadrant).copy(alpha = alpha))) }
            if (tasks.size > 2) Text("+", fontSize = 9.sp, color = colors.onSurfaceVariant)
        }
    }
}

// ------------------------------------------------------------------ tabs

@Composable
private fun DayTab(state: CalendarUiState, onOpenTask: (String) -> Unit, onOpenEvent: (PersonalEvent) -> Unit, onPickCity: () -> Unit) {
    val day = state.selectedDay ?: return
    val j = day.date.toJalali()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp)) {
        item(key = "dates") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    PersianDigits.format(j.day),
                    style = MaterialTheme.typography.displaySmall,
                    color = if (day.isHoliday) holidayColor() else MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(PersianDateFormatter.fullDate(j), style = MaterialTheme.typography.titleMedium)
                    Text(
                        listOfNotNull(PersianDateFormatter.gregorian(day.date), PersianDateFormatter.hijri(day.date, state.hijriOffset)).joinToString("  |  "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    relative(day.date, state.today)?.let {
                        Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        items(day.holidayNames, key = { "h$it" }) { OccasionLine(it, holiday = true) }
        items(day.occasions, key = { "o${it.title}" }) { OccasionLine(it.title, holiday = false) }
        items(day.events, key = { "e${it.event.id}" }) { EventRow(it, state.today, onOpenEvent) }
        items(day.allDay + day.timed.sortedBy { it.start }, key = { "t${it.id}" }) { task ->
            Card(
                onClick = { onOpenTask(task.id) },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(quadrantColor(task.quadrant)))
                    Spacer(Modifier.width(10.dp))
                    Text(task.title, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    task.start?.let { Text(PersianDateFormatter.time(it), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
        item(key = "prayer") { PrayerCard(state, onPickCity) }
    }
}

@Composable
private fun OccasionLine(title: String, holiday: Boolean) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(if (holiday) holidayColor() else MaterialTheme.colorScheme.tertiary))
        Spacer(Modifier.width(8.dp))
        Text(
            title,
            style = MaterialTheme.typography.bodyMedium,
            color = if (holiday) holidayColor() else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (holiday) FontWeight.Medium else FontWeight.Normal,
        )
    }
}

@Composable
private fun PrayerCard(state: CalendarUiState, onPickCity: () -> Unit) {
    val prayer = state.prayer
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(DsR.drawable.ic_mosque), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.calendar_prayer_title), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                TextButton(onClick = onPickCity) {
                    Text(state.city?.name ?: stringResource(R.string.calendar_pick_city))
                }
            }
            if (prayer != null) {
                val items = listOf(
                    R.string.prayer_fajr to prayer.fajr,
                    R.string.prayer_sunrise to prayer.sunrise,
                    R.string.prayer_dhuhr to prayer.dhuhr,
                    R.string.prayer_sunset to prayer.sunset,
                    R.string.prayer_maghrib to prayer.maghrib,
                    R.string.prayer_midnight to prayer.midnight,
                )
                items.chunked(3).forEach { row ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        row.forEach { (label, time) ->
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(stringResource(label), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(PersianDateFormatter.time(time), style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    }
                }
            } else {
                Text(stringResource(R.string.calendar_prayer_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun OccasionsTab(state: CalendarUiState, onSelect: (LocalDate) -> Unit) {
    val rows: List<Pair<LocalDate, Any>> =
        (state.monthOccasions.map { it.date to it } + state.monthEvents.map { it.date to it }).sortedBy { it.first }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp)) {
        if (rows.isEmpty()) item { Text(stringResource(R.string.calendar_no_occasions), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(rows) { (date, item) ->
            val j = date.toJalali()
            Row(
                Modifier.fillMaxWidth().clickable { onSelect(date) }.padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val holiday = (item as? Occasion)?.holiday == true
                Column(Modifier.width(56.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        PersianDigits.format(j.day),
                        style = MaterialTheme.typography.titleLarge,
                        color = if (holiday) holidayColor() else MaterialTheme.colorScheme.onSurface,
                    )
                    Text(PersianNames.weekday(date.dayOfWeek), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                when (item) {
                    is Occasion -> Column(Modifier.weight(1f)) {
                        Text(item.title, color = if (item.holiday) holidayColor() else MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyMedium)
                        if (item.holiday) Text(stringResource(R.string.calendar_holiday), style = MaterialTheme.typography.labelSmall, color = holidayColor())
                    }
                    is EventOccurrence -> Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        Icon(painterResource(eventIcon(item.event.kind)), null, tint = TagColors.color(item.event.color), modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(eventTitle(item), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

@Composable
private fun MineTab(state: CalendarUiState, onOpenEvent: (PersonalEvent) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp)) {
        if (state.upcoming.isEmpty()) {
            item {
                Text(stringResource(R.string.calendar_mine_empty), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            }
        }
        items(state.upcoming, key = { it.event.id }) { EventRow(it, state.today, onOpenEvent) }
    }
}

@Composable
private fun EventRow(occ: EventOccurrence, today: LocalDate, onOpen: (PersonalEvent) -> Unit) {
    val color = TagColors.color(occ.event.color)
    Card(
        onClick = { onOpen(occ.event) },
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.12f)),
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
                Icon(painterResource(eventIcon(occ.event.kind)), null, tint = Color.White, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(eventTitle(occ), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    eventKindName(occ.event.kind) + " · " + PersianDateFormatter.relativeDay(occ.date, today).let {
                        if (it.any { c -> c.isDigit() }) PersianDateFormatter.dayMonthYear(JalaliDate.from(occ.date)) else it
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val days = occ.date.toEpochDay() - today.toEpochDay()
            if (days > 0) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(PersianDigits.format(days), style = MaterialTheme.typography.titleMedium, color = color)
                    Text(stringResource(R.string.calendar_days_left), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else if (days == 0L) {
                Text(stringResource(R.string.calendar_today), style = MaterialTheme.typography.labelLarge, color = color)
            }
        }
    }
}

/** «تولد سارا (۳۰ سالگی)». */
@Composable
internal fun eventTitle(occ: EventOccurrence): String {
    val n = occ.count?.takeIf { it > 0 } ?: return occ.event.title
    val suffix = if (occ.event.kind == EventKind.BIRTHDAY) {
        stringResource(R.string.event_age, PersianDigits.format(n))
    } else {
        stringResource(R.string.event_anniversary, PersianDigits.format(n))
    }
    return "${occ.event.title} ($suffix)"
}

private fun relative(date: LocalDate, today: LocalDate): String? {
    val diff = date.toEpochDay() - today.toEpochDay()
    return when {
        diff == 0L -> "امروز"
        diff == 1L -> "فردا"
        diff == -1L -> "دیروز"
        diff > 1 -> "${PersianDigits.format(diff)} روز دیگر"
        else -> "${PersianDigits.format(-diff)} روز پیش"
    }
}

private const val SWIPE_PX = 120f
