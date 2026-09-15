package us.liyifan.things.widget

import android.content.Context
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.action.ActionParameters.Key
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
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
import kotlinx.coroutines.flow.first
import us.liyifan.things.ThingsApp
import us.liyifan.things.model.Item
import us.liyifan.things.model.ViewId
import us.liyifan.things.model.buildModel

/**
 * Today, on the home screen.
 *
 * It reads the local mirror rather than the network, so it draws instantly and correctly with no
 * signal, and ticking something goes through the same queue as ticking it in the app.
 */
class TodayWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val container = (context.applicationContext as ThingsApp).container
        val snapshot = container.repository.snapshot.first()
        val items = snapshot?.let { buildModel(it).view(ViewId.TODAY) }.orEmpty()

        provideContent {
            GlanceTheme {
                Column(
                    GlanceModifier
                        .fillMaxSize()
                        .background(GlanceTheme.colors.background)
                        .padding(12.dp),
                ) {
                    Row(GlanceModifier.fillMaxWidth()) {
                        Text(
                            "Today",
                            style = TextStyle(
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = GlanceTheme.colors.onBackground,
                            ),
                        )
                        Spacer(GlanceModifier.defaultWeight())
                        Text(
                            items.size.toString(),
                            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant),
                        )
                    }
                    Spacer(GlanceModifier.size(8.dp))
                    if (items.isEmpty()) {
                        Text("Nothing for today", style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant))
                    } else {
                        LazyColumn {
                            items(items, itemId = { it.id.hashCode().toLong() }) { task ->
                                WidgetRow(task)
                            }
                        }
                    }
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun WidgetRow(task: Item) {
    Row(
        GlanceModifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The box is the tick target; the whole row would make a mis-tap complete something.
        Box(
            GlanceModifier
                .size(18.dp)
                .clickable(
                    actionRunCallback<CompleteTaskAction>(
                        actionParametersOf(CompleteTaskAction.TASK_ID to task.id),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text("▢", style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant))
        }
        Spacer(GlanceModifier.width(8.dp))
        Text(
            task.title.ifBlank { "New To-Do" },
            maxLines = 2,
            style = TextStyle(color = GlanceTheme.colors.onBackground),
        )
    }
}

/** Ticking from the widget takes the same path as ticking in the app: local first, queued. */
class CompleteTaskAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = parameters[TASK_ID] ?: return
        val container = (context.applicationContext as ThingsApp).container
        container.repository.completeTask(id, done = true)
        TodayWidget().updateAll(context)
    }

    companion object {
        val TASK_ID: Key<String> = ActionParameters.Key("taskId")
    }
}

class TodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayWidget()
}
