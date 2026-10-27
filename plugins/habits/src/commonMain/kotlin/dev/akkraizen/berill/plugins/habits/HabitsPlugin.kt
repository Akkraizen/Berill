package dev.akkraizen.berill.plugins.habits

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import dev.akkraizen.berill.api.BerillPlugin
import dev.akkraizen.berill.api.HomeCard
import dev.akkraizen.berill.api.PluginContext
import dev.akkraizen.berill.api.ui.animation.AnimatedRoute
import dev.akkraizen.berill.plugins.habits.data.HabitsData
import dev.akkraizen.berill.plugins.habits.i18n.HabitsText
import dev.akkraizen.berill.plugins.habits.ui.HabitsEditScreen
import dev.akkraizen.berill.plugins.habits.ui.HabitsScreen
import dev.akkraizen.berill.plugins.habits.ui.HabitsSettings
import dev.akkraizen.berill.plugins.habits.ui.HabitsStatsScreen
import dev.akkraizen.berill.plugins.habits.ui.HabitsTodayCard

object HabitsPlugin : BerillPlugin {
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
        STATS -> HabitsStatsScreen(data)
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
}
