package dev.akkraizen.berill.plugins.habits

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import dev.akkraizen.berill.api.label
import dev.akkraizen.berill.api.BerillPlugin
import dev.akkraizen.berill.api.HomeCard
import dev.akkraizen.berill.api.PluginContext
import dev.akkraizen.berill.api.ui.animation.AnimatedRoute
import dev.akkraizen.berill.plugins.habits.data.HabitsData
import dev.akkraizen.berill.plugins.habits.i18n.HabitsText
import dev.akkraizen.berill.plugins.habits.ui.HabitsEditScreen
import dev.akkraizen.berill.plugins.habits.ui.HabitsScreen
import dev.akkraizen.berill.plugins.habits.ui.HabitsSettings
import dev.akkraizen.berill.plugins.habits.ui.HabitsTodayCard
import dev.akkraizen.berill.plugins.habits.widgets.habitsWidgets

object HabitsPlugin : BerillPlugin {
  override fun provides(ctx: dev.akkraizen.berill.api.PluginContext): List<dev.akkraizen.berill.api.Capability> = baseProvides(ctx) + dev.akkraizen.berill.plugins.habits.mcp.HabitsMcp(ctx)

  const val HABITS = "habits"
  const val HABIT_DAYS = "habit_days"

  override val id = "habits"
  override val title
    get() = HabitsText.title()

  override val icon = Icons.Outlined.TaskAlt
  override val order = 10
  override val description
    get() = HabitsText.description()

  override val installedByDefault = true
  override val hasSettings = true

  @Composable
  override fun Settings(ctx: PluginContext) {
    val data = remember(ctx) { HabitsData(ctx) }
    HabitsSettings(data)
  }

  override val collections = listOf(HABITS, HABIT_DAYS)

  internal const val STATS = "stats"
  internal const val EDIT = "edit"

  @Composable
  override fun Screen(ctx: PluginContext) {
    val data = remember(ctx) { HabitsData(ctx) }
    val route by ctx.nav.route.collectAsState()
    AnimatedRoute(route) { route ->
      when (route) {
        STATS -> HabitsScreen(data, statistics = true)
        EDIT -> HabitsEditScreen(data)
        else -> HabitsScreen(data)
      }
    }
  }

  override fun homeCards(ctx: PluginContext) =
    listOf(
      HomeCard(id = "today", title = HabitsText.todayCardTitle, order = 10) {
        val data = remember(ctx) { HabitsData(ctx) }
        HabitsTodayCard(data)
      }
    )

  private fun baseProvides(ctx: PluginContext): List<dev.akkraizen.berill.api.Capability> = listOf(dev.akkraizen.berill.api.DailyReminderSource(ctx, { title }, listOf(20), { ctx.data.raw(HABITS).all().any { habit -> (ctx.data.raw(HABIT_DAYS).get(dev.akkraizen.berill.api.ui.today().toString())?.fields?.get(habit.id)?.toString()?.toIntOrNull() ?: 0) < (habit.fields["defaultDuration"]?.toString()?.toIntOrNull() ?: 1) } }), dev.akkraizen.berill.api.CollectionDaySource(ctx, HABIT_DAYS, { title }, describe = { record -> record.fields.entries.joinToString { (key, value) -> (ctx.data.raw("habits").get(key)?.let { it.label() } ?: key) + ": " + value } }), dev.akkraizen.berill.api.CollectionSearchSource(ctx, HABITS, { title }))

  override fun widgets(ctx: PluginContext) = habitsWidgets(HabitsData(ctx))
}
