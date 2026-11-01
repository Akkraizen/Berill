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
import androidx.compose.material.icons.outlined.CenterFocusStrong
import androidx.compose.material.icons.outlined.CenterFocusWeak
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.unit.sp
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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.delay
import androidx.compose.runtime.rememberUpdatedState
import dev.akkraizen.berill.protocol.SyncRecord
import dev.akkraizen.berill.protocol.Collections
import dev.akkraizen.berill.core.connections.FailureOperation
import kotlinx.serialization.json.*

private val noteJson = Json { ignoreUnknownKeys = true; coerceInputValues = true; encodeDefaults = true }
private fun SyncRecord?.asEditorNote(): Note? = this?.takeUnless { it.deleted }?.let { noteJson.decodeFromJsonElement(Note.serializer(), it.fields) }
private class NoteDraft(initial: SyncRecord?) {
  val isNew = initial == null
  var baseline = initial
  var title by mutableStateOf(initial.asEditorNote()?.title.orEmpty())
  var body by mutableStateOf(initial.asEditorNote()?.body.orEmpty())
  var conflictId by mutableStateOf<String?>(null)
  var deleted = false
  fun dirty(): Boolean { val base = baseline.asEditorNote(); return title != base?.title.orEmpty() || body != base?.body.orEmpty() }
  fun accept(record: SyncRecord?) {
    baseline = record
    title = record.asEditorNote()?.title.orEmpty()
    body = record.asEditorNote()?.body.orEmpty()
    conflictId = null
  }
}

@OptIn(FlowPreview::class)
@Composable
internal fun NoteEditor(core: BerillCore, id: String, showBack: Boolean) {
  val preferences by core.editorPreferences.state.collectAsState()
  val focused by core.focusedNote.collectAsState()
  val focusMode = focused == id
  val notes = core.notes
  val draft = remember(id) { NoteDraft(core.store.get(Collections.NOTES, id)) }
  var confirmDelete by remember { mutableStateOf(false) }
  val titleFocus = remember { FocusRequester() }

  var editing by remember(id) { mutableStateOf(true) }

  val flush = rememberUpdatedState<() -> Unit> {
    if (!draft.deleted && (draft.dirty() || draft.conflictId != null)) {
      val desired = noteJson.encodeToJsonElement(Note.serializer(), Note(draft.title, draft.body, kotlin.time.Clock.System.now().toEpochMilliseconds())).jsonObject
      val saved = core.store.mergeDraft(Collections.NOTES, id, draft.baseline, desired, draft.conflictId)
      if (saved.conflictId == null) draft.accept(saved.record)
      else {
        draft.baseline = saved.baseline
        draft.conflictId = saved.conflictId
        if (!saved.deletion) {
          val current = saved.record.asEditorNote()
          if ("title" !in saved.fields) draft.title = current?.title.orEmpty()
          if ("body" !in saved.fields) draft.body = current?.body.orEmpty()
        }
      }
    }
  }
  LaunchedEffect(draft) {
    snapshotFlow { draft.title to draft.body }.drop(1).collectLatest {
      if (draft.conflictId == null) delay(500)
      core.connections.errors.run(FailureOperation.COMMAND) { flush.value() }
    }
  }
  LaunchedEffect(draft) {
    core.store.observe(Collections.NOTES).collect {
      val current = core.store.get(Collections.NOTES, id)
      if (!draft.deleted && !draft.dirty() && draft.conflictId == null) {
        if (current?.deleted == true) { draft.deleted = true; core.nav.back() }
        else draft.accept(current)
      }
    }
  }
  LaunchedEffect(draft) {
    core.store.conflicts.state.collect { values ->
      if (draft.conflictId != null && values.none { it.id == draft.conflictId }) {
        val current = core.store.get(Collections.NOTES, id)
        draft.accept(current)
        if (current?.deleted == true) { draft.deleted = true; core.nav.back() }
      }
    }
  }
  DisposableEffect(draft) {
    val detach = core.editorSessions.attach { core.connections.errors.run(FailureOperation.COMMAND) { flush.value() } }
    onDispose { detach() }
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
      if (showBack && !focusMode)
        IconButton(onClick = { core.nav.back() }) {
          Icon(BerillIcons.Back, contentDescription = CoreText.back())
        }
      Spacer(Modifier.weight(1f))
      IconButton(onClick = { core.focusedNote.value = if (focusMode) null else id }) {
        Icon(if (focusMode) Icons.Outlined.CenterFocusWeak else Icons.Outlined.CenterFocusStrong,
          dev.akkraizen.berill.api.i18n.I18n.text(if (focusMode) "editor.exitFocus" else "editor.focus"))
      }
      if (!focusMode) IconButton(onClick = { core.nav.openSettings("editor") }) { Icon(Icons.Outlined.Settings, dev.akkraizen.berill.api.i18n.I18n.text("editor.settings")) }
      IconButton(onClick = { editing = !editing }) {
        Icon(
          if (editing) Icons.Outlined.Visibility else BerillIcons.Edit,
          contentDescription = if (editing) CoreText.readMode() else CoreText.editMode()
        )
      }
      if (!focusMode) IconButton(onClick = { confirmDelete = true }) {
        Icon(BerillIcons.Delete, contentDescription = CoreText.deleteNote())
      }
    }
    if (draft.conflictId != null) Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
      Text(dev.akkraizen.berill.api.i18n.I18n.text("editor.concurrent"), Modifier.weight(1f), color = MaterialTheme.colorScheme.error)
      TextButton(onClick = { flush.value(); core.notifications.conflictsOpen.value = true }) { Text(dev.akkraizen.berill.api.i18n.I18n.text("center.conflicts")) }
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
          if (!focusMode) dev.akkraizen.berill.core.connections.Backlinks(core, id, draft.title)
          Spacer(Modifier.height(12.dp))
          MarkdownEditor(
            value = draft.body,
            onValueChange = { draft.body = it },
            placeholder = CoreText.bodyPlaceholder(),
            lineNumbers = preferences.lineNumbers,
            textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = preferences.fontSize.sp, fontFamily = if (preferences.monospace) androidx.compose.ui.text.font.FontFamily.Monospace else MaterialTheme.typography.bodyLarge.fontFamily),
            onWikiLink = { page -> openWikiLink(core, page) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 360.dp)
          )
        }
      } else {
        NotePreview(core, draft, id)
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
private fun NotePreview(core: BerillCore, draft: NoteDraft, id: String) {
  Column(
    Modifier.fillMaxSize()
      .mouseScrollable(rememberScrollState())
      .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    if (core.focusedNote.collectAsState().value != id) dev.akkraizen.berill.core.connections.Backlinks(core, id, draft.title)
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
