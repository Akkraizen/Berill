package dev.akkraizen.berill.plugins.workouts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.akkraizen.berill.api.PluginContext
import dev.akkraizen.berill.api.ui.display.Ic
import dev.akkraizen.berill.api.ui.feedback.EmptyHint
import dev.akkraizen.berill.api.ui.interaction.BerillTextField
import dev.akkraizen.berill.api.ui.interaction.DialogKeys
import dev.akkraizen.berill.api.ui.layout.AutoScrollLazyColumn as LazyColumn
import dev.akkraizen.berill.api.ui.selection.ChoiceChips

@Composable
internal fun ExerciseDialog(
  initial: CustomExercise?,
  muscle: String,
  existing: List<CatalogExercise>,
  onDismiss: () -> Unit,
  onSave: (CustomExercise) -> Unit
) {
  var name by remember { mutableStateOf(initial?.name.orEmpty()) }
  var m by remember { mutableStateOf(muscle) }
  var mode by remember { mutableStateOf(initial?.mode ?: ExerciseMode.REPETITIONS) }
  var eq by remember { mutableStateOf(initial?.equipment ?: EQ_BARBELL) }
  val duplicate = name.isNotBlank() && existing.any { it.matches(name) }
  val valid = name.isNotBlank() && !duplicate
  val confirm: () -> Unit = {
    if (valid)
      onSave((initial ?: CustomExercise()).copy(name = name.trim(), muscle = m, equipment = eq, mode = mode))
  }
  DialogKeys(onConfirm = confirm, confirmEnabled = valid, onDismiss = onDismiss)
  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Text(if (initial == null) CatalogText.newExercise() else CatalogText.editExercise())
    },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        BerillTextField(
          name,
          { name = it },
          label = { Text(CatalogText.name()) },
          singleLine = true,
          isError = duplicate,
          supportingText = if (duplicate) {
            { Text(CatalogText.alreadyExists()) }
          } else
            null,
          modifier = Modifier.fillMaxWidth()
        )
        Text(
          CatalogText.muscle(),
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        ChoiceChips(MUSCLES, m, CatalogText::muscleLabel, { m = it })
        Text(
          CatalogText.equipment(),
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        ChoiceChips(EQUIPMENT, eq, CatalogText::equipmentLabel, { eq = it })
        Text(WorkoutsText.exerciseType(), style = MaterialTheme.typography.labelLarge)
        ExerciseModeChips(mode) { mode = it }
      }
    },
    confirmButton = {
      TextButton(onClick = confirm, enabled = valid) {
        Text(if (initial == null) CatalogText.add() else CatalogText.save())
      }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text(CatalogText.cancel()) } }
  )
}

@Composable
internal fun ExercisePickerDialog(ctx: PluginContext, onDismiss: () -> Unit, onPick: (CatalogExercise) -> Unit) {
  val catalog = rememberCatalog(ctx)
  val images = rememberExerciseImages(ctx)
  var query by remember { mutableStateOf("") }
  var muscle by remember { mutableStateOf<String?>(null) }
  val found = catalog.visible.filtered(query, muscle, null)
  DialogKeys(onConfirm = found.singleOrNull()?.let { only -> { onPick(only) } }, onDismiss = onDismiss)
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(CatalogText.pickTitle()) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        BerillTextField(
          query,
          { query = it },
          placeholder = { Text(CatalogText.searchPlaceholder()) },
          leadingIcon = { Ic(Icons.Outlined.Search) },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
          onSubmit = { found.singleOrNull()?.let { onPick(it) } }
        )
        ChoiceChips(
          listOf<String?>(null) + MUSCLES,
          muscle,
          { it?.let(CatalogText::muscleLabel) ?: CatalogText.allMuscles() },
          { muscle = it }
        )
        if (found.isEmpty()) EmptyHint(CatalogText.nothing())
        LazyColumn(Modifier.heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
          items(found, key = { it.id }) { ex ->
            CatalogRow(ex, images.of(ex)) { onPick(ex) }
          }
        }
      }
    },
    confirmButton = {},
    dismissButton = { TextButton(onClick = onDismiss) { Text(CatalogText.cancel()) } }
  )
}
