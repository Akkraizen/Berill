package dev.akkraizen.berill.plugins.workouts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.akkraizen.berill.api.PluginContext
import dev.akkraizen.berill.api.ui.BerillIcons
import dev.akkraizen.berill.api.ui.cards.SectionCard
import dev.akkraizen.berill.api.ui.dialogs.DeleteButton
import dev.akkraizen.berill.api.ui.display.Ic
import dev.akkraizen.berill.api.ui.feedback.EmptyHint
import dev.akkraizen.berill.api.ui.feedback.ProgressLine
import dev.akkraizen.berill.api.ui.fields.BodyEditor
import dev.akkraizen.berill.api.ui.fields.CommitTextField
import dev.akkraizen.berill.api.ui.fields.DateField
import dev.akkraizen.berill.api.ui.layout.DetailHeader
import dev.akkraizen.berill.api.ui.layout.PluginList
import dev.akkraizen.berill.api.ui.nowMillis
import dev.akkraizen.berill.api.ui.rememberTimerSignal
import kotlinx.coroutines.delay

@Composable
internal fun WorkoutScreen(ctx: PluginContext, id: String) {
  val catalog = rememberCatalog(ctx)
  val timer = rememberSaveable(id, saver = WorkoutTimerState.Saver) { WorkoutTimerState() }
  val signal = rememberTimerSignal()
  var now by remember { mutableStateOf(nowMillis()) }
  var timerMessage by remember { mutableStateOf<String?>(null) }
  val collection = remember(ctx) { logs(ctx) }
  val workout by
    remember(collection, id) { collection.observe(id) }.collectAsState(collection.get(id))

  var picking by remember { mutableStateOf<Int?>(null) }
  val w = workout
  if (w == null) {
    PluginList { item { EmptyHint(WorkoutsText.noLogs()) } }
    return
  }
  fun update(change: (WorkoutLog) -> WorkoutLog) {
    val fresh = collection.get(id) ?: return
    collection.save(id, change(fresh))
  }

  fun editExercise(e: Int, change: (LoggedExercise) -> LoggedExercise) = update { log ->
    log.copy(exercises = log.exercises.mapIndexed { i, x -> if (i == e) change(x) else x })
  }

  fun editSet(e: Int, s: Int, change: (LoggedSet) -> LoggedSet) = editExercise(e) { x ->
    x.copy(sets = x.sets.mapIndexed { i, set -> if (i == s) change(set) else set })
  }

  LaunchedEffect(timer.value, w.finished, w.exercises) {
    val active = timer.value ?: return@LaunchedEffect
    val ex = w.exercises.getOrNull(active.exercise)
    if (w.finished || ex?.sets?.getOrNull(active.set) == null) {
      timer.value = null
      return@LaunchedEffect
    }
    if (active.deadlineMillis == null) return@LaunchedEffect
    while (true) {
      now = nowMillis()
      if (active.remaining(now) == 0L) {
        timer.value = null
        signal()
        timerMessage = if (active.resting) WorkoutsText.rest() + ": " + WorkoutsText.timerDone()
          else ex.name + ": " + WorkoutsText.timerDone()
        val fresh = collection.get(id)
        if (fresh != null) {
          val completion = fresh.completeTimer(active, now)
          if (completion.workout != fresh) collection.save(id, completion.workout)
          timer.value = completion.next
        }
        return@LaunchedEffect
      }
      delay(100)
    }
  }

  PluginList {
    item {
      DetailHeader(w.title.ifBlank { WorkoutsText.workoutTitle() }, Icons.Outlined.FitnessCenter) {
        FilledTonalButton(onClick = { update { it.copy(finished = !it.finished) } }) {
          if (!w.finished) {
            Ic(Icons.Outlined.CheckCircle, size = 18.dp)
            Spacer(Modifier.width(8.dp))
          }
          Text(if (w.finished) WorkoutsText.resume() else WorkoutsText.finish())
        }
      }
    }
    item {
      timer.value?.let { active ->
        WorkoutTimerPanel(active, now, w.exercises.getOrNull(active.exercise)?.name.orEmpty(),
          totalSeconds = w.exercises.getOrNull(active.exercise)?.let { ex ->
            if (active.resting) ex.restSeconds else ex.sets.getOrNull(active.set)?.durationSeconds
          } ?: 1,
          onPause = { timer.value = active.pause(nowMillis()) },
          onResume = { now = nowMillis(); timer.value = active.resume(now) },
          onStop = { timer.value = null })
      }
      timerMessage?.let { TimerCompletionBanner(it) { timerMessage = null } }
    }
    item {
      SectionCard {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          CommitTextField(w.title, { v -> update { it.copy(title = v) } }, WorkoutsText.name(), Modifier.weight(2f))
          DateField(
            w.date,
            { v -> if (v != null) update { it.copy(date = v) } },
            WorkoutsText.date(),
            Modifier.weight(1f)
          )
        }
        Text(
          listOf(WorkoutsText.setsDone(w.doneSets(), w.totalSets())(), WorkoutsText.tonnage(formatKg(w.tonnage()))())
            .joinToString(" · "),
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        TextButton(
          enabled = w.exercises.any { exercise -> exercise.sets.any { !it.done } },
          onClick = {
            timer.value = null
            timerMessage = null
            update { it.markAllSetsDone() }
          }
        ) {
          Ic(Icons.Outlined.CheckCircle, size = 18.dp)
          Spacer(Modifier.width(6.dp))
          Text(WorkoutsText.markAll())
        }
      }
    }
    w.exercises.forEachIndexed { e, ex ->
      item(key = "exercise-$e") {
        key(e) {
          LoggedExerciseCard(
            ctx = ctx,
            exercise = ex,
            onMarkAllDone = {
              if (timer.value?.exercise == e) timer.value = null
              editExercise(e) { it.markAllSetsDone() }
            },
            onMode = { mode -> timer.value = null; editExercise(e) { it.withMode(mode) } },
            onRest = { seconds -> editExercise(e) { it.copy(restSeconds = seconds) } },
            onTimer = { s ->
              val seconds = ex.sets.getOrNull(s)?.durationSeconds
              if (seconds != null && seconds > 0 && !w.finished) {
                now = nowMillis()
                timer.value = WorkoutTimer(e, s, seconds.toLong() * 1000).resume(now)
                timerMessage = null
              }
            },
            timerEnabled = !w.finished && timer.value == null,
            onRename = { v -> timer.value = null; editExercise(e) {
              it.withName(v, catalog.visible.firstOrNull { entry -> entry.matches(v) }?.mode)
            } },
            onPick = { picking = e },
            onDelete = {
              timer.value = null
              update { log ->
                log.copy(exercises = log.exercises.filterIndexed { i, _ -> i != e })
              }
            },
            onSet = { s, change ->
              if (timer.value?.exercise == e && timer.value?.set == s) timer.value = null
              editSet(e, s, change)
            },
            onAddSet = {
              editExercise(e) { x ->
                val last = x.sets.lastOrNull()
                x.copy(sets = x.sets + LoggedSet(last?.weight, last?.reps, durationSeconds = last?.durationSeconds))
              }
            },
            onRemoveSet = { s ->
              timer.value = null
              editExercise(e) { x -> x.copy(sets = x.sets.filterIndexed { i, _ -> i != s }) }
            }
          )
        }
      }
    }
    item {
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
          onClick = {
            update {
              it.copy(exercises = it.exercises + LoggedExercise(sets = listOf(LoggedSet())))
            }
          }
        ) {
          Ic(BerillIcons.Add, size = 18.dp)
          Spacer(Modifier.width(6.dp))
          Text(WorkoutsText.addExercise())
        }
        OutlinedButton(onClick = { picking = -1 }) {
          Ic(Icons.Outlined.MenuBook, size = 18.dp)
          Spacer(Modifier.width(6.dp))
          Text(CatalogText.addFromCatalog())
        }
      }
    }
    item {
      SectionCard(WorkoutsText.notes()) {
        BodyEditor(w.notes.orEmpty(), { v -> update { it.copy(notes = v.ifBlank { null }) } })
      }
    }
    item {
      DeleteButton(WorkoutsText.workoutWord()) {
        collection.delete(id)
        ctx.nav.back()
      }
    }
  }

  picking?.let { e ->
    ExercisePickerDialog(ctx, onDismiss = { picking = null }) { selected ->
      timer.value = null
      if (e < 0)
        update {
          it.copy(exercises = it.exercises + LoggedExercise(sets = listOf(LoggedSet())).withName(selected.name, selected.mode))
        }
      else
        editExercise(e) { it.withName(selected.name, selected.mode) }
      picking = null
    }
  }
}

