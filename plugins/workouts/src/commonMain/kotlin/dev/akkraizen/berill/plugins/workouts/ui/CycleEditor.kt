package dev.akkraizen.berill.plugins.workouts.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.ViewWeek
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.akkraizen.berill.api.PluginContext
import dev.akkraizen.berill.api.ui.cards.SectionCard
import dev.akkraizen.berill.api.ui.dialogs.DeleteButton
import dev.akkraizen.berill.api.ui.feedback.EmptyHint
import dev.akkraizen.berill.api.ui.fields.CommitTextField
import dev.akkraizen.berill.api.ui.fields.DateField
import dev.akkraizen.berill.api.ui.fields.DoubleField
import dev.akkraizen.berill.api.ui.fields.IntField
import dev.akkraizen.berill.api.ui.interaction.BerillTextField
import dev.akkraizen.berill.api.ui.interaction.DialogKeys
import dev.akkraizen.berill.api.ui.interaction.interactive
import dev.akkraizen.berill.api.ui.layout.DetailHeader
import dev.akkraizen.berill.api.ui.layout.PluginList
import dev.akkraizen.berill.api.ui.parseAmount
import dev.akkraizen.berill.api.ui.selection.ChoiceChips
import dev.akkraizen.berill.plugins.workouts.data.Cycle
import dev.akkraizen.berill.plugins.workouts.data.ExerciseOverride
import dev.akkraizen.berill.plugins.workouts.data.PROGRESSION_KG
import dev.akkraizen.berill.plugins.workouts.data.PROGRESSION_NONE
import dev.akkraizen.berill.plugins.workouts.data.PROGRESSION_PERCENT
import dev.akkraizen.berill.plugins.workouts.data.PlannedExercise
import dev.akkraizen.berill.plugins.workouts.data.applyProgram
import dev.akkraizen.berill.plugins.workouts.data.currentWeek
import dev.akkraizen.berill.plugins.workouts.data.cycles
import dev.akkraizen.berill.plugins.workouts.data.daysForWeek
import dev.akkraizen.berill.plugins.workouts.data.formatDuration
import dev.akkraizen.berill.plugins.workouts.data.formatKg
import dev.akkraizen.berill.plugins.workouts.data.logFromPlan
import dev.akkraizen.berill.plugins.workouts.data.overrideKey
import dev.akkraizen.berill.plugins.workouts.data.parseDuration
import dev.akkraizen.berill.plugins.workouts.data.planned
import dev.akkraizen.berill.plugins.workouts.data.startWorkout
import dev.akkraizen.berill.plugins.workouts.i18n.WorkoutsText

