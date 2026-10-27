package dev.akkraizen.berill.plugins.workouts.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.akkraizen.berill.api.PluginContext
import dev.akkraizen.berill.api.ui.feedback.EmptyHint
import dev.akkraizen.berill.api.ui.interaction.mouseScrollable
import dev.akkraizen.berill.api.ui.selection.ChoiceChips
import dev.akkraizen.berill.api.ui.selection.pastelCheckboxColors
import dev.akkraizen.berill.plugins.workouts.data.MAX_PROGRAM_DAYS
import dev.akkraizen.berill.plugins.workouts.data.Program
import dev.akkraizen.berill.plugins.workouts.data.applyProgram
import dev.akkraizen.berill.plugins.workouts.data.currentWeek
import dev.akkraizen.berill.plugins.workouts.data.programs
import dev.akkraizen.berill.plugins.workouts.i18n.WorkoutsText

@Composable
internal fun AssignProgramDialog(
  ctx: PluginContext,
  weekCount: Int,
  currentWeek: Int,
  onDismiss: () -> Unit,
  onApply: (String, Program, Set<Int>) -> Unit
) {
  val collection = remember(ctx) { programs(ctx) }
  val all by remember(collection) { collection.observe() }.collectAsState(emptyList())
  val available = all.filter { it.value.days.size <= MAX_PROGRAM_DAYS }.sortedBy { it.value.title }
  var programId by remember { mutableStateOf<String?>(null) }
  var selectedWeeks by remember { mutableStateOf(setOf(currentWeek)) }
  val program = available.firstOrNull { it.id == programId }?.value
  val validWeeks = selectedWeeks.filter { it in 1..weekCount }.toSet()
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(WorkoutsText.assignProgram()) },
    text = {
      Column(
        Modifier.fillMaxWidth().heightIn(max = 480.dp).mouseScrollable(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        Text(WorkoutsText.assignHint(), style = MaterialTheme.typography.bodySmall)
        if (all.isEmpty()) EmptyHint(WorkoutsText.noPrograms())
        else if (available.isEmpty()) EmptyHint(WorkoutsText.dayLimit())
        else {
          Text(WorkoutsText.chooseProgram(), style = MaterialTheme.typography.titleSmall)
          ChoiceChips(
            available.map { it.id },
            programId,
            { id -> available.first { it.id == id }.value.title.ifBlank { WorkoutsText.pageProgram() } },
            { programId = it }
          )
          Text(WorkoutsText.chooseWeeks(), style = MaterialTheme.typography.titleSmall)
          for (week in 1..weekCount) {
            Row(
              Modifier.fillMaxWidth().toggleable(
                value = week in selectedWeeks,
                role = Role.Checkbox,
                onValueChange = { checked ->
                  selectedWeeks = if (checked) selectedWeeks + week else selectedWeeks - week
                }
              ),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Checkbox(colors = pastelCheckboxColors(), checked = week in selectedWeeks, onCheckedChange = null)
              Text(WorkoutsText.week(week)())
            }
          }
        }
      }
    },
    confirmButton = {
      TextButton(enabled = program != null && validWeeks.isNotEmpty(), onClick = {
        program?.let { selected -> programId?.let { onApply(it, selected, validWeeks) } }
      }) { Text(WorkoutsText.applyProgram()) }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text(WorkoutsText.cancel()) } }
  )
}
