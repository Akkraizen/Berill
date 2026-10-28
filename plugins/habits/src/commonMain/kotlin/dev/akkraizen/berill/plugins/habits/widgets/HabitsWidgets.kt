package dev.akkraizen.berill.plugins.habits.widgets

import dev.akkraizen.berill.api.Item
import dev.akkraizen.berill.api.i18n.Tr
import dev.akkraizen.berill.api.ui.DAYS_SHORT
import dev.akkraizen.berill.api.ui.dayMonth
import dev.akkraizen.berill.api.ui.today
import dev.akkraizen.berill.api.ui.weekdayIndex
import dev.akkraizen.berill.api.widgets.WidgetAction
import dev.akkraizen.berill.api.widgets.WidgetBlock
import dev.akkraizen.berill.api.widgets.WidgetCheck
import dev.akkraizen.berill.api.widgets.WidgetContent
import dev.akkraizen.berill.api.widgets.WidgetEffect
import dev.akkraizen.berill.api.widgets.WidgetGridRow
import dev.akkraizen.berill.api.widgets.WidgetIcon
import dev.akkraizen.berill.api.widgets.WidgetProvider
import dev.akkraizen.berill.api.widgets.WidgetRequest
import dev.akkraizen.berill.api.widgets.WidgetRow
import dev.akkraizen.berill.api.widgets.WidgetSize
import dev.akkraizen.berill.api.widgets.WidgetTone
import dev.akkraizen.berill.plugins.habits.HabitsPlugin
import dev.akkraizen.berill.plugins.habits.data.HabitDef
import dev.akkraizen.berill.plugins.habits.data.HabitsData
import dev.akkraizen.berill.plugins.habits.data.active
import dev.akkraizen.berill.plugins.habits.data.dayValue
import dev.akkraizen.berill.plugins.habits.data.formatAmount
import dev.akkraizen.berill.plugins.habits.i18n.HabitsText
import dev.akkraizen.berill.plugins.habits.ui.HabitStats

internal fun habitsWidgets(data: HabitsData): List<WidgetProvider> = listOf(HabitsTodayWidget(data), HabitsWeekWidget(data))

internal fun HabitsData.stats(): HabitStats =
  HabitStats(
    habits = defs.all().active(),
    values = days.all().associate { r -> r.id to r.fields.keys.associateWith { k -> dayValue(r.fields, k) } },
    today = today()
  )

private fun Item<HabitDef>.label(): String = listOfNotNull(value.emoji?.takeIf { it.isNotBlank() }, value.label.ifBlank { id }).joinToString(" ")

private class HabitsTodayWidget(private val data: HabitsData) : WidgetProvider {
  override val id = "today"
  override val title: Tr = HabitsText.widgetTodayTitle
  override val description: Tr = HabitsText.widgetTodayDescription
  override val size = WidgetSize.Medium

  override suspend fun content(request: WidgetRequest): WidgetContent {
    val stats = data.stats()
    val day = stats.today
    val habits = stats.habits
    val done = stats.doneCount(day)
    return WidgetContent(
      title = HabitsText.title(),
      subtitle = if (habits.isEmpty()) day.dayMonth() else HabitsText.shortDoneOf(done, habits.size)(),
      icon = WidgetIcon.Habit,
      open = WidgetAction.Open(),
      blocks = if (habits.isEmpty())
        emptyList()
      else
        listOf(
          WidgetBlock.Checks(
            habits.map { habit ->
              val value = stats.value(day, habit.id)
              val norm = habit.value.defaultDuration
              WidgetCheck(
                label = habit.label(),
                checked = value >= norm,
                action = WidgetAction.Run(TOGGLE, mapOf(HABIT to habit.id)),
                subtitle = when {
                  value in 1 until norm -> HabitsText.cardProgress(formatAmount(habit.value.unit, value), formatAmount(habit.value.unit, norm))()
                  norm > 1 -> HabitsText.cardNorm(formatAmount(habit.value.unit, norm))()
                  else -> null
                }
              )
            }
          )
        ),
      empty = HabitsText.noHabits()
    )
  }

  override suspend fun perform(request: WidgetRequest, action: WidgetAction.Run): WidgetEffect {
    if (action.id != TOGGLE) return WidgetEffect.Refresh()
    val habitId = action.args[HABIT] ?: return WidgetEffect.Refresh()
    val def = data.defs.get(habitId) ?: return WidgetEffect.Refresh()
    val date = today()
    val current = dayValue(data.days.get(date.toString())?.fields, habitId)
    data.toggle(habitId, def, date, current)
    return WidgetEffect.Refresh()
  }

  override suspend fun summary(request: WidgetRequest): WidgetRow? {
    val stats = data.stats()
    if (stats.habits.isEmpty()) return null
    val done = stats.doneCount(stats.today)
    return WidgetRow(
      title = HabitsText.title(),
      subtitle = stats.habits.filterNot { stats.isDone(stats.today, it) }.take(3).joinToString(", ") { it.label() }.ifBlank { null },
      trailing = HabitsText.shortDoneOf(done, stats.habits.size)(),
      action = WidgetAction.Open(pluginId = HabitsPlugin.id),
      tone = if (done == stats.habits.size) WidgetTone.Good else WidgetTone.Normal,
      icon = WidgetIcon.Habit
    )
  }

  companion object {
    const val TOGGLE = "toggle"
    const val HABIT = "habit"
  }
}

private class HabitsWeekWidget(private val data: HabitsData) : WidgetProvider {
  override val id = "week"
  override val title: Tr = HabitsText.widgetWeekTitle
  override val description: Tr = HabitsText.widgetWeekDescription
  override val size = WidgetSize.Medium

  override suspend fun content(request: WidgetRequest): WidgetContent {
    val stats = data.stats()
    val days = stats.lastDays(DAYS)
    val perfect = stats.currentStreak(stats::isPerfect)
    return WidgetContent(
      title = title(),
      subtitle = HabitsText.widgetPerfectStreak(perfect)(),
      icon = WidgetIcon.Habit,
      open = WidgetAction.Open(route = HabitsPlugin.STATS),
      blocks = if (stats.habits.isEmpty())
        emptyList()
      else
        listOf(
          WidgetBlock.Grid(
            header = days.map { DAYS_SHORT[it.weekdayIndex] },
            rows = stats.habits.map { habit ->
              val norm = habit.value.defaultDuration.coerceAtLeast(1)
              WidgetGridRow(
                label = habit.label(),
                cells = days.map { day -> (stats.value(day, habit.id).toFloat() / norm).coerceIn(0f, 1f) },
                trailing = HabitsText.widgetStreak(stats.currentStreak { stats.isDone(it, habit) })()
              )
            }
          )
        ),
      empty = HabitsText.noHabits()
    )
  }

  private companion object {
    const val DAYS = 7
  }
}