@Composable
internal fun CycleEditor(ctx: PluginContext, id: String) {
  val collection = remember(ctx) { cycles(ctx) }
  val cycle by
    remember(collection, id) { collection.observe(id) }.collectAsState(collection.get(id))
  val prefs = rememberWorkoutPrefs(ctx)
  val c = cycle
  if (c == null) {
    PluginList { item { EmptyHint(WorkoutsText.noCycles()) } }
    return
  }
  fun update(change: (Cycle) -> Cycle) {
    val fresh = collection.get(id) ?: return
    collection.save(id, change(fresh))
  }

  val now = c.weekNow()
  var selected by rememberSaveable(id) { mutableIntStateOf(now ?: 1) }
  val week = selected.coerceIn(1, c.weeks.coerceAtLeast(1))
  var overriding by remember(id, week) { mutableStateOf<Triple<Int, Int, PlannedExercise>?>(null) }

  var choosingProgram by remember(id) { mutableStateOf(false) }

  PluginList {
    item {
      DetailHeader(c.title.ifBlank { WorkoutsText.pageCycle() }, Icons.Outlined.CalendarMonth)
    }

    item {
      SectionCard(WorkoutsText.weekPlan(), Icons.Outlined.ViewWeek) {
        ChoiceChips(
          (1..c.weeks.coerceAtLeast(1)).toList(),
          week,
          { n ->
            val marks = listOfNotNull(
              WorkoutsText.currentWeek().takeIf { n == now },
              WorkoutsText.deloadWeek().takeIf {
                c.deloadLast && c.weeks > 1 && n == c.weeks
              }
            )
            WorkoutsText.week(n)() + if (marks.isEmpty()) "" else " · " + marks.joinToString(", ")
          },
          { selected = it }
        )
        FilledTonalButton(onClick = { choosingProgram = true }) { Text(WorkoutsText.assignProgram()) }
        c.weekPrograms[week]?.let { plan ->
          Text(plan.title.ifBlank { WorkoutsText.pageProgram() }, style = MaterialTheme.typography.titleSmall)
          TextButton(onClick = {
            update { fresh -> fresh.copy(
              weekPrograms = fresh.weekPrograms - week,
              overrides = fresh.overrides.filterKeys { it.substringBefore(':') != week.toString() }
            ) }
          }) { Text(WorkoutsText.resetWeekProgram()) }
        }
        if (c.daysForWeek(week).isEmpty()) EmptyHint(WorkoutsText.programSummaryEmpty())
        c.daysForWeek(week).forEachIndexed { d, day ->
          val planned = day.exercises.indices.map { e -> c.planned(week, d, e, prefs.maxes, prefs.rounding) }
          Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth()
          ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
              DayStartRow(day.title, emptyList()) {
                startWorkout(
                  ctx,
                  WorkoutsText.fromCycle(c.title, week, day.title)(),
                  "cycle:$id:$week:$d",
                  logFromPlan(planned)
                )
              }
              planned.forEachIndexed { e, p -> PlannedRow(p) { overriding = Triple(d, e, p) } }
            }
          }
        }
      }
    }

    item {
      SectionCard(WorkoutsText.progression(), Icons.Outlined.Tune) {
        CommitTextField(c.title, { v -> update { it.copy(title = v) } }, WorkoutsText.name())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          IntField(
            c.weeks,
            { v -> update { it.copy(weeks = (v ?: 1).coerceIn(1, 52)) } },
            WorkoutsText.weeksCount(),
            Modifier.weight(1f)
          )
          DateField(c.start, { v -> update { it.copy(start = v) } }, WorkoutsText.startDate(), Modifier.weight(2f))
        }
        ChoiceChips(
          listOf(PROGRESSION_KG, PROGRESSION_PERCENT, PROGRESSION_NONE),
          c.progression.kind,
          { kind ->
            when (kind) {
              PROGRESSION_KG -> WorkoutsText.progressionKg()
              PROGRESSION_PERCENT -> WorkoutsText.progressionPercent()
              else -> WorkoutsText.progressionNone()
            }
          },
          { kind -> update { it.copy(progression = it.progression.copy(kind = kind)) } }
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          if (c.progression.kind != PROGRESSION_NONE)
            DoubleField(
              c.progression.step,
              { v -> update { it.copy(progression = it.progression.copy(step = v ?: 0.0)) } },
              if (c.progression.kind == PROGRESSION_PERCENT)
                WorkoutsText.stepPercent()
              else
                WorkoutsText.stepKg(),
              Modifier.weight(1f)
            )
          IntField(
            c.progression.repsStep,
            { v -> update { it.copy(progression = it.progression.copy(repsStep = v ?: 0)) } },
            WorkoutsText.repsStep(),
            Modifier.weight(1f)
          )
        }
        Row(
          Modifier.fillMaxWidth().interactive(bleed = 6.dp) {
            update { it.copy(deloadLast = !it.deloadLast) }
          },
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column(Modifier.weight(1f)) {
            Text(WorkoutsText.deloadLast())
            Text(
              WorkoutsText.deloadHint(),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
          Switch(checked = c.deloadLast, onCheckedChange = { on -> update { it.copy(deloadLast = on) } })
        }
      }
    }

    item {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(WorkoutsText.template(), style = MaterialTheme.typography.titleMedium)
        Text(
          WorkoutsText.templateHint(),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        DaysEditor(ctx, c.days, percentMode = true) { edit ->
          update { it.copy(days = edit(it.days)) }
        }
      }
    }
    item {
      DeleteButton(WorkoutsText.cycleWord()) {
        collection.delete(id)
        ctx.nav.back()
      }
    }
  }

  if (choosingProgram) {
    AssignProgramDialog(ctx, c.weeks, week, onDismiss = { choosingProgram = false }) { programId, program, weeks ->
      update { fresh ->
        val validWeeks = weeks.filter { it in 1..fresh.weeks }.toSet()
        if (validWeeks.isEmpty()) fresh else fresh.applyProgram(program, validWeeks, programId)
      }
      choosingProgram = false
    }
  }

  overriding?.let { (d, e, planned) ->
    OverrideDialog(
      planned = planned,
      week = week,
      current = c.overrides[overrideKey(week, d, e)],
      onDismiss = { overriding = null },
      onSave = { o ->
        val key = overrideKey(week, d, e)
        update {
          it.copy(overrides = if (o == null) it.overrides - key else it.overrides + (key to o))
        }
        overriding = null
      }
    )
  }
}

