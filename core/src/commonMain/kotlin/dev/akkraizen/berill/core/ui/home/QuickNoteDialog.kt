package dev.akkraizen.berill.core.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.akkraizen.berill.api.i18n.I18n
import dev.akkraizen.berill.api.ui.BerillIcons
import dev.akkraizen.berill.api.ui.interaction.BerillTextField
import dev.akkraizen.berill.api.ui.layout.*
import dev.akkraizen.berill.api.ui.markdown.MarkdownEditor
import dev.akkraizen.berill.api.ui.state.*
import dev.akkraizen.berill.core.BerillCore
import dev.akkraizen.berill.core.connections.FailureOperation
import dev.akkraizen.berill.core.notes.Note
import dev.akkraizen.berill.core.notes.displayTitle
import dev.akkraizen.berill.core.ui.CoreText

@Composable
internal fun QuickNoteDialog(core: BerillCore, onDismiss: () -> Unit) {
  val preferences by core.editorPreferences.state.collectAsState()
  val entries = LocalQuickEntries.current
  val draft = rememberQuickDraft(QuickEntryKind.NOTE, Note.serializer(), Note())
  var note by draft
  val recent by core.notes.observe().collectAsState(emptyList())
  val valid = note.title.isNotBlank() || note.body.isNotBlank()
  QuickActionDialog(CoreText.newNote(), BerillIcons.Add, onDismiss) {
    QuickForm {
      QuickPresets(QuickEntryKind.NOTE, Note.serializer(), note.copy(updatedAt = 0), valid,
        recent.map { it.value.copy(updatedAt = 0) }, { it.displayTitle() }, { note = it })
      BerillTextField(note.title, { note = note.copy(title = it) }, Modifier.fillMaxWidth(), placeholder = { Text(CoreText.titlePlaceholder()) }, singleLine = true)
      MarkdownEditor(note.body, { note = note.copy(body = it) }, Modifier.fillMaxWidth().heightIn(min = 180.dp), placeholder = CoreText.bodyPlaceholder(), lineNumbers = preferences.lineNumbers,
        textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = preferences.fontSize.sp, fontFamily = if (preferences.monospace) androidx.compose.ui.text.font.FontFamily.Monospace else MaterialTheme.typography.bodyLarge.fontFamily))
      QuickDraftActions { draft.reset(Note()) }
      QuickSaveButtons(valid, onDismiss) { again ->
        core.connections.errors.run(FailureOperation.COMMAND) {
          val id = core.data.newId()
          core.notes.save(id, note.title, note.body)
          val saved = core.notes.get(id)
          entries.created { if (core.notes.get(id) == saved) { core.notes.delete(id); true } else false }
          draft.reset(Note())
          if (!again) onDismiss()
        }
      }
    }
  }
}
