package dev.akkraizen.berill.plugins.workouts

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.akkraizen.berill.api.PluginContext
import dev.akkraizen.berill.api.ui.BerillIcons
import dev.akkraizen.berill.api.ui.animation.BerillMotion
import dev.akkraizen.berill.api.ui.dialogs.ConfirmDialog
import dev.akkraizen.berill.api.ui.display.Ic
import dev.akkraizen.berill.api.ui.feedback.EmptyHint
import dev.akkraizen.berill.api.ui.fields.CommitTextField
import dev.akkraizen.berill.api.ui.fields.DoubleField
import dev.akkraizen.berill.api.ui.fields.IntField
import dev.akkraizen.berill.api.ui.interaction.BerillChip

internal typealias DaysEdit = (List<WorkoutDay>) -> List<WorkoutDay>

internal fun List<WorkoutDay>.editDay(
  index: Int,
  change: (WorkoutDay) -> WorkoutDay
): List<WorkoutDay> = if (index !in indices) this else mapIndexed { i, d -> if (i == index) change(d) else d }

internal fun List<WorkoutDay>.editExercise(day: Int, exercise: Int, change: (Exercise) -> Exercise): List<WorkoutDay> =
  editDay(day) { d ->
    if (exercise !in d.exercises.indices)
      d
    else
      d.copy(exercises = d.exercises.mapIndexed { i, e -> if (i == exercise) change(e) else e })
  }

private fun <T> List<T>.move(from: Int, to: Int): List<T> =
  if (from !in indices || to !in indices)
    this
  else
    toMutableList().also { it.add(to, it.removeAt(from)) }

@Composable
internal fun DaysEditor(ctx: PluginContext, days: List<WorkoutDay>, percentMode: Boolean, onEdit: (DaysEdit) -> Unit) {
  val catalog = rememberCatalog(ctx)
  var deleting by remember { mutableStateOf<Int?>(null) }

  var picking by remember { mutableStateOf<Pair<Int, Int?>?>(null) }
  days.forEachIndexed { d, day ->
    key(d) {
      Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            CommitTextField(
              day.title,
              { t -> onEdit { it.editDay(d) { x -> x.copy(title = t) } } },
              WorkoutsText.dayName(),
              Modifier.weight(1f)
            )
            IconButton(onClick = { onEdit { it.move(d, d - 1) } }, enabled = d > 0) {
              Ic(Icons.Outlined.KeyboardArrowUp)
            }
            IconButton(onClick = { onEdit { it.move(d, d + 1) } }, enabled = d < days.lastIndex) {
              Ic(Icons.Outlined.KeyboardArrowDown)
            }
            IconButton(onClick = { deleting = d }) {
              Ic(BerillIcons.Delete, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
          }
          if (day.exercises.isEmpty()) EmptyHint(WorkoutsText.noExercises())
          day.exercises.forEachIndexed { e, ex ->
            key(e) {
              ExerciseEditor(
                ex,
                catalog,
                percentMode,
                d,
                e,
                day.exercises.size,
                onEdit,
                onPick = { picking = d to e }
              )
            }
          }
          Row {
            TextButton(
              onClick = {
                onEdit {
                  it.editDay(d) { x -> x.copy(exercises = x.exercises + Exercise()) }
                }
              }
            ) {
              Ic(BerillIcons.Add, size = 18.dp)
              Spacer(Modifier.width(6.dp))
              Text(WorkoutsText.addExercise())
            }
            TextButton(onClick = { picking = d to null }) {
              Ic(Icons.Outlined.MenuBook, size = 18.dp)
              Spacer(Modifier.width(6.dp))
              Text(CatalogText.addFromCatalog())
            }
          }
        }
      }
    }
  }
  OutlinedButton(
    enabled = days.size < MAX_PROGRAM_DAYS,
    onClick = { onEdit { it.appendDay(WorkoutDay(WorkoutsText.dayTitle(it.size + 1)())) } }
  ) {
    Ic(BerillIcons.Add, size = 18.dp)
    Spacer(Modifier.width(6.dp))
    Text(WorkoutsText.addDay())
  }
  if (days.size >= MAX_PROGRAM_DAYS) {
    Text(WorkoutsText.dayLimit(), style = MaterialTheme.typography.bodySmall)
  }
  picking?.let { (d, e) ->
    ExercisePickerDialog(ctx, onDismiss = { picking = null }) { selected ->
      onEdit { list ->
        if (e == null)
          list.editDay(d) { x -> x.copy(exercises = x.exercises + Exercise().withName(selected.name, selected.mode)) }
        else
          list.editExercise(d, e) { it.withName(selected.name, selected.mode) }
      }
      picking = null
    }
  }
  deleting?.let { d ->
    ConfirmDialog(
      title = WorkoutsText.deleteDayQuestion(days.getOrNull(d)?.title.orEmpty())(),
      text = WorkoutsText.deleteDayText(),
      onConfirm = { onEdit { list -> list.filterIndexed { i, _ -> i != d } } },
      onDismiss = { deleting = null }
    )
  }
}