@Composable
private fun PlannedRow(p: PlannedExercise, onClick: () -> Unit) {
  val colors = MaterialTheme.colorScheme
  Row(
    Modifier.fillMaxWidth().interactive(bleed = 6.dp, onClick = onClick),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(10.dp)
  ) {
    Column(Modifier.weight(1f)) {
      Text(p.name.ifBlank { WorkoutsText.exercise() })
      val marks = listOfNotNull(
        p.percent?.let { "${formatKg(it)}%" },
        WorkoutsText.overridden().takeIf { p.overridden },
        WorkoutsText.deloadWeek().takeIf { p.deload }
      )
      if (marks.isNotEmpty())
        Text(marks.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
    }
    Text((p.durationSeconds?.let { WorkoutsText.timingSummary(p.sets, it, p.durationPerSet) } ?: WorkoutsText.setsReps(p.sets, p.reps)()), style = MaterialTheme.typography.titleSmall)
    Text(
      when {
        p.weight != null -> WorkoutsText.kgValue(formatKg(p.weight))()
        p.missingMax -> WorkoutsText.noMax()
        else -> WorkoutsText.noWeight()
      },
      style = MaterialTheme.typography.titleSmall,
      fontWeight = FontWeight.SemiBold,
      color = if (p.missingMax) colors.error else colors.primary
    )
  }
}

@Composable
private fun OverrideDialog(
  planned: PlannedExercise,
  week: Int,
  current: ExerciseOverride?,
  onDismiss: () -> Unit,
  onSave: (ExerciseOverride?) -> Unit
) {
  var duration by remember { mutableStateOf(current?.durationSeconds?.let(::formatDuration).orEmpty()) }
  var sets by remember { mutableStateOf(current?.sets?.toString().orEmpty()) }
  var reps by remember { mutableStateOf(current?.reps.orEmpty()) }
  var byPercent by remember { mutableStateOf(current?.percent != null || (current?.weight == null && planned.percent != null)) }
  var weight by remember { mutableStateOf((current?.percent ?: current?.weight)?.let(::formatKg).orEmpty()) }
  val amount = parseAmount(weight)
  val validWeight = weight.isBlank() || (amount != null && if (byPercent) amount in 1.0..120.0 else amount >= 0.0)
  val validDuration = duration.isBlank() || (parseDuration(duration) ?: 0) > 0
  val confirm: () -> Unit = confirm@{
    if (if (planned.durationSeconds != null) !validDuration else !validWeight) return@confirm
    val o = if (planned.durationSeconds != null) ExerciseOverride(
      durationSeconds = parseDuration(duration)?.takeIf { it > 0 },
      sets = if (planned.durationPerSet) sets.toIntOrNull()?.coerceIn(1, 50) else null
    ) else ExerciseOverride(
      sets = sets.toIntOrNull()?.coerceIn(1, 50),
      reps = reps.trim().ifEmpty { null },
      weight = amount.takeUnless { byPercent },
      percent = amount.takeIf { byPercent }
    )
    onSave(o.takeIf { it.sets != null || it.reps != null || it.weight != null || it.percent != null || it.durationSeconds != null })
  }
  val valid = if (planned.durationSeconds != null) validDuration else validWeight
  DialogKeys(onConfirm = confirm, confirmEnabled = valid, onDismiss = onDismiss)
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(WorkoutsText.overrideTitle(planned.name, week)()) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
          WorkoutsText.overrideHint(),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (planned.durationSeconds != null) {
          BerillTextField(duration, { duration = it.take(8) },
            label = { Text(WorkoutsText.durationInput(), maxLines = 1, overflow = TextOverflow.Ellipsis) }, singleLine = true, isError = !validDuration,
            placeholder = { Text(formatDuration(planned.durationSeconds), maxLines = 1, overflow = TextOverflow.Ellipsis) })
          if (planned.durationPerSet) {
            BerillTextField(sets, { sets = it.filter(Char::isDigit).take(2) },
              label = { Text(WorkoutsText.sets(), maxLines = 1, overflow = TextOverflow.Ellipsis) }, singleLine = true,
              placeholder = { Text("${planned.sets}", maxLines = 1, overflow = TextOverflow.Ellipsis) })
          }
        } else {
          BerillTextField(
            sets,
            { sets = it.filter(Char::isDigit).take(2) },
            label = { Text(WorkoutsText.sets(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
            singleLine = true,
            placeholder = { Text("${planned.sets}", maxLines = 1, overflow = TextOverflow.Ellipsis) }
          )
          BerillTextField(
            reps,
            { reps = it.take(12) },
            label = { Text(WorkoutsText.reps(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
            singleLine = true,
            placeholder = { Text(planned.reps, maxLines = 1, overflow = TextOverflow.Ellipsis) }
          )
          ChoiceChips(
            listOf(false, true), byPercent,
            { if (it) WorkoutsText.byPercent() else WorkoutsText.byWeight() },
            { byPercent = it; weight = "" }
          )
          BerillTextField(
            weight,
            { weight = it.take(8) },
            label = { Text(if (byPercent) WorkoutsText.percentOfMax() else WorkoutsText.weightKg(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
            singleLine = true,
            isError = !validWeight,
            placeholder = if ((if (byPercent) planned.percent else planned.weight) != null) {
              { Text(formatKg((if (byPercent) planned.percent else planned.weight)!!)) }
            } else
              null
          )
        }
      }
    },
    confirmButton = { TextButton(enabled = valid, onClick = confirm) { Text(WorkoutsText.save()) } },
    dismissButton = {
      Row {
        if (current != null)
          TextButton(onClick = { onSave(null) }) { Text(WorkoutsText.resetOverride()) }
        TextButton(onClick = onDismiss) { Text(WorkoutsText.cancel()) }
      }
    }
  )
}
