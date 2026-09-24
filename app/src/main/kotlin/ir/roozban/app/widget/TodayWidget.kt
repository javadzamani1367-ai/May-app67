package ir.roozban.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.CheckBox
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import ir.roozban.app.MainActivity
import ir.roozban.app.R
import ir.roozban.app.entry.QuickAddActivity
import ir.roozban.core.calendar.PersianDateFormatter
import ir.roozban.core.calendar.toJalali
import ir.roozban.core.domain.CompleteTaskUseCase
import ir.roozban.core.domain.TaskRepository
import ir.roozban.core.model.Task
import ir.roozban.core.model.TaskDue
import kotlinx.coroutines.flow.first
import java.time.LocalDate

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun tasks(): TaskRepository
    fun complete(): CompleteTaskUseCase
}

private fun Context.entryPoint() = EntryPointAccessors.fromApplication(applicationContext, WidgetEntryPoint::class.java)

private val TaskIdKey = ActionParameters.Key<String>("task_id")

/** Home-screen widget: today's and overdue tasks, tick to complete, «+» for quick add. */
class TodayWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val today = LocalDate.now()
        val tasks = context.entryPoint().tasks().observeOpenTasks().first()
            .filter { it.due != null && !it.due!!.date.isAfter(today) }
            .take(MAX_ITEMS)
        val header = PersianDateFormatter.fullDate(today.toJalali())
        provideContent {
            GlanceTheme {
                WidgetContent(header, tasks, today, context)
            }
        }
    }

    companion object {
        private const val MAX_ITEMS = 12
    }
}

@Composable
private fun WidgetContent(header: String, tasks: List<Task>, today: LocalDate, context: Context) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(20.dp)
            .padding(12.dp),
    ) {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = header,
                style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 15.sp, color = GlanceTheme.colors.onSurface),
                modifier = GlanceModifier.defaultWeight().clickable(actionStartActivity<MainActivity>()),
            )
            Image(
                provider = ImageProvider(R.drawable.ic_widget_add),
                contentDescription = context.getString(R.string.widget_add),
                colorFilter = ColorFilter.tint(GlanceTheme.colors.primary),
                modifier = GlanceModifier.size(32.dp).clickable(actionStartActivity<QuickAddActivity>()),
            )
        }
        Spacer(GlanceModifier.size(6.dp))
        if (tasks.isEmpty()) {
            Text(
                text = context.getString(R.string.widget_empty),
                style = TextStyle(fontSize = 13.sp, color = GlanceTheme.colors.onSurfaceVariant),
            )
        } else {
            LazyColumn {
                items(tasks, itemId = { it.id.hashCode().toLong() }) { task ->
                    val overdue = task.due!!.date.isBefore(today)
                    val time = (task.due as? TaskDue.At)?.let { PersianDateFormatter.time(it.time) }
                    Row(modifier = GlanceModifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        CheckBox(
                            checked = false,
                            onCheckedChange = actionRunCallback<CompleteTaskAction>(actionParametersOf(TaskIdKey to task.id)),
                        )
                        Text(
                            text = task.title,
                            maxLines = 1,
                            style = TextStyle(fontSize = 14.sp, color = GlanceTheme.colors.onSurface),
                            modifier = GlanceModifier.defaultWeight().clickable(actionStartActivity<MainActivity>()),
                        )
                        if (time != null || overdue) {
                            Spacer(GlanceModifier.width(6.dp))
                            Text(
                                text = if (overdue) context.getString(R.string.widget_overdue) else time.orEmpty(),
                                style = TextStyle(
                                    fontSize = 12.sp,
                                    color = if (overdue) GlanceTheme.colors.error else GlanceTheme.colors.onSurfaceVariant,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}

class CompleteTaskAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = parameters[TaskIdKey] ?: return
        val entry = context.entryPoint()
        entry.tasks().get(id)?.let { entry.complete()(it) }
        TodayWidget().update(context, glanceId)
    }
}

class TodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayWidget()
}
