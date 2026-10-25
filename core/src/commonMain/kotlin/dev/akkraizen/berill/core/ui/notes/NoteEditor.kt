package dev.akkraizen.berill.core.ui.notes

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import dev.akkraizen.berill.api.ui.BerillIcons
import dev.akkraizen.berill.api.ui.animation.BerillMotion
import dev.akkraizen.berill.api.ui.interaction.DialogKeys
import dev.akkraizen.berill.api.ui.interaction.mouseScrollable
import dev.akkraizen.berill.api.ui.markdown.MarkdownEditor
import dev.akkraizen.berill.api.ui.markdown.MarkdownText
import dev.akkraizen.berill.api.ui.markdown.toggleMarkdownTask
import dev.akkraizen.berill.core.BerillCore
import dev.akkraizen.berill.core.notes.*
import dev.akkraizen.berill.core.ui.CoreText
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop

private class NoteDraft(initial: Note?) {
  val isNew = initial == null
  var title by mutableStateOf(initial?.title ?: "")
  var body by mutableStateOf(initial?.body ?: "")
  var deleted = false
}

@OptIn(FlowPreview::class)
@Composable
internal fun NoteEditor(core: BerillCore, id: String, showBack: Boolean) {
  val notes = core.notes
  val draft = remember(id) { NoteDraft(notes.get(id)) }
  var confirmDelete by remember { mutableStateOf(false) }
  val titleFocus = remember { FocusRequester() }

  var editing by remember(id) { mutableStateOf(true) }

  LaunchedEffect(draft) {
    snapshotFlow { draft.title to draft.body }
      .drop(1)
      .debounce(500)
      .collect { (title, body) -> if (!draft.deleted) notes.save(id, title, body) }
  }
  DisposableEffect(draft) {
    onDispose { if (!draft.deleted) notes.save(id, draft.title, draft.body) }
  }
  LaunchedEffect(draft, editing) {
    if (draft.isNew && editing) runCatching { titleFocus.requestFocus() }
  }

  val colors = MaterialTheme.colorScheme
  Column(
    Modifier.fillMaxSize().onPreviewKeyEvent { e ->
      val toggle =
        e.type == KeyEventType.KeyDown && (e.isCtrlPressed || e.isMetaPressed) && e.key == Key.E
      if (toggle) editing = !editing
      toggle
    }
  ) {
    Row(
      Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      if (showBack)
        IconButton(onClick = { core.nav.back() }) {
          Icon(BerillIcons.Back, contentDescription = CoreText.back())
        }
      Spacer(Modifier.weight(1f))
      IconButton(onClick = { editing = !editing }) {
        Icon(
          if (editing) Icons.Outlined.Visibility else BerillIcons.Edit,
          contentDescription = if (editing) CoreText.readMode() else CoreText.editMode()
        )
      }
      IconButton(onClick = { confirmDelete = true }) {
        Icon(BerillIcons.Delete, contentDescription = CoreText.deleteNote())
      }
    }
    AnimatedContent(
      targetState = editing,
      transitionSpec = {
        fadeIn(BerillMotion.slowEffects()) togetherWith fadeOut(BerillMotion.effects())
      },
      modifier = Modifier.fillMaxWidth().weight(1f),
      label = "note-mode"
    ) { edit ->
      if (edit) {

        Column(
          Modifier.fillMaxSize()
            .mouseScrollable(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, bottom = 24.dp)
        ) {
          EditorField(
            value = draft.title,
            onValueChange = { draft.title = it },
            placeholder = CoreText.titlePlaceholder(),
            style = MaterialTheme.typography.headlineSmall.copy(color = colors.onSurface),
            modifier = Modifier.fillMaxWidth().focusRequester(titleFocus)
          )
          Spacer(Modifier.height(12.dp))
          MarkdownEditor(
            value = draft.body,
            onValueChange = { draft.body = it },
            placeholder = CoreText.bodyPlaceholder(),
            onWikiLink = { page -> openWikiLink(core, page) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 360.dp)
          )
        }
      } else {
        NotePreview(core, draft)
      }
    }
  }

  if (confirmDelete) {
    val confirm: () -> Unit = {
      draft.deleted = true
      notes.delete(id)
      confirmDelete = false
      core.nav.back()
    }
    DialogKeys(onConfirm = confirm, onDismiss = { confirmDelete = false })
    AlertDialog(
      onDismissRequest = { confirmDelete = false },
      title = { Text(CoreText.deleteNoteTitle()) },
      text = { Text(CoreText.deleteNoteText()) },
      confirmButton = { TextButton(onClick = confirm) { Text(CoreText.delete()) } },
      dismissButton = {
        TextButton(onClick = { confirmDelete = false }) { Text(CoreText.cancel()) }
      }
    )
  }
}

@Composable
private fun NotePreview(core: BerillCore, draft: NoteDraft) {
  Column(
    Modifier.fillMaxSize()
      .mouseScrollable(rememberScrollState())
      .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    if (draft.title.isNotBlank()) Text(draft.title, style = MaterialTheme.typography.headlineSmall)
    if (draft.body.isBlank()) {
      Text(CoreText.emptyNote(), color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
      MarkdownText(
        draft.body,
        onWikiLink = { page -> openWikiLink(core, page) },
        onToggleTask = { line -> draft.body = toggleMarkdownTask(draft.body, line) }
      )
    }
  }
}

private fun openWikiLink(core: BerillCore, page: String) {
  val id = core.notes.findByTitle(page)
    ?: core.data.newId().also { core.notes.save(it, page.trim(), "") }
  core.nav.openNote(id)
}
