package dev.akkraizen.berill.plugins.workouts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.EventNote
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.ListAlt
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import dev.akkraizen.berill.api.BerillPlugin
import dev.akkraizen.berill.api.HomeCard
import dev.akkraizen.berill.api.PluginContext
import dev.akkraizen.berill.api.PluginPage
import dev.akkraizen.berill.api.PluginPermissions
import dev.akkraizen.berill.api.QuickAction
import dev.akkraizen.berill.api.ui.cards.Stat
import dev.akkraizen.berill.api.ui.cards.StatTiles
import dev.akkraizen.berill.api.ui.dayMonth
import dev.akkraizen.berill.api.ui.mondayOfWeek
import dev.akkraizen.berill.api.ui.nowMillis
import dev.akkraizen.berill.api.ui.toDateOrNull
import dev.akkraizen.berill.api.ui.today

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
}

internal fun programs(ctx: PluginContext) = ctx.data.collection(WorkoutsPlugin.PROGRAMS, Program.serializer())

internal fun cycles(ctx: PluginContext) = linkedCycles(ctx)

internal fun logs(ctx: PluginContext) = ctx.data.collection(WorkoutsPlugin.LOGS, WorkoutLog.serializer())

internal fun startWorkout(ctx: PluginContext, title: String, source: String?, exercises: List<LoggedExercise>) {
  val id = ctx.data.newId()
  logs(ctx)
    .save(id, WorkoutLog(today().toString(), title, source, exercises, createdAt = nowMillis()))
  ctx.nav.openPlugin(WorkoutsPlugin.id, WorkoutsPlugin.LOG_ROUTE + id, WorkoutsPlugin.PAGE_JOURNAL)
}

@Composable
private fun WorkoutsHomeCard(ctx: PluginContext) {
  val collection = remember(ctx) { logs(ctx) }
  val all by remember(collection) { collection.observe() }.collectAsState(emptyList())
  val monday = today().mondayOfWeek()
  val thisWeek = all.count { log -> log.value.date.toDateOrNull()?.let { it >= monday } == true }
  val last = all.maxByOrNull { it.value.date + it.value.createdAt.toString().padStart(15, '0') }?.value
  StatTiles(
    listOf(
      Stat(WorkoutsText.thisWeek(), "$thisWeek", icon = Icons.Outlined.FitnessCenter),
      Stat(
        WorkoutsText.lastWorkout(),
        last?.date?.toDateOrNull()?.dayMonth() ?: WorkoutsText.never(),
        sub = last?.title,
        icon = Icons.Outlined.EventNote
      )
    ),
    minTileWidth = 140.dp
  )
  if (all.isEmpty())
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      Text(
        WorkoutsText.noLogs(),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
}