@Composable
private fun ExerciseEditor(
  ex: Exercise,
  catalog: Catalog,
  percentMode: Boolean,
  d: Int,
  e: Int,
  count: Int,
  onEdit: (DaysEdit) -> Unit,
  onPick: () -> Unit
) {
  fun edit(change: (Exercise) -> Exercise) = onEdit { it.editExercise(d, e, change) }
  Surface(Modifier.fillMaxWidth().animateContentSize(BerillMotion.spatial()),
    color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.large) {
    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        CommitTextField(ex.name, { v -> edit { it.withName(v, catalog.visible.firstOrNull { entry -> entry.matches(v) }?.mode) } }, WorkoutsText.exercise(), Modifier.weight(1f))
        IconButton(onClick = onPick) {
          Ic(Icons.Outlined.MenuBook, size = 20.dp, tint = MaterialTheme.colorScheme.primary)
        }
      }
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(WorkoutsText.exerciseType(), style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        IconButton(
          onClick = {
            onEdit { it.editDay(d) { x -> x.copy(exercises = x.exercises.move(e, e - 1)) } }
          },
          enabled = e > 0
        ) {
          Ic(Icons.Outlined.KeyboardArrowUp, size = 20.dp)
        }
        IconButton(
          onClick = {
            onEdit { it.editDay(d) { x -> x.copy(exercises = x.exercises.move(e, e + 1)) } }
          },
          enabled = e < count - 1
        ) {
          Ic(Icons.Outlined.KeyboardArrowDown, size = 20.dp)
        }
        IconButton(
          onClick = {
            onEdit {
              it.editDay(d) { x ->
                x.copy(exercises = x.exercises.filterIndexed { i, _ -> i != e })
              }
            }
          }
        ) {
          Ic(BerillIcons.Close, size = 20.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      }
      ExerciseModeChips(ex.mode) { mode -> edit { it.withMode(mode) } }
      if (ex.durationSeconds != null) {
        if (ex.durationPerSet) {
          IntField(ex.sets, { v -> edit { it.copy(sets = (v ?: 1).coerceIn(1, 50)) } }, WorkoutsText.sets())
        }
        DurationField(ex.durationSeconds, { v -> edit { it.copy(durationSeconds = v) } })
        if (ex.durationPerSet) {
          DurationField(ex.restSeconds, { v -> edit { it.copy(restSeconds = v) } },
            label = WorkoutsText.restDuration(), allowZero = true)
        }
      } else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        IntField(
          ex.sets,
          { v -> edit { it.copy(sets = (v ?: 1).coerceIn(1, 50)) } },
          WorkoutsText.sets(),
          Modifier.weight(1f)
        )
        CommitTextField(
          ex.reps,
          { v -> edit { it.copy(reps = v.ifBlank { "1" }) } },
          WorkoutsText.reps(),
          Modifier.weight(1f)
        )
        if (percentMode && ex.percent != null) {
          DoubleField(
            ex.percent,
            { v -> edit { it.copy(percent = v?.coerceIn(1.0, 120.0)) } },
            WorkoutsText.percentOfMax(),
            Modifier.weight(1f)
          )
        } else {
          DoubleField(
            ex.weight,
            { v -> edit { it.copy(weight = v?.coerceAtLeast(0.0)) } },
            WorkoutsText.weightKg(),
            Modifier.weight(1f)
          )
        }
      }
      if (percentMode && ex.durationSeconds == null) {

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          BerillChip(
            onClick = { edit { it.copy(percent = null) } },
            selected = ex.percent == null,
            label = { Text(WorkoutsText.byWeight()) }
          )
          BerillChip(
            onClick = { edit { it.copy(percent = it.percent ?: 70.0, weight = null) } },
            selected = ex.percent != null,
            label = { Text(WorkoutsText.byPercent()) }
          )
        }
      }
    }
  }
}
