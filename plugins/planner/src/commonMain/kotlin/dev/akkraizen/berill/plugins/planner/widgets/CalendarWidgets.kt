package dev.akkraizen.berill.plugins.planner.widgets

import dev.akkraizen.berill.api.Item
import dev.akkraizen.berill.api.i18n.Tr
import dev.akkraizen.berill.api.ui.DAYS_SHORT
import dev.akkraizen.berill.api.ui.YearMonth
import dev.akkraizen.berill.api.ui.dayMonth
import dev.akkraizen.berill.api.ui.plusDays
import dev.akkraizen.berill.api.ui.toDateOrNull
import dev.akkraizen.berill.api.ui.today
import dev.akkraizen.berill.api.ui.weekdayIndex
import dev.akkraizen.berill.api.widgets.WidgetAction
import dev.akkraizen.berill.api.widgets.WidgetBlock
import dev.akkraizen.berill.api.widgets.WidgetButton
import dev.akkraizen.berill.api.widgets.WidgetContent
import dev.akkraizen.berill.api.widgets.WidgetDay
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
import dev.akkraizen.berill.plugins.planner.data.eventsOn
import dev.akkraizen.berill.plugins.planner.data.hhmm
import dev.akkraizen.berill.plugins.planner.data.monthGrid
import dev.akkraizen.berill.plugins.planner.data.parseTime
import dev.akkraizen.berill.plugins.planner.i18n.CalendarText
import dev.akkraizen.berill.plugins.planner.i18n.PlannerText
import dev.akkraizen.berill.plugins.planner.ui.DAY_ROUTE
import dev.akkraizen.berill.plugins.planner.ui.NEW_ROUTE
import kotlinx.datetime.LocalDate

internal fun eventTimeLabel(event: CalendarEvent): String =
  when {
    event.isBirthday -> CalendarText.birthday()
    event.allDay -> CalendarText.allDay()
    else -> listOfNotNull(parseTime(event.start)?.hhmm(), parseTime(event.end)?.hhmm()).joinToString("–")
  }

internal fun eventRow(item: Item<CalendarEvent>, day: LocalDate): WidgetRow =
  WidgetRow(
    title = item.value.title.ifBlank { CalendarText.pageCalendar() },
    subtitle = item.value.location.ifBlank { null },
    trailing = eventTimeLabel(item.value),
    action = WidgetAction.Open(route = DAY_ROUTE + day, page = PlannerPlugin.PAGE_CALENDAR),
    tone = if (item.value.isBirthday) WidgetTone.Accent else WidgetTone.Normal,
    icon = WidgetIcon.Calendar
  )

internal fun dayTitle(day: LocalDate, now: LocalDate): String =
  when (day) {
    now -> PlannerText.today()
    now.plusDays(1) -> PlannerText.widgetTomorrow()
    else -> "${DAYS_SHORT[day.weekdayIndex]}, ${day.dayMonth()}"
  }

private fun addAction(day: LocalDate) = WidgetAction.Open(route = NEW_ROUTE + day, page = PlannerPlugin.PAGE_CALENDAR)

internal class MonthCalendarWidget(private val calendar: CalendarData) : WidgetProvider {
  override val id = "calendar"
  override val title: Tr = PlannerText.widgetCalendarTitle
  override val description: Tr = PlannerText.widgetCalendarDescription
  override val size = WidgetSize.Large

  private fun month(request: WidgetRequest, now: LocalDate): YearMonth {
    val stored = request.state[MONTH]?.split('-')?.mapNotNull { it.toIntOrNull() }
    return if (stored != null && stored.size == 2 && stored[1] in 1..12) YearMonth(stored[0], stored[1]) else YearMonth.of(now)
  }

  private fun selected(request: WidgetRequest, month: YearMonth, now: LocalDate): LocalDate =
    request.state[DAY].toDateOrNull()?.takeIf { it in month } ?: if (now in month) now else month.first

