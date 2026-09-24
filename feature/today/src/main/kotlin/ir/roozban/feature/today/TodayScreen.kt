package ir.roozban.feature.today

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.roozban.core.designsystem.R as DsR
import ir.roozban.core.designsystem.theme.RoozbanTheme

@Composable
internal fun TodayRoute(viewModel: TodayViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val quickAdd by viewModel.quickAdd.collectAsStateWithLifecycle()
    var sheetOpen by rememberSaveable { mutableStateOf(false) }

    TodayScreen(state = state, onAddClick = { sheetOpen = true })

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
}

@Composable
internal fun TodayScreen(state: TodayUiState, onAddClick: () -> Unit) {
    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddClick,
                icon = { Icon(painterResource(DsR.drawable.ic_add), contentDescription = null) },
                text = { Text(stringResource(R.string.today_add_task)) },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = padding.calculateTopPadding() + 24.dp,
                bottom = padding.calculateBottomPadding() + 96.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { DateHeader(state) }
            item { WeekStrip(state.week) }
            if (state.sessionTasks.isEmpty()) {
                item { EmptyState() }
            } else {
                items(state.sessionTasks, key = { it.id }) { task ->
                    TaskCard(task, Modifier.animateItem())
                }
                item {
                    Text(
                        text = stringResource(R.string.today_session_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun DateHeader(state: TodayUiState) {
    Column {
        Text(
            text = state.weekday,
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.semantics { heading() },
        )
        Text(text = state.date, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(4.dp))
        Text(
            text = state.secondaryDates,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun WeekStrip(week: List<WeekDay>) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
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

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 48.dp, start = 16.dp, end = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.today_empty_title),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.today_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun TaskCard(task: DraftTask, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(task.title, style = MaterialTheme.typography.titleMedium)
            val meta = listOfNotNull(task.whenLabel, task.recurrenceLabel, task.estimateLabel)
            AnimatedVisibility(visible = meta.isNotEmpty(), enter = fadeIn(), exit = fadeOut()) {
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    meta.forEach {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier
                                .clip(MaterialTheme.shapes.small)
                                .background(MaterialTheme.colorScheme.secondaryContainer)
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, locale = "fa")
@Composable
private fun TodayScreenPreview() {
    RoozbanTheme {
        TodayScreen(
            state = TodayUiState(
                weekday = "پنجشنبه",
                date = "۲ مهر ۱۴۰۵",
                secondaryDates = "۲۴ سپتامبر ۲۰۲۶ · ۱۳ ربیع‌الثانی ۱۴۴۸",
                week = listOf("ش", "ی", "د", "س", "چ", "پ", "ج")
                    .zip(listOf("۲۸", "۲۹", "۳۰", "۳۱", "۱", "۲", "۳"))
                    .mapIndexed { i, (label, day) -> WeekDay(label, day, isToday = i == 5, isWeekend = i == 6) },
                sessionTasks = listOf(DraftTask(1, "جلسه با علی", "پس‌فردا، ۱۰:۰۰", null, "۱ ساعت")),
            ),
            onAddClick = {},
        )
    }
}
