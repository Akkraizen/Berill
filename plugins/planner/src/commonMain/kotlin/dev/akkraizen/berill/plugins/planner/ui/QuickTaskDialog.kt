package dev.akkraizen.berill.plugins.planner.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxWidth
import dev.akkraizen.berill.api.PluginContext
import dev.akkraizen.berill.api.i18n.I18n
import dev.akkraizen.berill.api.ui.interaction.BerillTextField
import dev.akkraizen.berill.api.ui.layout.*
import dev.akkraizen.berill.api.ui.state.*
import dev.akkraizen.berill.api.ui.nowMillis
import dev.akkraizen.berill.api.ui.today
import dev.akkraizen.berill.api.ui.toDateOrNull
import dev.akkraizen.berill.plugins.planner.data.*
import kotlinx.coroutines.CancellationException

@Composable
internal fun QuickTaskDialog(ctx: PluginContext, onDismiss: () -> Unit, onError: (Exception) -> Unit) {
  val entries = LocalQuickEntries.current
  val data = remember(ctx) { PlannerData(ctx) }
  val initial = remember { PlannerTask(date = today().toString()) }
  val draft = rememberQuickDraft(QuickEntryKind.TASK, PlannerTask.serializer(), initial)
  var task by draft
  val recent by data.tasks.observe().collectAsState(emptyList())
  val valid = task.text.isNotBlank() && task.date.orEmpty().toDateOrNull() != null
  QuickActionDialog(I18n.text("quick.task"), Icons.Outlined.CalendarMonth, onDismiss) {
    QuickForm {
      QuickPresets(QuickEntryKind.TASK, PlannerTask.serializer(), task, task.text.isNotBlank(),
        recent.sortedByDescending { it.value.order }.map { PlannerTask(text = it.value.text, date = task.date) }, { it.text }, { task = PlannerTask(text = it.text, date = task.date) })
      BerillTextField(task.text, { task = task.copy(text = it) }, Modifier.fillMaxWidth(), label = { Text(I18n.text("quick.taskText")) })
      QuickDateField(task.date.orEmpty()) { task = task.copy(date = it) }
      QuickDraftActions { draft.reset(initial.copy(date = today().toString())) }
      QuickSaveButtons(valid, onDismiss) { again ->
        try {
          saveQuickEntry(entries, data.tasks, ctx.data.newId(), PlannerTask(text = normalizeInput(task.text), date = task.date!!.toDateOrNull().toString(), order = nowMillis()))
          draft.reset(PlannerTask(date = task.date))
          if (!again) onDismiss()
        } catch (e: CancellationException) { throw e }
          catch (e: Exception) { onError(e) }
      }
    }
  }
}
