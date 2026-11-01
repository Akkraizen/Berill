package dev.akkraizen.berill.core.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import dev.akkraizen.berill.api.i18n.I18n
import dev.akkraizen.berill.core.BerillCore
import dev.akkraizen.berill.core.preferences.*
import dev.akkraizen.berill.core.ui.CoreText

@Composable
internal fun HomeSettings(core: BerillCore) {
  val prefs by core.homePreferences.state.collectAsState()
  val plugins by core.plugins.enabled.collectAsState()
  val cards = listOf("core/notes" to CoreText.recentNotes()) + plugins.flatMap { p -> p.homeCards(core.plugins.context(p)).map { "${p.id}/${it.id}" to it.title() } }
  val actions = listOf("core/new-note" to CoreText.newNote()) + plugins.flatMap { p -> p.quickActions(core.plugins.context(p)).map { "${p.id}/${it.id}" to it.label() } }
  SettingSwitch(I18n.text("workspace.greeting"), null, prefs.greeting) { core.homePreferences.set(prefs.copy(greeting = it)) }
  Text(I18n.text("workspace.cards"), style = MaterialTheme.typography.titleMedium)
  OrderedOptions(cards, prefs.cardOrder, prefs.hiddenCards, { core.homePreferences.set(prefs.copy(cardOrder = it)) }, { id, visible -> core.homePreferences.set(prefs.copy(hiddenCards = if (visible) prefs.hiddenCards - id else prefs.hiddenCards + id)) })
  Text(I18n.text("workspace.actions"), style = MaterialTheme.typography.titleMedium)
  OrderedOptions(actions, prefs.actionOrder, prefs.hiddenActions, { core.homePreferences.set(prefs.copy(actionOrder = it)) }, { id, visible -> core.homePreferences.set(prefs.copy(hiddenActions = if (visible) prefs.hiddenActions - id else prefs.hiddenActions + id)) })
  TextButton(onClick = { core.homePreferences.reset() }) { Text(I18n.text("workspace.reset")) }
}

@Composable
private fun OrderedOptions(items: List<Pair<String, String>>, preferred: List<String>, hidden: Set<String>, onOrder: (List<String>) -> Unit, onVisible: (String, Boolean) -> Unit) {
  val order = orderedKeys(items.map { it.first }, preferred)
  dev.akkraizen.berill.api.ui.layout.BoundedList {
    order.forEachIndexed { index, id ->
      Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Checkbox(checked = id !in hidden, onCheckedChange = { onVisible(id, it) })
        Text(items.first { it.first == id }.second, Modifier.weight(1f))
        IconButton(enabled = index > 0, onClick = { onOrder(mergeOrder(preferred, moveKey(order, id, -1))) }) { Icon(Icons.Outlined.ArrowUpward, I18n.text("workspace.up")) }
        IconButton(enabled = index < order.lastIndex, onClick = { onOrder(mergeOrder(preferred, moveKey(order, id, 1))) }) { Icon(Icons.Outlined.ArrowDownward, I18n.text("workspace.down")) }
      }
    }
  }
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
