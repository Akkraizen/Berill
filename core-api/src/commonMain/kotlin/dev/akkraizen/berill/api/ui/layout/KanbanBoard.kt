package dev.akkraizen.berill.api.ui.layout

import androidx.compose.animation.AnimatedContent
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
      Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        columns.forEachIndexed { index, column ->
          Column(
            Modifier.weight(1f).animateContentSize(BerillMotion.spatial()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            KanbanColumnBody(columns, index, itemKey, onMove, card)
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
  showHeader: Boolean = true
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
      AppearAnimated {
        Surface(
          shape = MaterialTheme.shapes.large,
          color = MaterialTheme.colorScheme.surfaceContainerHighest,
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
