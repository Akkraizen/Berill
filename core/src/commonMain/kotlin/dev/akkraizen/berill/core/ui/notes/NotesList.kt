package dev.akkraizen.berill.core.ui.notes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.FormatColorReset
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.akkraizen.berill.api.Item
import dev.akkraizen.berill.api.ui.BerillIcons
import dev.akkraizen.berill.api.ui.ddMMyyyy
import dev.akkraizen.berill.api.ui.display.Ic
import dev.akkraizen.berill.api.ui.hhmm
import dev.akkraizen.berill.api.ui.interaction.BerillTextField
import dev.akkraizen.berill.api.ui.interaction.interactive
import dev.akkraizen.berill.api.ui.interaction.mouseAutoScroll
import dev.akkraizen.berill.api.ui.layout.ListLayout
import dev.akkraizen.berill.api.ui.layout.LocalCompactLayout
import dev.akkraizen.berill.api.ui.layout.LocalListLayout
import dev.akkraizen.berill.api.ui.localDateTime
import dev.akkraizen.berill.api.ui.state.rememberDraft
import dev.akkraizen.berill.core.BerillCore
import dev.akkraizen.berill.core.notes.*
import dev.akkraizen.berill.core.preferences.NoteSort
import dev.akkraizen.berill.core.ui.CoreText

@Composable
internal fun NotesList(core: BerillCore, openedId: String?) {
  val notes by core.notes.observe().collectAsState(emptyList())
  val view by core.notesView.state.collectAsState()
  var query by rememberDraft("notes/list/query")
  var tag by rememberDraft("notes/list/tag")
  var templates by remember { mutableStateOf(false) }
  val tags = remember(notes) { notes.flatMap { it.value.tags() }.groupingBy { it }.eachCount().entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key }).map { it.key } }
  val modes = dev.akkraizen.berill.api.ui.layout.rememberCollectionModes()
  val shown = remember(notes, query, tag, view.sort, view.manualOrder) {
    val terms = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
    notes
      .filter { note -> tag.isEmpty() || tag in note.value.tags() }
      .filter { note -> terms.all { it in (note.value.title + "\n" + note.value.body).lowercase() } }
      .let { list -> if (view.sort == NoteSort.MANUAL) dev.akkraizen.berill.api.ui.layout.applyManualOrder(sortNotes(list, NoteSort.UPDATED), view.manualOrder) { it.id } else sortNotes(list, view.sort) }
  }
  val pinned = shown.filter { it.value.pinned }
  val others = shown.filterNot { it.value.pinned }

  Box(Modifier.fillMaxSize()) {
    val rows = LocalListLayout.current == ListLayout.ROWS
    val compact = LocalCompactLayout.current
    val gap = if (compact) 6.dp else 10.dp
    val state = rememberLazyGridState()
    LazyVerticalGrid(
      columns = if (rows) GridCells.Fixed(1) else GridCells.Adaptive(160.dp),
      state = state,
      modifier = Modifier.mouseAutoScroll(state),
      contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 96.dp),
      horizontalArrangement = Arrangement.spacedBy(gap),
      verticalArrangement = Arrangement.spacedBy(gap)
    ) {
      item(key = "title", span = { GridItemSpan(maxLineSpan) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(CoreText.notes(), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
          IconButton(onClick = { templates = true }) { Icon(Icons.Outlined.ContentCopy, CoreText.noteTemplates()) }
          SortMenu(view.sort, modes) { core.notesView.set(core.notesView.state.value.copy(sort = it)) }
        }
      }
      item(key = "search", span = { GridItemSpan(maxLineSpan) }) {
        BerillTextField(
          query,
          { query = it },
          placeholder = { Text(CoreText.search(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
          singleLine = true,
          shape = MaterialTheme.shapes.extraLarge,
          modifier = Modifier.fillMaxWidth()
        )
      }
      if (!modes.idle)
        item(key = "modes", span = { GridItemSpan(maxLineSpan) }) {
          dev.akkraizen.berill.api.ui.layout.CollectionModeBar(
            modes,
            listOf(
              dev.akkraizen.berill.api.ui.layout.BulkAction(CoreText.pinNote(), Icons.Outlined.PushPin) { ids ->
                val pin = ids.any { id -> core.notes.get(id)?.pinned != true }
                ids.forEach { core.notes.setPinned(it, pin) }
              },
              dev.akkraizen.berill.api.ui.layout.BulkAction(
                CoreText.noteColor(), Icons.Outlined.Palette,
                choices = (listOf<String?>(null) + NOTE_COLORS).map { name -> colorLabel(name) to { ids: List<String> -> ids.forEach { core.notes.setColor(it, name) } } }
              ),
              dev.akkraizen.berill.api.ui.layout.BulkAction(dev.akkraizen.berill.api.ui.UiText.bulkDelete(), BerillIcons.Delete, destructive = true) { ids -> ids.forEach(core.notes::delete) }
            ),
            shown.map { it.id }
          )
        }
      if (tags.isNotEmpty())
        item(key = "tags", span = { GridItemSpan(maxLineSpan) }) {
          Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(tag.isEmpty(), { tag = "" }, label = { Text(CoreText.allNotes()) })
            tags.forEach { name -> FilterChip(tag == name, { tag = if (tag == name) "" else name }, label = { Text("#$name") }) }
          }
        }
      if (shown.isEmpty())
        item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
          Text(
            if (notes.isEmpty()) CoreText.noNotesYet() else CoreText.nothingFound(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 8.dp)
          )
        }
      val split = pinned.isNotEmpty() && others.isNotEmpty()
      if (pinned.isNotEmpty()) {
        if (split) header("pinned", CoreText.pinnedNotes())
        cards(core, pinned, openedId, !rows, modes)
      }
      if (others.isNotEmpty()) {
        if (split) header("others", CoreText.otherNotes())
        cards(core, others, openedId, !rows, modes)
      }
    }
    ExtendedFloatingActionButton(
      onClick = { core.nav.openNote(null) },
      icon = { Icon(BerillIcons.Add, contentDescription = null) },
      text = { Text(CoreText.noteFab()) },
      modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
    )
  }
  if (templates) NoteTemplatesDialog(core, notes, onDismiss = { templates = false })
}

