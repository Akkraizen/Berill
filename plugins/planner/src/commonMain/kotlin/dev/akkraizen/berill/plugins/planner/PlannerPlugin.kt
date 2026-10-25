package dev.akkraizen.berill.plugins.planner

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.akkraizen.berill.api.BerillPlugin
import dev.akkraizen.berill.api.Deadline
import dev.akkraizen.berill.api.DeadlineSource
import dev.akkraizen.berill.api.HomeCard
import dev.akkraizen.berill.api.PluginContext
import dev.akkraizen.berill.api.all
import dev.akkraizen.berill.api.boolean
import dev.akkraizen.berill.api.observeBoolean
import dev.akkraizen.berill.api.set
import dev.akkraizen.berill.api.ui.cards.SectionCard
import dev.akkraizen.berill.api.ui.nowMillis
import dev.akkraizen.berill.api.ui.today
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

object PlannerPlugin : BerillPlugin {
  const val TASKS = "planner"
  const val WEEKS = "planner_weeks"

  override val id = "planner"
  override val title
    get() = PlannerText.title()

  override val icon = Icons.Outlined.CalendarMonth
  override val order = 15
  override val description
    get() = PlannerText.description()

  override val installedByDefault = true
  override val hasSettings = true

  @Composable
  override fun Settings(ctx: PluginContext) {
    val show by
      remember(ctx) {
        ctx.settings.observeBoolean(SHOW_DEADLINES, true)
      }
        .collectAsState(ctx.settings.boolean(SHOW_DEADLINES, true))
    SectionCard(PlannerText.deadlines(), Icons.Outlined.Alarm) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
          Text(PlannerText.showDeadlines(), style = MaterialTheme.typography.bodyLarge)
          Text(
            PlannerText.showDeadlinesHint(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
        Switch(checked = show, onCheckedChange = { ctx.settings.set(SHOW_DEADLINES, it) })
      }
    }
  }

  override val collections = listOf(TASKS, WEEKS)

  @Composable
  override fun Screen(ctx: PluginContext) = PlannerScreen(ctx)

  override fun homeCards(ctx: PluginContext) =
    listOf(
      HomeCard("today", PlannerText.todayPlans, order = 15) {
        val data = remember(ctx) { PlannerData(ctx) }
        val all by remember(data) { data.tasks.observe() }.collectAsState(emptyList())
        val deadlines by remember(data) { data.deadlines() }.collectAsState(emptyList())
        DayCard(data, today(), all, deadlines, compact = false, showHeader = false)
      }
    )
}

internal const val SHOW_DEADLINES = "showDeadlines"

internal class PlannerData(val ctx: PluginContext) {
  val tasks = ctx.data.collection(PlannerPlugin.TASKS, PlannerTask.serializer())
  val weeks = ctx.data.collection(PlannerPlugin.WEEKS, WeekNotes.serializer())

  fun addDayTask(date: String, text: String) {
    if (text.isBlank()) return
    tasks.save(ctx.data.newId(), PlannerTask(text = normalizeInput(text), date = date, order = nowMillis()))
  }

  fun edit(id: String, transform: (PlannerTask) -> PlannerTask) {
    val current = tasks.get(id) ?: return
    tasks.save(id, transform(current))
  }

  fun addGoal(week: String, text: String) {
    if (text.isBlank()) return
    tasks.save(ctx.data.newId(), PlannerTask(text = text.trim(), week = week, order = nowMillis()))
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  fun deadlines(): Flow<List<Deadline>> =
    ctx.settings.observeBoolean(SHOW_DEADLINES, true).flatMapLatest { show ->
      val sources = if (show) ctx.capabilities.all<DeadlineSource>() else emptyList()
      if (sources.isEmpty())
        flowOf(emptyList())
      else
        combine(sources.map { it.deadlines() }) { lists -> lists.flatMap { it } }
    }
}
