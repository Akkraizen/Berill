package dev.akkraizen.berill.api.ui.layout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.ViewKanban
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.akkraizen.berill.api.PluginContext
import dev.akkraizen.berill.api.ui.UiText
import dev.akkraizen.berill.api.ui.display.Ic
import dev.akkraizen.berill.api.ui.selection.ChoiceChips
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

@kotlinx.serialization.Serializable
enum class ListLayout { BOARD, CARDS, ROWS }

val LocalListLayout = compositionLocalOf<ListLayout?> { null }

val LocalCompactLayout = compositionLocalOf { false }

fun listLayoutLabel(layout: ListLayout): String = when (layout) {
  ListLayout.BOARD -> UiText.layoutBoard()
  ListLayout.CARDS -> UiText.layoutCards()
  ListLayout.ROWS -> UiText.layoutRows()
}

fun listLayoutIcon(layout: ListLayout): ImageVector = when (layout) {
  ListLayout.BOARD -> Icons.Outlined.ViewKanban
  ListLayout.CARDS -> Icons.Outlined.GridView
  ListLayout.ROWS -> Icons.AutoMirrored.Outlined.ViewList
}

class ListLayoutKey(val key: String, val supported: List<ListLayout>, val fallback: ListLayout) {
  init {
    require(fallback in supported)
  }

  fun stored(ctx: PluginContext): ListLayout? =
    (ctx.settings.get(key) as? JsonPrimitive)?.contentOrNull?.let { value -> supported.firstOrNull { it.name == value } }

  fun resolve(stored: ListLayout?, global: ListLayout?): ListLayout =
    stored ?: global?.takeIf { it in supported } ?: fallback

  fun set(ctx: PluginContext, layout: ListLayout?) = ctx.settings.set(key, layout?.let { JsonPrimitive(it.name) } ?: JsonNull)
}

@Composable
fun rememberStoredListLayout(ctx: PluginContext, key: ListLayoutKey): ListLayout? {
  val raw by remember(ctx, key.key) { ctx.settings.observe(key.key) }.collectAsState(ctx.settings.get(key.key))
  return (raw as? JsonPrimitive)?.contentOrNull?.let { value -> key.supported.firstOrNull { it.name == value } }
}

@Composable
fun rememberListLayout(ctx: PluginContext, key: ListLayoutKey): ListLayout =
  key.resolve(rememberStoredListLayout(ctx, key), LocalListLayout.current)

@Composable
fun ListLayoutSetting(ctx: PluginContext, key: ListLayoutKey, title: String) {
  val stored = rememberStoredListLayout(ctx, key)
  Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Text(title, style = MaterialTheme.typography.titleSmall)
    ChoiceChips(
      listOf<ListLayout?>(null) + key.supported,
      stored,
      { it?.let(::listLayoutLabel) ?: UiText.layoutDefault() },
      { key.set(ctx, it) },
      icon = { it?.let(::listLayoutIcon) }
    )
  }
}

@Composable
fun ListLayoutSwitcher(ctx: PluginContext, key: ListLayoutKey, modes: CollectionModes? = null, canReorder: Boolean = true) {
  val current = rememberListLayout(ctx, key)
  var open by remember { mutableStateOf(false) }
  Box {
    IconButton(onClick = { open = true }) { Icon(listLayoutIcon(current), UiText.layoutChoose()) }
    DropdownMenu(open, onDismissRequest = { open = false }) {
      key.supported.forEach { layout ->
        DropdownMenuItem(
          text = { Text(listLayoutLabel(layout)) },
          leadingIcon = { Ic(listLayoutIcon(layout), size = 20.dp) },
          trailingIcon = if (layout == current) ({ Ic(dev.akkraizen.berill.api.ui.BerillIcons.Check, size = 18.dp) }) else null,
          onClick = {
            key.set(ctx, layout)
            open = false
          }
        )
      }
      if (modes != null) {
        androidx.compose.material3.HorizontalDivider()
        CollectionModeMenuItems(modes, canReorder && current != ListLayout.BOARD) { open = false }
      }
    }
  }
}

@Composable
fun <T> CardGrid(
  items: List<T>,
  modifier: Modifier = Modifier,
  minCardWidth: Dp = 220.dp,
  maxColumns: Int = 5,
  spacing: Dp = 12.dp,
  card: @Composable (T, Modifier) -> Unit
) {
  val gap = if (LocalCompactLayout.current) spacing * 0.6f else spacing
  BoxWithConstraints(modifier.fillMaxWidth()) {
    val columns = ((maxWidth + spacing) / (minCardWidth + spacing)).toInt().coerceIn(1, maxColumns)
    Column(verticalArrangement = Arrangement.spacedBy(gap)) {
      items.chunked(columns).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
          row.forEach { item -> card(item, Modifier.weight(1f)) }
          repeat(columns - row.size) { Box(Modifier.weight(1f)) }
        }
      }
    }
  }
}

class CollectionGroup<T>(val title: String, val icon: ImageVector?, val items: List<T>)

@Composable
fun <T> GroupedCollection(
  groups: List<CollectionGroup<T>>,
  layout: ListLayout,
  minCardWidth: Dp = 180.dp,
  maxColumns: Int = 6,
  emptyText: String? = null,
  id: ((T) -> String)? = null,
  modes: CollectionModes? = null,
  onReorder: ((List<String>) -> Unit)? = null,
  row: @Composable (T) -> Unit,
  card: @Composable (T, Modifier) -> Unit
) {
  val visible = groups.filter { it.items.isNotEmpty() }
  Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
    if (visible.isEmpty() && emptyText != null) dev.akkraizen.berill.api.ui.feedback.EmptyHint(emptyText)
    visible.forEach { group ->
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          group.icon?.let { Ic(it, size = 20.dp, tint = MaterialTheme.colorScheme.primary) }
          Text(group.title, style = MaterialTheme.typography.titleMedium)
          Text("${group.items.size}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val ids = id?.let { key -> group.items.map(key) }
        val latest = androidx.compose.runtime.rememberUpdatedState(ids.orEmpty())
        @Composable
        fun wrap(item: T, content: @Composable () -> Unit) {
          if (id == null || modes == null) content()
          else SelectableItem(modes, id(item), { latest.value }, onReorder) { content() }
        }
        if (layout == ListLayout.ROWS) RowList(group.items) { item -> wrap(item) { row(item) } }
        else CardGrid(group.items, minCardWidth = minCardWidth, maxColumns = maxColumns) { item, modifier ->
          Box(modifier) { wrap(item) { card(item, Modifier.fillMaxWidth()) } }
        }
      }
    }
  }
}

@Composable
fun <T> RowList(items: List<T>, row: @Composable (T) -> Unit) {
  androidx.compose.material3.Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer) {
    Column {
      items.forEachIndexed { index, item ->
        if (index > 0) androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        Box(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = if (LocalCompactLayout.current) 6.dp else 10.dp)) { row(item) }
      }
    }
  }
}

