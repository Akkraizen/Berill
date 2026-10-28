package dev.akkraizen.berill.plugins.planner.widgets

import dev.akkraizen.berill.api.Item
import dev.akkraizen.berill.api.PluginContext
import dev.akkraizen.berill.api.i18n.Tr
import dev.akkraizen.berill.api.ui.dayMonth
import dev.akkraizen.berill.api.ui.today
import dev.akkraizen.berill.api.widgets.WidgetAction
import dev.akkraizen.berill.api.widgets.WidgetBlock
import dev.akkraizen.berill.api.widgets.WidgetButton
import dev.akkraizen.berill.api.widgets.WidgetCheck
import dev.akkraizen.berill.api.widgets.WidgetContent
import dev.akkraizen.berill.api.widgets.WidgetEffect
import dev.akkraizen.berill.api.widgets.WidgetIcon
import dev.akkraizen.berill.api.widgets.WidgetProvider
import dev.akkraizen.berill.api.widgets.WidgetRequest
import dev.akkraizen.berill.api.widgets.WidgetRow
import dev.akkraizen.berill.api.widgets.WidgetSize
import dev.akkraizen.berill.api.widgets.WidgetTone
import dev.akkraizen.berill.plugins.planner.PlannerPlugin
import dev.akkraizen.berill.plugins.planner.data.CalendarData
import dev.akkraizen.berill.plugins.planner.data.CalendarEvent
import dev.akkraizen.berill.plugins.planner.data.PlannerData
import dev.akkraizen.berill.plugins.planner.data.PlannerTask
import dev.akkraizen.berill.plugins.planner.data.eventsOn
import dev.akkraizen.berill.plugins.planner.data.parse
import dev.akkraizen.berill.plugins.planner.data.taskOrder
import dev.akkraizen.berill.plugins.planner.i18n.CalendarText
import dev.akkraizen.berill.plugins.planner.i18n.PlannerText
import kotlinx.datetime.LocalDate

internal fun plannerWidgets(ctx: PluginContext): List<WidgetProvider> =
  listOf(PlannerTodayWidget(PlannerData(ctx), CalendarData(ctx)), MonthCalendarWidget(CalendarData(ctx)), AgendaWidget(CalendarData(ctx)))

private class PlannerTodayWidget(private val tasks: PlannerData, private val calendar: CalendarData) : WidgetProvider {
  override val id = "today"
  override val title: Tr = PlannerText.widgetTodayTitle
  override val description: Tr = PlannerText.widgetTodayDescription
  override val size = WidgetSize.Medium

  private fun tasksOn(day: LocalDate): List<Item<PlannerTask>> =
    tasks.tasks.all().filter { it.value.date == day.toString() }.sortedWith(taskOrder)

  private fun eventsToday(day: LocalDate): List<Item<CalendarEvent>> = eventsOn(calendar.events.all(), day)

  override suspend fun content(request: WidgetRequest): WidgetContent {
    val day = today()
    val dayTasks = tasksOn(day)
    val events = eventsToday(day)
    val done = dayTasks.count { it.value.done }
    val blocks = buildList {
      if (events.isNotEmpty())
        add(
          WidgetBlock.Rows(
            events.map { item ->
              WidgetRow(
                title = item.value.title.ifBlank { CalendarText.pageCalendar() },
                subtitle = item.value.location.ifBlank { null },
                trailing = eventTimeLabel(item.value),
                action = WidgetAction.Open(page = PlannerPlugin.PAGE_CALENDAR),
                tone = if (item.value.isBirthday) WidgetTone.Accent else WidgetTone.Normal,
                icon = WidgetIcon.Calendar
              )
            }
          )
        )
      if (dayTasks.isNotEmpty())
        add(
          WidgetBlock.Checks(
            dayTasks.map { item ->
              val parsed = parse(item.value.text)
              WidgetCheck(
                label = parsed.title,
                checked = item.value.done,
                action = WidgetAction.Run(TOGGLE, mapOf(TASK to item.id)),
                trailing = parsed.time
              )
            }
          )
        )
    }
    return WidgetContent(
      title = PlannerText.today(),
      subtitle = listOfNotNull(day.dayMonth(), dayTasks.takeIf { it.isNotEmpty() }?.let { PlannerText.widgetDone(done, it.size)() }).joinToString(" · "),
      icon = WidgetIcon.Calendar,
      open = WidgetAction.Open(page = PlannerPlugin.PAGE_PLAN),
      buttons = listOf(WidgetButton(PlannerText.widgetAdd(), WidgetAction.Open(page = PlannerPlugin.PAGE_PLAN), WidgetIcon.Add)),
      blocks = blocks,
      empty = PlannerText.noTasks()
    )
  }

  override suspend fun perform(request: WidgetRequest, action: WidgetAction.Run): WidgetEffect {
    if (action.id != TOGGLE) return WidgetEffect.Refresh()
    val id = action.args[TASK] ?: return WidgetEffect.Refresh()
    tasks.edit(id) { it.copy(done = !it.done) }
    return WidgetEffect.Refresh()
  }

  override suspend fun summary(request: WidgetRequest): WidgetRow? {
    val day = today()
    val dayTasks = tasksOn(day)
    val events = eventsToday(day)
    if (dayTasks.isEmpty() && events.isEmpty()) return null
    val next = dayTasks.firstOrNull { !it.value.done }?.let { parse(it.value.text).title }
      ?: events.firstOrNull()?.value?.let { "${eventTimeLabel(it)} ${it.title}" }
    return WidgetRow(
      title = PlannerText.title(),
      subtitle = next,
      trailing = if (dayTasks.isEmpty()) PlannerText.widgetEvents(events.size)() else PlannerText.widgetDone(dayTasks.count { it.value.done }, dayTasks.size)(),
      action = WidgetAction.Open(pluginId = PlannerPlugin.id, page = PlannerPlugin.PAGE_PLAN),
      tone = if (dayTasks.isNotEmpty() && dayTasks.all { it.value.done }) WidgetTone.Good else WidgetTone.Normal,
      icon = WidgetIcon.Calendar
    )
  }

  private companion object {
    const val TOGGLE = "toggle"
    const val TASK = "task"
  }
}