private fun LazyGridScope.header(key: String, title: String) {
  item(key = "header/$key", span = { GridItemSpan(maxLineSpan) }) {
    Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
  }
}

private fun LazyGridScope.cards(core: BerillCore, list: List<Item<Note>>, openedId: String?, grid: Boolean, modes: dev.akkraizen.berill.api.ui.layout.CollectionModes) {
  val ids = list.map { it.id }
  items(list, key = { it.id }) { item ->
    Box(if (modes.reorder.dragging == null) Modifier.animateItem() else Modifier) {
      dev.akkraizen.berill.api.ui.layout.SelectableItem(modes, item.id, { ids }, { order ->
        val view = core.notesView.state.value
        core.notesView.set(view.copy(manualOrder = dev.akkraizen.berill.api.ui.layout.mergeManualOrder(view.manualOrder.ifEmpty { core.notes.all().map { it.id } }, order)))
      }) {
        dev.akkraizen.berill.api.ui.interaction.SwipeActions(
          start = dev.akkraizen.berill.api.ui.interaction.RowSwipeAction(
            if (item.value.pinned) dev.akkraizen.berill.api.ui.UiText.swipeUnpin() else dev.akkraizen.berill.api.ui.UiText.swipePin(),
            Icons.Outlined.PushPin
          ) { core.notes.setPinned(item.id, !item.value.pinned) },
          end = dev.akkraizen.berill.api.ui.interaction.RowSwipeAction(CoreText.delete(), BerillIcons.Delete, destructive = true) { core.notes.delete(item.id) },
          enabled = modes.idle,
          shape = MaterialTheme.shapes.large
        ) {
          NoteCard(core, item, selected = item.id == openedId, grid = grid, onClick = { core.nav.openNote(item.id) })
        }
      }
    }
  }
}

private fun colorLabel(name: String?): String = name?.let { dev.akkraizen.berill.api.i18n.I18n.text("notes.color.$it") } ?: CoreText.noColor()

internal fun sortNotes(list: List<Item<Note>>, sort: NoteSort): List<Item<Note>> = when (sort) {
  NoteSort.UPDATED -> list.sortedByDescending { it.value.updatedAt }
  NoteSort.TITLE -> list.sortedBy { it.value.displayTitle().lowercase() }
  NoteSort.MANUAL -> list
  NoteSort.COLOR -> list.sortedWith(compareBy<Item<Note>> { NOTE_COLORS.indexOf(it.value.color).let { i -> if (i < 0) Int.MAX_VALUE else i } }.thenByDescending { it.value.updatedAt })
}

private fun sortLabel(sort: NoteSort): String = when (sort) {
  NoteSort.UPDATED -> CoreText.sortUpdated()
  NoteSort.TITLE -> CoreText.sortTitle()
  NoteSort.COLOR -> CoreText.sortColor()
  NoteSort.MANUAL -> CoreText.sortManual()
}

