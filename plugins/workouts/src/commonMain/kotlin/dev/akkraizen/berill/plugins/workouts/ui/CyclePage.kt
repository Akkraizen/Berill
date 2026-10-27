package dev.akkraizen.berill.plugins.workouts.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.akkraizen.berill.api.Item
import dev.akkraizen.berill.api.PluginContext
import dev.akkraizen.berill.api.ui.BerillIcons
import dev.akkraizen.berill.api.ui.animation.AnimatedRoute
import dev.akkraizen.berill.api.ui.cards.SectionCard
import dev.akkraizen.berill.api.ui.daysFromToday
import dev.akkraizen.berill.api.ui.display.Ic
import dev.akkraizen.berill.api.ui.feedback.EmptyHint
import dev.akkraizen.berill.api.ui.interaction.BerillTextField
import dev.akkraizen.berill.api.ui.interaction.DialogKeys
import dev.akkraizen.berill.api.ui.layout.DetailHeader
import dev.akkraizen.berill.api.ui.layout.PluginList
import dev.akkraizen.berill.api.ui.mondayOfWeek
import dev.akkraizen.berill.api.ui.nowMillis
import dev.akkraizen.berill.api.ui.toDateOrNull
import dev.akkraizen.berill.api.ui.today
import dev.akkraizen.berill.plugins.workouts.WorkoutsPlugin
import dev.akkraizen.berill.plugins.workouts.data.Cycle
import dev.akkraizen.berill.plugins.workouts.data.PlannedExercise
import dev.akkraizen.berill.plugins.workouts.data.WorkoutDay
import dev.akkraizen.berill.plugins.workouts.data.currentWeek
import dev.akkraizen.berill.plugins.workouts.data.cycles
import dev.akkraizen.berill.plugins.workouts.data.daysForWeek
import dev.akkraizen.berill.plugins.workouts.data.formatKg
import dev.akkraizen.berill.plugins.workouts.data.logFromPlan
import dev.akkraizen.berill.plugins.workouts.data.planned
import dev.akkraizen.berill.plugins.workouts.data.startWorkout
import dev.akkraizen.berill.plugins.workouts.i18n.WorkoutsText

@Composable
internal fun CyclePage(ctx: PluginContext) {
  val route by ctx.nav.route.collectAsState()
  AnimatedRoute(route) { r ->
    if (r != null && r.startsWith(WorkoutsPlugin.CYCLE_ROUTE))
      CycleEditor(ctx, r.removePrefix(WorkoutsPlugin.CYCLE_ROUTE))
    else
      CycleList(ctx)
  }
}

internal fun Cycle.weekNow(): Int? = currentWeek(start.toDateOrNull()?.let { -it.daysFromToday() })

@Composable
private fun CycleList(ctx: PluginContext) {
  val collection = remember(ctx) { cycles(ctx) }
  val all by remember(collection) { collection.observe() }.collectAsState(emptyList())
  var creating by remember { mutableStateOf(false) }
  val sorted = all.sortedByDescending { it.value.createdAt }

  PluginList {
    item {
      DetailHeader(WorkoutsText.cycles(), Icons.Outlined.CalendarMonth) {
        FilledTonalButton(onClick = { creating = true }) {
          Ic(BerillIcons.Add, size = 18.dp)
          Spacer(Modifier.width(8.dp))
          Text(WorkoutsText.newCycle())
        }
      }
    }
    if (sorted.isEmpty()) item { SectionCard { EmptyHint(WorkoutsText.noCycles()) } }
    items(sorted, key = { it.id }) { item -> CycleCard(ctx, item) }
  }

  if (creating)
    CreateCycleDialog(
      onDismiss = { creating = false },
      onCreate = { name, weeks ->
        val id = ctx.data.newId()
        collection.save(
          id,
          Cycle(
            title = name,
            weeks = weeks,
            start = today().mondayOfWeek().toString(),
            days = listOf(WorkoutDay(WorkoutsText.dayTitle(1)())),
            createdAt = nowMillis()
          )
        )
        creating = false
        ctx.nav.open(WorkoutsPlugin.CYCLE_ROUTE + id)
      }
    )
}

@Composable
private fun CycleCard(ctx: PluginContext, item: Item<Cycle>) {
  val c = item.value
  val prefs = rememberWorkoutPrefs(ctx)
  val now = c.weekNow()
  val open = { ctx.nav.open(WorkoutsPlugin.CYCLE_ROUTE + item.id) }
  SectionCard(
    c.title.ifBlank { WorkoutsText.pageCycle() },
    Icons.Outlined.CalendarMonth,
    onTitleClick = open,
    trailing = { TextButton(onClick = open) { Text(WorkoutsText.edit()) } }
  ) {
    Text(
      listOfNotNull(
        WorkoutsText.weeks(c.weeks),
        WorkoutsText.days(c.daysForWeek(now ?: 1).size),
        now?.let { WorkoutsText.week(it)() + " — " + WorkoutsText.currentWeek() }
      )
        .joinToString(" · "),
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    if (now != null)
      c.daysForWeek(now).forEachIndexed { d, day ->
        val planned = day.exercises.indices.map { e -> c.planned(now, d, e, prefs.maxes, prefs.rounding) }
        DayStartRow(day.title, planned.map { plannedSummary(it) }) {
          startWorkout(
            ctx,
            WorkoutsText.fromCycle(c.title, now, day.title)(),
            "cycle:${item.id}:$now:$d",
            logFromPlan(planned)
          )
        }
      }
  }
}

private fun plannedSummary(p: PlannedExercise): String {
  val weight = when {
    p.weight != null -> WorkoutsText.kgValue(formatKg(p.weight))()
    p.missingMax -> WorkoutsText.noMax()
    else -> null
  }
  return listOfNotNull(
    p.name.ifBlank { WorkoutsText.exercise() } +
      " " +
      (p.durationSeconds?.let { WorkoutsText.timingSummary(p.sets, it, p.durationPerSet) } ?: WorkoutsText.setsReps(p.sets, p.reps)()),
    weight
  )
    .joinToString(" · ")
}

@Composable
private fun CreateCycleDialog(onDismiss: () -> Unit, onCreate: (String, Int) -> Unit) {
  var name by remember { mutableStateOf("") }
  var weeks by remember { mutableStateOf("4") }
  val weeksValue = weeks.toIntOrNull()?.takeIf { it in 1..52 }
  val confirm: () -> Unit = { onCreate(name.trim(), weeksValue ?: 4) }
  DialogKeys(onConfirm = confirm, confirmEnabled = name.isNotBlank() && weeksValue != null, onDismiss = onDismiss)
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(WorkoutsText.newCycle()) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BerillTextField(name, { name = it }, label = { Text(WorkoutsText.name()) }, singleLine = true)
        BerillTextField(
          weeks,
          { weeks = it.filter(Char::isDigit).take(2) },
          label = { Text(WorkoutsText.weeksCount()) },
          singleLine = true,
          isError = weeksValue == null
        )
      }
    },
    confirmButton = {
      TextButton(enabled = name.isNotBlank() && weeksValue != null, onClick = confirm) {
        Text(WorkoutsText.create())
      }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text(WorkoutsText.cancel()) } }
  )
}
