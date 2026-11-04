package dev.akkraizen.berill.api.ui.layout

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DragIndicator
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import dev.akkraizen.berill.api.PluginContext
import dev.akkraizen.berill.api.setStringList
import dev.akkraizen.berill.api.stringList
import dev.akkraizen.berill.api.ui.BerillIcons
import dev.akkraizen.berill.api.ui.UiText
import dev.akkraizen.berill.api.ui.display.Ic
import dev.akkraizen.berill.api.ui.interaction.interactive

class CollectionModes {
  var reordering by mutableStateOf(false)
  var selecting by mutableStateOf(false)
  val selected = mutableStateListOf<String>()
  val reorder = ReorderState()

  fun toggle(id: String) {
    selecting = true
    if (id in selected) selected.remove(id) else selected.add(id)
  }

  fun clear() {
    selected.clear()
    selecting = false
  }

  val idle: Boolean get() = !reordering && !selecting
}

@Composable
fun rememberCollectionModes(): CollectionModes = remember { CollectionModes() }

class BulkAction(val label: String, val icon: ImageVector, val destructive: Boolean = false, val choices: List<Pair<String, (List<String>) -> Unit>>? = null, val run: (List<String>) -> Unit = {})

class ManualOrder(val key: String) {
  fun stored(ctx: PluginContext): List<String> = ctx.settings.stringList(key).orEmpty()
  fun save(ctx: PluginContext, visible: List<String>) = ctx.settings.setStringList(key, mergeManualOrder(stored(ctx), visible))
}

@Composable
fun rememberManualOrder(ctx: PluginContext, order: ManualOrder): List<String> {
  val raw by remember(ctx, order.key) { ctx.settings.observe(order.key) }.collectAsState(ctx.settings.get(order.key))
  return (raw as? kotlinx.serialization.json.JsonArray)?.mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.content } ?: emptyList()
}

@Composable
fun CollectionModeMenuItems(modes: CollectionModes, canReorder: Boolean, close: () -> Unit) {
  DropdownMenuItem(
    text = { Text(UiText.selectMany()) },
    leadingIcon = { Ic(Icons.Outlined.CheckCircle, size = 20.dp) },
    onClick = { modes.reordering = false; modes.selecting = true; close() }
  )
  if (canReorder) DropdownMenuItem(
    text = { Text(UiText.reorder()) },
    leadingIcon = { Ic(Icons.Outlined.DragIndicator, size = 20.dp) },
    onClick = { modes.clear(); modes.reordering = true; close() }
  )
}

@Composable
fun CollectionModeBar(modes: CollectionModes, actions: List<BulkAction>, allIds: List<String>) {
  if (modes.idle) return
  Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
      if (modes.reordering) {
        Ic(Icons.Outlined.DragIndicator, size = 20.dp)
        Text(UiText.reorderHint(), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        TextButton(onClick = { modes.reordering = false }) { Text(UiText.done()) }
      } else {
        Text(UiText.selectedCount(modes.selected.size)(), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        TextButton(onClick = {
          if (modes.selected.size == allIds.size) modes.selected.clear()
          else { modes.selected.clear(); modes.selected.addAll(allIds) }
        }) { Text(if (modes.selected.size == allIds.size && allIds.isNotEmpty()) UiText.selectNone() else UiText.selectAll()) }
        actions.forEach { action -> BulkActionButton(action, modes) }
        IconButton(onClick = { modes.clear() }) { Icon(BerillIcons.Close, UiText.cancel()) }
      }
    }
  }
}

@Composable
private fun BulkActionButton(action: BulkAction, modes: CollectionModes) {
  var open by remember { mutableStateOf(false) }
  val enabled = modes.selected.isNotEmpty()
  val tint = if (action.destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSecondaryContainer
  Box {
    IconButton(enabled = enabled, onClick = {
      if (action.choices != null) open = true
      else { action.run(modes.selected.toList()); modes.clear() }
    }) { Icon(action.icon, action.label, tint = if (enabled) tint else tint.copy(alpha = 0.38f)) }
    if (action.choices != null) DropdownMenu(open, onDismissRequest = { open = false }) {
      action.choices.forEach { (label, pick) ->
        DropdownMenuItem(text = { Text(label) }, onClick = {
          pick(modes.selected.toList())
          open = false
          modes.clear()
        })
      }
    }
  }
}

@Composable
fun SelectableItem(modes: CollectionModes?, id: String, order: (() -> List<String>)? = null, onOrder: ((List<String>) -> Unit)? = null, content: @Composable () -> Unit) {
  if (modes == null || modes.idle) return content()
  if (modes.reordering && order != null && onOrder != null) {
    Box(Modifier.reorderable(modes.reorder, id, order, live = false, onOrder = onOrder)) {
      content()
      Box(Modifier.matchParentSize().pointerInput(Unit) {
        awaitPointerEventScope {
          while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { change -> if (!change.pressed && change.previousPressed) change.consume() }
        }
      })
      Ic(Icons.Outlined.DragIndicator, size = 20.dp, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.align(Alignment.TopEnd).padding(6.dp))
    }
    return
  }
  if (!modes.selecting) return content()
  val chosen = id in modes.selected
  Box(if (chosen) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.large) else Modifier) {
    content()
    Box(Modifier.matchParentSize().interactive(MaterialTheme.shapes.large) { modes.toggle(id) })
    Ic(
      if (chosen) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
      size = 22.dp,
      tint = if (chosen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(22.dp)
    )
  }
}
