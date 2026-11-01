package dev.akkraizen.berill.core.ui.settings

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.unit.dp
import dev.akkraizen.berill.api.i18n.I18n
import dev.akkraizen.berill.api.ui.cards.SectionCard
import dev.akkraizen.berill.api.ui.interaction.*
import dev.akkraizen.berill.core.BerillCore
import dev.akkraizen.berill.core.connections.FailureOperation

private fun ShortcutAction.title() = I18n.text("shortcuts.action.$name")

@Composable
internal fun ShortcutsSettings(core: BerillCore) {
  val profile by core.shortcuts.state.collectAsState()
  var recording by remember { mutableStateOf<ShortcutAction?>(null) }
  var resetConflict by remember { mutableStateOf<ShortcutAction?>(null) }
  LaunchedEffect(profile) { resetConflict = null }
  SectionCard(I18n.text("shortcuts.title"), Icons.Outlined.Keyboard) {
    Text(I18n.text("shortcuts.hint"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    ShortcutAction.entries.forEach { action ->
      Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(action.title(), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        FilledTonalButton(onClick = { recording = action }) { Text(profile.label(action) ?: I18n.text("shortcuts.none")) }
        IconButton(onClick = {
          resetConflict = action.defaultBinding?.let { profile.conflict(action, it) }
          if (resetConflict == null) core.connections.errors.run(FailureOperation.COMMAND) { core.shortcuts.reset(action) }
        }, enabled = action in profile.overrides) {
          Icon(Icons.Outlined.Restore, I18n.text("shortcuts.resetOne"))
        }
      }
    }
    resetConflict?.let { Text(I18n.text("shortcuts.conflict", "action" to it.title()), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    OutlinedButton(onClick = { core.connections.errors.run(FailureOperation.COMMAND) { core.shortcuts.resetAll() } }) { Text(I18n.text("shortcuts.resetAll")) }
  }
  recording?.let { action ->
    RecordShortcutDialog(core, profile, action, onDismiss = { recording = null })
  }
}

@Composable
private fun RecordShortcutDialog(core: BerillCore, profile: ShortcutProfile, action: ShortcutAction, onDismiss: () -> Unit) {
  var candidate by remember(action) { mutableStateOf<ShortcutChord?>(null) }
  var invalid by remember(action) { mutableStateOf(false) }
  val focus = remember { FocusRequester() }
  val conflict = candidate?.let { profile.conflict(action, it) }
  val global = core.desktop?.takeIf { it.isHotkeyEnabled() }?.hotkey()?.let(ShortcutChord::parseLabel)
  val globalConflict = candidate?.let { global?.overlaps(it) } == true
  val canSave = candidate?.valid == true && conflict == null && !globalConflict
  DisposableEffect(Unit) {
    ShortcutCapture.active = true
    onDispose { ShortcutCapture.active = false }
  }
  LaunchedEffect(Unit) { focus.requestFocus() }
  AlertDialog(onDismissRequest = onDismiss,
    icon = { Icon(Icons.Outlined.Keyboard, null) }, title = { Text(action.title()) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(I18n.text("shortcuts.recordHint"), style = MaterialTheme.typography.bodyMedium)
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.secondaryContainer,
          contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
          modifier = Modifier.fillMaxWidth().focusRequester(focus).onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) false
            else if (event.key == Key.Escape) { onDismiss(); true }
            else if (event.key == Key.Tab) false
            else { ShortcutChord.capture(event)?.let { candidate = it; invalid = !it.valid }; true }
          }.focusable()) {
          Box(Modifier.fillMaxWidth().heightIn(min = 80.dp).padding(16.dp), contentAlignment = Alignment.Center) {
            Text(candidate?.label() ?: I18n.text("shortcuts.press"), style = MaterialTheme.typography.titleMedium)
          }
        }
        val error = when {
          invalid -> I18n.text("shortcuts.invalid")
          conflict != null -> I18n.text("shortcuts.conflict", "action" to conflict.title())
          globalConflict -> I18n.text("shortcuts.globalConflict")
          else -> null
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
      }
    },
    confirmButton = { FilledTonalButton(enabled = canSave, onClick = {
      if (core.connections.errors.run(FailureOperation.COMMAND) { core.shortcuts.set(action, candidate) }) onDismiss()
    }) { Text(I18n.text("shortcuts.save")) } },
    dismissButton = { Row {
      TextButton(onClick = { if (core.connections.errors.run(FailureOperation.COMMAND) { core.shortcuts.set(action, null) }) onDismiss() }) { Text(I18n.text("shortcuts.disable")) }
      TextButton(onClick = onDismiss) { Text(I18n.text("ui.cancel")) }
    } })
}