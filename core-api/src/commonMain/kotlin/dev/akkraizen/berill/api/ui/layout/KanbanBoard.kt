package dev.akkraizen.berill.api.ui.layout

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.zIndex
import dev.akkraizen.berill.api.ui.interaction.dragAnywhere
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.akkraizen.berill.api.ui.*
import dev.akkraizen.berill.api.ui.animation.AppearAnimated
import dev.akkraizen.berill.api.ui.animation.BerillMotion
import dev.akkraizen.berill.api.ui.display.Ic
import dev.akkraizen.berill.api.ui.feedback.EmptyHint
import dev.akkraizen.berill.api.ui.feedback.color
import dev.akkraizen.berill.api.ui.selection.ChoiceChips

@Composable
fun <T> KanbanBoard(
  columns: List<KanbanColumn<T>>,
  itemKey: (T) -> String,
  onMove: (item: T, toColumn: String) -> Unit,
  card: @Composable (T) -> Unit
) {
  if (columns.isEmpty()) return
  BoxWithConstraints(Modifier.fillMaxWidth()) {
    if (maxWidth >= 720.dp) {
      val drag = remember { KanbanDrag() }
      val bounds = remember { mutableStateMapOf<Int, Rect>() }
      val target = drag.key?.let { bounds.entries.firstOrNull { (_, rect) -> drag.pointer.x in rect.left..rect.right }?.key }
      Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        columns.forEachIndexed { index, column ->
          val highlight by animateColorAsState(
            if (target == index && index != drag.from) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f) else Color.Transparent,
            label = "kanban-drop"
          )
          Column(
            Modifier
              .weight(1f)
              .zIndex(if (drag.from == index) 1f else 0f)
              .onGloballyPositioned { bounds[index] = it.boundsInRoot() }
              .background(highlight, MaterialTheme.shapes.large)
              .then(if (drag.key == null) Modifier.animateContentSize(BerillMotion.spatial()) else Modifier),
            verticalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            KanbanColumnBody(columns, index, itemKey, onMove, card, drag = drag, onDrop = { item ->
              val to = bounds.entries.firstOrNull { (_, rect) -> drag.pointer.x in rect.left..rect.right }?.key
              if (to != null && to != index) onMove(item, columns[to].key)
            })
          }
        }
      }
    } else {
      var selected by rememberSaveable { mutableStateOf(0) }
      val index = selected.coerceIn(0, columns.lastIndex)
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ChoiceChips(
          options = columns.indices.toList(),
          selected = index,
          label = { "${columns[it].title} · ${columns[it].items.size}" },
          onSelect = { selected = it },
          icon = { columns[it].icon }
        )
        AnimatedContent(
          targetState = index,
          transitionSpec = { BerillMotion.slide(forward = targetState > initialState) },
          label = "kanban-column"
        ) { shown ->
          Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            KanbanColumnBody(
              columns,
              shown.coerceIn(0, columns.lastIndex),
              itemKey,
              onMove,
              card,
              showHeader = false
            )
          }
        }
      }
    }
  }
}

@Composable
private fun <T> KanbanColumnBody(
  columns: List<KanbanColumn<T>>,
  index: Int,
  itemKey: (T) -> String,
  onMove: (T, String) -> Unit,
  card: @Composable (T) -> Unit,
  showHeader: Boolean = true,
  drag: KanbanDrag? = null,
  onDrop: (T) -> Unit = {}
) {
  val column = columns[index]
  if (showHeader)
    Row(
      Modifier.fillMaxWidth().padding(horizontal = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      column.icon?.let { Ic(it, tint = MaterialTheme.colorScheme.primary) }
      Text(
        column.title,
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.weight(1f)
      )
      Text(
        "${column.items.size}",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary
      )
    }
  if (column.items.isEmpty()) EmptyHint(column.emptyText, Modifier.padding(horizontal = 4.dp))
  column.items.forEach { item ->
    key(itemKey(item)) {
      val id = itemKey(item)
      var origin by remember { mutableStateOf(Offset.Zero) }
      val dragged = drag != null && drag.key == id
      val elevation by animateDpAsState(if (dragged) 12.dp else 0.dp, label = "kanban-lift")
      val moves = columns.indices.filter { it != index }.map { to ->
        CustomAccessibilityAction(columns[to].title) { onMove(item, columns[to].key); true }
      }
      AppearAnimated {
        Surface(
          shape = MaterialTheme.shapes.large,
          color = MaterialTheme.colorScheme.surfaceContainerHighest,
          shadowElevation = elevation,
          modifier = Modifier
            .fillMaxWidth()
            .zIndex(if (dragged) 1f else 0f)
            .onGloballyPositioned { origin = it.positionInRoot() }
            .graphicsLayer {
              if (dragged) {
                translationX = drag!!.offset.x
                translationY = drag.offset.y
                rotationZ = 1.5f
              }
            }
            .semantics { customActions = moves }
            .let { base ->
              if (drag == null) base
              else base.dragAnywhere(
                id,
                onStart = { drag.start(id, index, origin + it) },
                onMove = { drag.move(it) },
                onEnd = {
                  onDrop(item)
                  drag.reset()
                },
                onCancel = { drag.reset() }
              )
            }
        ) {
          Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (drag != null) card(item)
            else {
              card(item)
              Row(Modifier.fillMaxWidth()) {
                if (index > 0)
                  TextButton(onClick = { onMove(item, columns[index - 1].key) }) {
                    Ic(BerillIcons.Prev, size = 16.dp)
                    Text(
                      columns[index - 1].title,
                      style = MaterialTheme.typography.labelSmall,
                      maxLines = 1
                    )
                  }
                Spacer(Modifier.weight(1f))
                if (index < columns.lastIndex)
                  TextButton(onClick = { onMove(item, columns[index + 1].key) }) {
                    Text(
                      columns[index + 1].title,
                      style = MaterialTheme.typography.labelSmall,
                      maxLines = 1
                    )
                    Ic(BerillIcons.Next, size = 16.dp)
                  }
              }
            }
          }
        }
      }
    }
  }
}

internal class KanbanDrag {
  var key by mutableStateOf<String?>(null)
    private set
  var from by mutableStateOf(-1)
    private set
  var offset by mutableStateOf(Offset.Zero)
    private set
  var pointer by mutableStateOf(Offset.Zero)
    private set

  fun start(id: String, column: Int, at: Offset) {
    key = id
    from = column
    offset = Offset.Zero
    pointer = at
  }

  fun move(amount: Offset) {
    offset += amount
    pointer += amount
  }

  fun reset() {
    key = null
    from = -1
    offset = Offset.Zero
  }
}
