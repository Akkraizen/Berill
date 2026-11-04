package dev.akkraizen.berill.plugins.projects.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material.icons.automirrored.outlined.NoteAdd
import androidx.compose.material.icons.automirrored.outlined.StickyNote2
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.akkraizen.berill.api.NoteRef
import dev.akkraizen.berill.api.PluginContext
import dev.akkraizen.berill.api.ui.UiText
import dev.akkraizen.berill.api.ui.cards.SectionCard
import dev.akkraizen.berill.api.ui.display.Ic
import dev.akkraizen.berill.api.ui.feedback.EmptyHint
import dev.akkraizen.berill.api.ui.fields.SearchField
import dev.akkraizen.berill.api.ui.interaction.interactive
import dev.akkraizen.berill.plugins.projects.data.Project
import dev.akkraizen.berill.plugins.projects.i18n.ProjectsText

@Composable
internal fun LinkedNotesCard(ctx: PluginContext, project: Project, onChange: ((Project) -> Project) -> Unit) {
  if (!ctx.notes.available) return
  val notes by remember(ctx) { ctx.notes.observe() }.collectAsState(emptyList())
  val byId = notes.associateBy { it.id }
  val linked = project.notes.mapNotNull { byId[it] }
  var picking by remember { mutableStateOf(false) }

  SectionCard(ProjectsText.linkedNotes(), Icons.AutoMirrored.Outlined.StickyNote2, collapseKey = "projects/notes") {
    if (linked.isEmpty()) EmptyHint(ProjectsText.noLinkedNotes())
    linked.forEach { note ->
      Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).interactive(bleed = 4.dp) { ctx.nav.openNote(note.id) }) {
          Text(note.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
          if (note.preview.isNotBlank())
            Text(note.preview, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = { onChange { it.copy(notes = it.notes - note.id) } }) {
          Icon(Icons.Outlined.LinkOff, ProjectsText.unlink(), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      OutlinedButton(onClick = { picking = true }) {
        Ic(Icons.Outlined.Link, size = 18.dp)
        Spacer(Modifier.width(8.dp))
        Text(ProjectsText.linkNote())
      }
      FilledTonalButton(onClick = {
        val id = ctx.notes.create(project.title.ifBlank { ProjectsText.untitled() }, "")
        if (id != null) {
          onChange { it.copy(notes = (it.notes + id).distinct()) }
          ctx.nav.openNote(id)
        }
      }) {
        Ic(Icons.AutoMirrored.Outlined.NoteAdd, size = 18.dp)
        Spacer(Modifier.width(8.dp))
        Text(ProjectsText.createNote())
      }
    }
  }

  if (picking)
    NotePicker(notes.filter { it.id !in project.notes }, onPick = { id ->
      onChange { it.copy(notes = (it.notes + id).distinct()) }
      picking = false
    }, onDismiss = { picking = false })
}

@Composable
private fun NotePicker(notes: List<NoteRef>, onPick: (String) -> Unit, onDismiss: () -> Unit) {
  var query by remember { mutableStateOf("") }
  val terms = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
  val found = notes.filter { note -> terms.all { it in (note.title + " " + note.preview).lowercase() } }.take(100)
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(ProjectsText.linkNote()) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SearchField(query, { query = it }, ProjectsText.searchNotes())
        Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
          if (found.isEmpty()) EmptyHint(ProjectsText.nothingHere())
          found.forEach { note ->
            Column(Modifier.fillMaxWidth().interactive(bleed = 4.dp) { onPick(note.id) }) {
              Text(note.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
              if (note.preview.isNotBlank())
                Text(note.preview, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
          }
        }
      }
    },
    confirmButton = {},
    dismissButton = { TextButton(onClick = onDismiss) { Text(UiText.cancel()) } }
  )
}