@Composable
private fun SortMenu(current: NoteSort, modes: dev.akkraizen.berill.api.ui.layout.CollectionModes, onChange: (NoteSort) -> Unit) {
  var open by remember { mutableStateOf(false) }
  Box {
    IconButton(onClick = { open = true }) { Icon(Icons.AutoMirrored.Outlined.Sort, CoreText.sortNotes()) }
    DropdownMenu(open, onDismissRequest = { open = false }) {
      NoteSort.entries.forEach { sort ->
        DropdownMenuItem(
          text = { Text(sortLabel(sort)) },
          trailingIcon = if (sort == current) ({ Ic(BerillIcons.Check, size = 18.dp) }) else null,
          onClick = { onChange(sort); open = false }
        )
      }
      HorizontalDivider()
      dev.akkraizen.berill.api.ui.layout.CollectionModeMenuItems(modes, current == NoteSort.MANUAL) { open = false }
    }
  }
}

internal fun noteHue(name: String?): Color? = when (name) {
  "red" -> Color(0xFFE5534B)
  "orange" -> Color(0xFFF0883E)
  "yellow" -> Color(0xFFE3B341)
  "green" -> Color(0xFF57AB5A)
  "teal" -> Color(0xFF39C5BB)
  "blue" -> Color(0xFF539BF5)
  "purple" -> Color(0xFF986EE2)
  "pink" -> Color(0xFFE275AD)
  else -> null
}

@Composable
internal fun noteBackground(name: String?, selected: Boolean): Color {
  val base = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
  return noteHue(name)?.let { lerp(base, it, if (selected) 0.32f else 0.22f) } ?: base
}

@Composable
internal fun NoteColorRow(current: String?, onPick: (String?) -> Unit) {
  Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
    Box(
      Modifier.size(26.dp).clip(CircleShape).border(1.dp, MaterialTheme.colorScheme.outline, CircleShape).interactive(CircleShape) { onPick(null) },
      contentAlignment = Alignment.Center
    ) { Ic(Icons.Outlined.FormatColorReset, size = 16.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
    NOTE_COLORS.forEach { name ->
      Box(
        Modifier.size(26.dp).clip(CircleShape).background(noteHue(name)!!)
          .then(if (current == name) Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
          .interactive(CircleShape) { onPick(name) }
      )
    }
  }
}

@Composable
private fun NoteCard(core: BerillCore, item: Item<Note>, selected: Boolean, grid: Boolean, onClick: () -> Unit) {
  val note = item.value
  var menu by remember { mutableStateOf(false) }
  val compact = LocalCompactLayout.current
  Box {
    Surface(
      shape = MaterialTheme.shapes.large,
      color = noteBackground(note.color, selected),
      modifier = Modifier.fillMaxWidth().then(if (grid) Modifier.height(if (compact) NOTE_CARD_HEIGHT_COMPACT else NOTE_CARD_HEIGHT) else Modifier)
        .interactive(MaterialTheme.shapes.large, onLongClick = { menu = true }, onClick = onClick)
    ) {
      Row(if (grid) Modifier.fillMaxSize() else Modifier.height(IntrinsicSize.Min)) {
        noteHue(note.color)?.let { hue -> Box(Modifier.width(4.dp).fillMaxHeight().background(hue)) }
        Column(Modifier.then(if (grid) Modifier.fillMaxSize() else Modifier).padding(if (compact) 12.dp else 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
              note.displayTitle(),
              style = MaterialTheme.typography.titleMedium,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              modifier = Modifier.weight(1f)
            )
            if (note.pinned) Ic(Icons.Outlined.PushPin, size = 16.dp, tint = MaterialTheme.colorScheme.primary)
          }
          val preview = note.preview()
          if (preview.isNotEmpty())
            Text(
              preview,
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = if (grid) (if (compact) 3 else 4) else 2,
              overflow = TextOverflow.Ellipsis
            )
          if (grid) Spacer(Modifier.weight(1f))
          if (note.updatedAt > 0) {
            val t = localDateTime(note.updatedAt)
            Text(
              "${t.date.ddMMyyyy()} ${t.hhmm()}",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 1
            )
          }
        }
      }
    }
    DropdownMenu(menu, onDismissRequest = { menu = false }) {
      DropdownMenuItem(
        text = { Text(if (note.pinned) CoreText.unpinNote() else CoreText.pinNote()) },
        leadingIcon = { Ic(Icons.Outlined.PushPin, size = 20.dp) },
        onClick = { core.notes.setPinned(item.id, !note.pinned); menu = false }
      )
      HorizontalDivider()
      NoteColorRow(note.color) { core.notes.setColor(item.id, it); menu = false }
    }
  }
}

private val NOTE_CARD_HEIGHT = 168.dp
private val NOTE_CARD_HEIGHT_COMPACT = 136.dp
