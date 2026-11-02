package dev.akkraizen.berill.core.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import dev.akkraizen.berill.api.i18n.I18n
import dev.akkraizen.berill.core.BerillCore
import dev.akkraizen.berill.core.ui.home.HomeScreen

@Composable
internal fun HomeSettings(core: BerillCore, modifier: Modifier = Modifier) {
  Box(modifier.fillMaxSize()) { HomeScreen(core, editing = true) }
}

@Composable
internal fun EditorSettings(core: BerillCore) {
  val prefs by core.editorPreferences.state.collectAsState()
  SettingSwitch(I18n.text("editor.lineNumbers"), null, prefs.lineNumbers) { core.editorPreferences.set(prefs.copy(lineNumbers = it)) }
  SettingSwitch(I18n.text("editor.monospace"), null, prefs.monospace) { core.editorPreferences.set(prefs.copy(monospace = it)) }
  Text(I18n.text("editor.fontSize", "size" to prefs.fontSize))
  Slider(value = prefs.fontSize.toFloat(), onValueChange = { core.editorPreferences.set(prefs.copy(fontSize = it.toInt().coerceIn(12, 28))) }, valueRange = 12f..28f, steps = 15)
  TextButton(onClick = { core.editorPreferences.reset() }) { Text(I18n.text("workspace.reset")) }
  // Synced between devices, unlike the editor appearance above.
  val template = remember { core.dailyNotes.template() }
  dev.akkraizen.berill.api.ui.fields.CommitTextField(
    value = template,
    onCommit = { core.dailyNotes.setTemplate(it) },
    label = I18n.text("editor.dailyTemplate"),
    modifier = Modifier.fillMaxWidth(),
    singleLine = false,
    normalize = { it.trimEnd() },
  )
  Text(I18n.text("editor.dailyTemplateHint"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
