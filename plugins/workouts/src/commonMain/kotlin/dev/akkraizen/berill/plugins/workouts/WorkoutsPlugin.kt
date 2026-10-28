package dev.akkraizen.berill.plugins.workouts

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.EventNote
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.ListAlt
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.runtime.Composable
import dev.akkraizen.berill.api.BerillPlugin
import dev.akkraizen.berill.api.HomeCard
import dev.akkraizen.berill.api.PluginContext
import dev.akkraizen.berill.api.PluginPage
import dev.akkraizen.berill.api.PluginPermissions
import dev.akkraizen.berill.api.QuickAction
import dev.akkraizen.berill.plugins.workouts.data.EXERCISE_DB_HOST
import dev.akkraizen.berill.plugins.workouts.data.startWorkout
import dev.akkraizen.berill.plugins.workouts.i18n.CatalogText
import dev.akkraizen.berill.plugins.workouts.i18n.WorkoutsText
import dev.akkraizen.berill.plugins.workouts.ui.CalculatorPage
import dev.akkraizen.berill.plugins.workouts.ui.CatalogPage
import dev.akkraizen.berill.plugins.workouts.ui.CyclePage
import dev.akkraizen.berill.plugins.workouts.ui.JournalPage
import dev.akkraizen.berill.plugins.workouts.ui.ProgramPage
import dev.akkraizen.berill.plugins.workouts.ui.WorkoutsHomeCard
import dev.akkraizen.berill.plugins.workouts.ui.WorkoutsSettings
import dev.akkraizen.berill.plugins.workouts.widgets.workoutsWidgets

object WorkoutsPlugin : BerillPlugin {
  const val PROGRAMS = "workout_programs"
  const val CYCLES = "workout_cycles"
  const val LOGS = "workout_logs"
  const val EXERCISES = "workout_exercises"

  const val PAGE_PROGRAM = "program"
  const val PAGE_CYCLE = "cycle"
  const val PAGE_JOURNAL = "journal"
  const val PAGE_CALCULATOR = "calculator"
  const val PAGE_CATALOG = "catalog"

  internal const val PROGRAM_ROUTE = "program:"
  internal const val CYCLE_ROUTE = "cycle:"
  internal const val LOG_ROUTE = "log:"
  internal const val EXERCISE_ROUTE = "exercise:"

  override val id = "workouts"
  override val title: String
    get() = WorkoutsText.title()

  override val icon = Icons.Outlined.FitnessCenter
  override val order = 12
  override val description: String
    get() = WorkoutsText.description()

  override val installedByDefault = true
  override val collections = listOf(PROGRAMS, CYCLES, LOGS, EXERCISES)

  override val permissions = PluginPermissions(network = listOf(EXERCISE_DB_HOST))
  override val hasSettings = true

  @Composable
  override fun Settings(ctx: PluginContext) = WorkoutsSettings(ctx)

  override val pages = listOf(
    PluginPage(PAGE_PROGRAM, WorkoutsText.pageProgram, Icons.Outlined.ListAlt) { ctx ->
      ProgramPage(ctx)
    },
    PluginPage(PAGE_CYCLE, WorkoutsText.pageCycle, Icons.Outlined.CalendarMonth) { ctx ->
      CyclePage(ctx)
    },
    PluginPage(PAGE_JOURNAL, WorkoutsText.pageJournal, Icons.Outlined.EventNote) { ctx ->
      JournalPage(ctx)
    },
    PluginPage(PAGE_CALCULATOR, WorkoutsText.pageCalculator, Icons.Outlined.Calculate) { ctx
      ->
      CalculatorPage(ctx)
    },
    PluginPage(PAGE_CATALOG, CatalogText.page, Icons.Outlined.MenuBook) { ctx ->
      CatalogPage(ctx)
    }
  )

  override fun homeCards(ctx: PluginContext) =
    listOf(HomeCard("week", WorkoutsText.homeCard, order = 25) { WorkoutsHomeCard(ctx) })

  override fun quickActions(ctx: PluginContext) =
    listOf(
      QuickAction("workout", WorkoutsText.quickWorkout, Icons.Outlined.FitnessCenter) {
        startWorkout(ctx, WorkoutsText.workoutTitle(), null, emptyList())
      }
    )

  override fun widgets(ctx: PluginContext) = workoutsWidgets(ctx)
}