@Composable
private fun LoggedExerciseCard(
  ctx: PluginContext,
  exercise: LoggedExercise,
  onMarkAllDone: () -> Unit,
  onMode: (ExerciseMode) -> Unit,
  onRest: (Int) -> Unit,
  onTimer: (Int) -> Unit,
  timerEnabled: Boolean,
  onRename: (String) -> Unit,
  onPick: () -> Unit,
  onDelete: () -> Unit,
  onSet: (Int, (LoggedSet) -> LoggedSet) -> Unit,
  onAddSet: () -> Unit,
  onRemoveSet: (Int) -> Unit
) {
  val colors = MaterialTheme.colorScheme
  Surface(color = colors.surfaceContainerHigh, shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.fillMaxWidth()) {
    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        CommitTextField(exercise.name, onRename, WorkoutsText.exercise(), Modifier.weight(1f))
        IconButton(onClick = onPick) { Ic(Icons.Outlined.MenuBook, tint = colors.primary) }
        IconButton(onClick = onDelete) {
          Ic(BerillIcons.Delete, tint = colors.onSurfaceVariant)
        }
      }
      ExerciseModeChips(exercise.mode, onMode)
      if (exercise.sets.isNotEmpty()) {
        val done = exercise.sets.count { it.done }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
          Text(
            WorkoutsText.setsDone(done, exercise.sets.size)(),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.labelMedium,
            color = colors.onSurfaceVariant
          )
          TextButton(onClick = onMarkAllDone, enabled = done < exercise.sets.size) {
            Ic(Icons.Outlined.CheckCircle, size = 18.dp)
            Spacer(Modifier.width(6.dp))
            Text(WorkoutsText.markAll())
          }
        }
        ProgressLine(done.toFloat() / exercise.sets.size)
      }
      if (exercise.durationPerSet) {
        DurationField(exercise.restSeconds, onRest, label = WorkoutsText.restDuration(), allowZero = true)
      }
      exercise.sets.forEachIndexed { s, set ->
        key(s) {
          LoggedSetCard(
            number = s + 1, set = set,
            singleDuration = exercise.mode == ExerciseMode.DURATION,
            timerEnabled = timerEnabled,
            onChange = { change -> onSet(s, change) },
            onTimer = { onTimer(s) }, onRemove = { onRemoveSet(s) }
          )
        }
      }
      Row(verticalAlignment = Alignment.CenterVertically) {
        if (exercise.mode != ExerciseMode.DURATION) OutlinedButton(onClick = onAddSet) {
          Ic(BerillIcons.Add, size = 18.dp)
          Spacer(Modifier.width(6.dp))
          Text(WorkoutsText.addSet())
        }
        Spacer(Modifier.weight(1f))

        val best = exercise.sets
          .filter { it.done && (it.weight ?: 0.0) > 0 && (it.reps ?: 0) > 0 }
          .mapNotNull { s -> estimateOneRepMax(s.weight!!, s.reps!!)?.let { s to it } }
          .maxByOrNull { it.second }
        if (best != null && exercise.name.isNotBlank()) {
          Text(
            WorkoutsText.bestSet(formatKg(best.first.weight!!), best.first.reps!!, formatKg(best.second))(),
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
            modifier = Modifier.weight(2f, fill = false)
          )
          TextButton(onClick = { setMax(ctx, exercise.name, roundTo(best.second, 0.5)) }) {
            Text(WorkoutsText.saveAsMax())
          }
        }
      }
    }
  }
}
