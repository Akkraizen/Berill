package dev.akkraizen.berill.plugins.workouts.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.ListAlt
import androidx.compose.material.icons.outlined.PlayArrow
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.akkraizen.berill.api.Item
import dev.akkraizen.berill.api.PluginContext
import dev.akkraizen.berill.api.ui.BerillIcons
import dev.akkraizen.berill.api.ui.animation.AnimatedRoute
import dev.akkraizen.berill.api.ui.cards.SectionCard
import dev.akkraizen.berill.api.ui.display.Ic
import dev.akkraizen.berill.api.ui.feedback.EmptyHint
import dev.akkraizen.berill.api.ui.layout.DetailHeader
import dev.akkraizen.berill.api.ui.layout.PluginList
import dev.akkraizen.berill.api.ui.nowMillis
import dev.akkraizen.berill.plugins.workouts.WorkoutsPlugin
import dev.akkraizen.berill.plugins.workouts.data.DEFAULT_ROUNDING
import dev.akkraizen.berill.plugins.workouts.data.Exercise
import dev.akkraizen.berill.plugins.workouts.data.Program
import dev.akkraizen.berill.plugins.workouts.data.WorkoutDay
import dev.akkraizen.berill.plugins.workouts.data.formatKg
import dev.akkraizen.berill.plugins.workouts.data.logFromDay
import dev.akkraizen.berill.plugins.workouts.data.programs
import dev.akkraizen.berill.plugins.workouts.data.resolvedWeight
import dev.akkraizen.berill.plugins.workouts.data.startWorkout
import dev.akkraizen.berill.plugins.workouts.i18n.WorkoutsText

@Composable
internal fun ProgramPage(ctx: PluginContext) {
  val route by ctx.nav.route.collectAsState()
  AnimatedRoute(route) { r ->
    if (r != null && r.startsWith(WorkoutsPlugin.PROGRAM_ROUTE))
      ProgramEditor(ctx, r.removePrefix(WorkoutsPlugin.PROGRAM_ROUTE))
    else
      ProgramList(ctx)
  }
}

@Composable
private fun ProgramList(ctx: PluginContext) {
  val collection = remember(ctx) { programs(ctx) }
  val all by remember(collection) { collection.observe() }.collectAsState(emptyList())
  var creating by remember { mutableStateOf(false) }
  val sorted = all.sortedBy { it.value.createdAt }

  PluginList {
    item {
      DetailHeader(WorkoutsText.programs(), Icons.Outlined.ListAlt) {
        FilledTonalButton(onClick = { creating = true }) {
          Ic(BerillIcons.Add, size = 18.dp)
          Spacer(Modifier.width(8.dp))
          Text(WorkoutsText.newProgram())
        }
      }
    }
    if (sorted.isEmpty()) item { SectionCard { EmptyHint(WorkoutsText.noPrograms()) } }
    items(sorted, key = { it.id }) { item -> ProgramCard(ctx, item) }
  }

  if (creating)
    NameDialog(
      title = WorkoutsText.newProgram(),
      onDismiss = { creating = false },
      onCreate = { name ->
        val id = ctx.data.newId()
        collection.save(
          id,
          Program(name, days = listOf(WorkoutDay(WorkoutsText.dayTitle(1)())), createdAt = nowMillis())
        )
        creating = false
        ctx.nav.open(WorkoutsPlugin.PROGRAM_ROUTE + id)
      }
    )
}

@Composable
private fun ProgramCard(ctx: PluginContext, item: Item<Program>) {
  val prefs = rememberWorkoutPrefs(ctx)
  val p = item.value
  val open = { ctx.nav.open(WorkoutsPlugin.PROGRAM_ROUTE + item.id) }
  SectionCard(
    p.title.ifBlank { WorkoutsText.pageProgram() },
    Icons.Outlined.FitnessCenter,
    onTitleClick = open,
    trailing = { TextButton(onClick = open) { Text(WorkoutsText.edit()) } }
  ) {
    if (p.days.isEmpty()) EmptyHint(WorkoutsText.programSummaryEmpty())
    p.days.forEachIndexed { d, day ->
      DayStartRow(day.title, day.exercises.map { exerciseSummary(it, prefs.maxes, prefs.rounding) }) {
        startWorkout(ctx, WorkoutsText.fromProgram(p.title, day.title)(), "program:${item.id}:$d", logFromDay(day, prefs.maxes, prefs.rounding))
      }
    }
  }
}

internal fun exerciseSummary(e: Exercise, maxes: Map<String, Double> = emptyMap(), rounding: Double = DEFAULT_ROUNDING): String {
  val weight = if (e.durationSeconds != null) null else e.resolvedWeight(maxes, rounding)?.let { WorkoutsText.kgValue(formatKg(it))() }
    ?: e.percent?.let { "${formatKg(it)}% · ${WorkoutsText.noMax()}" }
  return listOfNotNull(
    e.name.ifBlank { WorkoutsText.exercise() } +
      " " +
      (e.durationSeconds?.let { WorkoutsText.timingSummary(e.sets, it, e.durationPerSet) } ?: WorkoutsText.setsReps(e.sets, e.reps)()),
    weight
  )
    .joinToString(" · ")
}

@Composable
internal fun DayStartRow(title: String, exercises: List<String>, onStart: () -> Unit) {
  Column(
    Modifier.fillMaxWidth().padding(vertical = 8.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp)
  ) {
    Row(
      Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
      FilledTonalButton(onClick = onStart) {
        Ic(Icons.Outlined.PlayArrow, size = 18.dp)
        Spacer(Modifier.width(6.dp))
        Text(WorkoutsText.start())
      }
    }
    exercises.forEachIndexed { index, exercise ->
      Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top
      ) {
        Text(
          "${index + 1}.",
          modifier = Modifier.width(28.dp),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
          exercise,
          modifier = Modifier.weight(1f),
          style = MaterialTheme.typography.bodyMedium
        )
      }
    }
  }
}