  override suspend fun content(request: WidgetRequest): WidgetContent {
    val now = today()
    val month = month(request, now)
    val selected = selected(request, month, now)
    val events = calendar.events.all().filter { !it.value.deleted }
    val grid = monthGrid(month.year, month.month)
    val dayEvents = eventsOn(events, selected)
    return WidgetContent(
      title = month.title,
      subtitle = dayTitle(selected, now),
      icon = WidgetIcon.Calendar,
      open = WidgetAction.Open(page = PlannerPlugin.PAGE_CALENDAR),
      buttons = listOf(
        WidgetButton(PlannerText.widgetPrevious(), WidgetAction.Run(SHIFT, mapOf(BY to "-1")), WidgetIcon.Previous),
        WidgetButton(PlannerText.widgetNext(), WidgetAction.Run(SHIFT, mapOf(BY to "1")), WidgetIcon.Next)
      ),
      blocks = listOfNotNull(
        WidgetBlock.Month(
          weekdays = DAYS_SHORT,
          days = grid.map { day ->
            WidgetDay(
              label = day.day.toString(),
              inMonth = day in month,
              today = day == now,
              selected = day == selected,
              events = eventsOn(events, day).size,
              action = WidgetAction.Run(SELECT, mapOf(DAY to day.toString()))
            )
          }
        ),
        if (dayEvents.isEmpty()) WidgetBlock.Text(PlannerText.widgetNoEvents(), WidgetTone.Muted) else WidgetBlock.Rows(dayEvents.map { eventRow(it, selected) }),
        WidgetBlock.Buttons(
          listOfNotNull(
            if (month != YearMonth.of(now) || selected != now) WidgetButton(PlannerText.today(), WidgetAction.Run(TODAY), WidgetIcon.Calendar) else null,
            WidgetButton(PlannerText.widgetOpenDay(), WidgetAction.Open(route = DAY_ROUTE + selected, page = PlannerPlugin.PAGE_CALENDAR), WidgetIcon.Open),
            WidgetButton(PlannerText.widgetNewEvent(), addAction(selected), WidgetIcon.Add, primary = true)
          )
        )
      )
    )
  }

  override suspend fun perform(request: WidgetRequest, action: WidgetAction.Run): WidgetEffect {
    val now = today()
    return when (action.id) {
      SHIFT -> {
        val next = month(request, now) + (action.args[BY]?.toIntOrNull() ?: 0)
        val day = if (now in next) now else next.first
        WidgetEffect.Refresh(state = mapOf(MONTH to "${next.year}-${next.month}", DAY to day.toString()))
      }
      SELECT -> {
        val day = action.args[DAY].toDateOrNull() ?: return WidgetEffect.Refresh()
        val month = YearMonth.of(day)
        WidgetEffect.Refresh(state = mapOf(MONTH to "${month.year}-${month.month}", DAY to day.toString()))
      }
      TODAY -> WidgetEffect.Refresh(state = emptyMap())
      else -> WidgetEffect.Refresh()
    }
  }

  private companion object {
    const val MONTH = "month"
    const val DAY = "day"
    const val BY = "by"
    const val SHIFT = "shift"
    const val SELECT = "select"
    const val TODAY = "today"
  }
}

internal class AgendaWidget(private val calendar: CalendarData) : WidgetProvider {
  override val id = "agenda"
  override val title: Tr = PlannerText.widgetAgendaTitle
  override val description: Tr = PlannerText.widgetAgendaDescription
  override val size = WidgetSize.Medium

  override suspend fun content(request: WidgetRequest): WidgetContent {
    val now = today()
    val events = calendar.events.all().filter { !it.value.deleted }
    val blocks = (0 until DAYS).map { now.plusDays(it) }.flatMap { day ->
      val list = eventsOn(events, day)
      if (list.isEmpty()) emptyList()
      else listOf(WidgetBlock.Text(dayTitle(day, now), WidgetTone.Accent), WidgetBlock.Rows(list.map { eventRow(it, day) }))
    }
    return WidgetContent(
      title = title(),
      subtitle = now.dayMonth(),
      icon = WidgetIcon.Calendar,
      open = WidgetAction.Open(page = PlannerPlugin.PAGE_CALENDAR),
      buttons = listOf(WidgetButton(PlannerText.widgetNewEvent(), addAction(now), WidgetIcon.Add)),
      blocks = blocks,
      empty = PlannerText.widgetNoUpcoming()
    )
  }

  private companion object {
    const val DAYS = 14
  }
}
