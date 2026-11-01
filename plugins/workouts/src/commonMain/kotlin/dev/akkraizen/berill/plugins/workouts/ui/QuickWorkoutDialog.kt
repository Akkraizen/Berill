package dev.akkraizen.berill.plugins.workouts.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import dev.akkraizen.berill.api.PluginContext
import dev.akkraizen.berill.api.i18n.I18n
import dev.akkraizen.berill.api.ui.interaction.BerillTextField
import dev.akkraizen.berill.api.ui.layout.*
import dev.akkraizen.berill.api.ui.state.*
import dev.akkraizen.berill.api.ui.toDateOrNull
import dev.akkraizen.berill.api.ui.today
import dev.akkraizen.berill.plugins.workouts.data.*
import dev.akkraizen.berill.plugins.workouts.i18n.WorkoutsText
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable

@Serializable
internal data class QuickWorkoutDraft(val title: String = "", val date: String = "", val exercises: List<LoggedExercise> = emptyList(), val source: String? = null, val activeId: String? = null)

internal fun WorkoutLog.asQuickTemplate(date: String) = QuickWorkoutDraft(title, date,
  exercises.map { exercise -> exercise.copy(sets = exercise.sets.map { it.copy(done = false) }) }, source)

@Composable
internal fun QuickWorkoutDialog(ctx: PluginContext, onDismiss: () -> Unit, onError: (Exception) -> Unit) {
  val entries = LocalQuickEntries.current
  val initial = remember { QuickWorkoutDraft(WorkoutsText.workoutTitle(), today().toString()) }
  val draft = rememberQuickDraft(QuickEntryKind.WORKOUT, QuickWorkoutDraft.serializer(), initial)
  var value by draft
  val collection = remember(ctx) { logs(ctx) }
  val recent by collection.observe().collectAsState(emptyList())
  val popup = rememberPopupContext(ctx, onDismiss)
  val active = value.activeId?.takeIf { collection.get(it) != null }
  QuickActionDialog(WorkoutsText.quickWorkout(), Icons.Outlined.FitnessCenter, onDismiss) {
    if (active != null) Column {
      TextButton(onClick = { draft.reset(initial.copy(date = today().toString())) }) { Text(I18n.text("quick.nextWorkout")) }
      Box(Modifier.weight(1f)) { WorkoutScreen(popup, active) }
    } else QuickForm {
      QuickPresets(QuickEntryKind.WORKOUT, QuickWorkoutDraft.serializer(), value.copy(activeId = null), value.title.isNotBlank(),
        recent.sortedByDescending { it.value.createdAt }.map { it.value.asQuickTemplate(value.date) }, { it.title }, { value = it.copy(date = value.date, activeId = null) })
      BerillTextField(value.title, { value = value.copy(title = it) }, Modifier.fillMaxWidth(), label = { Text(WorkoutsText.name()) }, singleLine = true)
      QuickDateField(value.date) { value = value.copy(date = it) }
      QuickDraftActions { draft.reset(initial.copy(date = today().toString())) }
      QuickSaveButtons(value.title.isNotBlank() && value.date.toDateOrNull() != null, onDismiss) { again ->
        try {
          val id = createWorkout(ctx, value.title.trim(), value.source, value.exercises, value.date.toDateOrNull().toString())
          val saved = collection.get(id)
          entries.created { if (collection.get(id) == saved) { collection.delete(id); true } else false }
          if (again) draft.reset(initial.copy(date = value.date)) else value = value.copy(activeId = id)
        } catch (e: CancellationException) { throw e }
          catch (e: Exception) { onError(e) }
      }
    }
  }
}
