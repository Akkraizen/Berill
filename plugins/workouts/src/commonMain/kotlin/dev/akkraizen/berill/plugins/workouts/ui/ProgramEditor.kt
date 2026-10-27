package dev.akkraizen.berill.plugins.workouts.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ListAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import dev.akkraizen.berill.api.PluginContext
import dev.akkraizen.berill.api.ui.cards.SectionCard
import dev.akkraizen.berill.api.ui.dialogs.DeleteButton
import dev.akkraizen.berill.api.ui.feedback.EmptyHint
import dev.akkraizen.berill.api.ui.fields.BodyEditor
import dev.akkraizen.berill.api.ui.fields.CommitTextField
import dev.akkraizen.berill.api.ui.interaction.BerillTextField
import dev.akkraizen.berill.api.ui.interaction.DialogKeys
import dev.akkraizen.berill.api.ui.layout.DetailHeader
import dev.akkraizen.berill.api.ui.layout.PluginList
import dev.akkraizen.berill.plugins.workouts.data.Program
import dev.akkraizen.berill.plugins.workouts.data.programs
import dev.akkraizen.berill.plugins.workouts.data.saveProgram
import dev.akkraizen.berill.plugins.workouts.i18n.WorkoutsText

@Composable
internal fun ProgramEditor(ctx: PluginContext, id: String) {
  val collection = remember(ctx) { programs(ctx) }
  val program by
    remember(collection, id) { collection.observe(id) }.collectAsState(collection.get(id))
  val p = program
  if (p == null) {
    PluginList { item { EmptyHint(WorkoutsText.programSummaryEmpty()) } }
    return
  }

  fun update(change: (Program) -> Program) {
    val fresh = collection.get(id) ?: return
    saveProgram(ctx, id, change(fresh))
  }

  PluginList {
    item { DetailHeader(p.title.ifBlank { WorkoutsText.pageProgram() }, Icons.Outlined.ListAlt) }
    item {
      SectionCard {
        CommitTextField(p.title, { v -> update { it.copy(title = v) } }, WorkoutsText.name())
      }
    }
    item {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        DaysEditor(ctx, p.days, percentMode = true) { edit ->
          update { it.copy(days = edit(it.days)) }
        }
      }
    }
    item {
      SectionCard(WorkoutsText.programNotes()) {
        BodyEditor(p.notes.orEmpty(), { v -> update { it.copy(notes = v.ifBlank { null }) } })
      }
    }
    item {
      DeleteButton(WorkoutsText.programWord()) {
        collection.delete(id)
        ctx.nav.back()
      }
    }
  }
}

@Composable
internal fun NameDialog(title: String, onDismiss: () -> Unit, onCreate: (String) -> Unit) {
  var name by remember { mutableStateOf("") }
  val confirm: () -> Unit = { onCreate(name.trim()) }
  DialogKeys(onConfirm = confirm, confirmEnabled = name.isNotBlank(), onDismiss = onDismiss)
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(title) },
    text = {
      BerillTextField(name, { name = it }, label = { Text(WorkoutsText.name()) }, singleLine = true)
    },
    confirmButton = {
      TextButton(enabled = name.isNotBlank(), onClick = confirm) { Text(WorkoutsText.create()) }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text(WorkoutsText.cancel()) } }
  )
}
