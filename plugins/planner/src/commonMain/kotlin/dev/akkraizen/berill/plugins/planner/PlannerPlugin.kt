package dev.akkraizen.berill.plugins.planner

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ViewWeek
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
import dev.akkraizen.berill.api.HomeCard
import dev.akkraizen.berill.api.PluginContext
import dev.akkraizen.berill.api.PluginPage
import dev.akkraizen.berill.api.PluginPermissions
import dev.akkraizen.berill.api.all
import dev.akkraizen.berill.api.boolean
import dev.akkraizen.berill.api.observeBoolean
import dev.akkraizen.berill.api.set
import dev.akkraizen.berill.api.ui.cards.SectionCard
import dev.akkraizen.berill.api.ui.today
import dev.akkraizen.berill.plugins.planner.data.PlannerData
import dev.akkraizen.berill.plugins.planner.data.SHOW_DEADLINES
import dev.akkraizen.berill.plugins.planner.i18n.CalendarText
import dev.akkraizen.berill.plugins.planner.i18n.PlannerText
import dev.akkraizen.berill.plugins.planner.ui.CalendarPage
import dev.akkraizen.berill.plugins.planner.ui.DayCard
import dev.akkraizen.berill.plugins.planner.ui.GoogleCalendarSettings
import dev.akkraizen.berill.plugins.planner.ui.PlannerScreen

object PlannerPlugin : BerillPlugin {
  const val TASKS = "planner"
  const val WEEKS = "planner_weeks"
  const val EVENTS = "planner_events"
  const val PAGE_PLAN = "plan"
  const val PAGE_CALENDAR = "calendar"

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
    GoogleCalendarSettings(ctx)
  }

  override val collections = listOf(TASKS, WEEKS, EVENTS)

  override val permissions = PluginPermissions(network = listOf("oauth2.googleapis.com", "www.googleapis.com"))

  override val pages = listOf(
    PluginPage(PAGE_PLAN, CalendarText.pagePlan, Icons.Outlined.ViewWeek) { ctx -> PlannerScreen(ctx) },
    PluginPage(PAGE_CALENDAR, CalendarText.pageCalendar, Icons.Outlined.CalendarMonth) { ctx -> CalendarPage(ctx) }
  )

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
