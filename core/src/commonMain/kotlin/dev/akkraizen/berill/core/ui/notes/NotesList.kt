package dev.akkraizen.berill.core.ui.notes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.key
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.akkraizen.berill.api.Item
import dev.akkraizen.berill.api.ui.BerillIcons
import dev.akkraizen.berill.api.ui.ddMMyyyy
import dev.akkraizen.berill.api.ui.hhmm
import dev.akkraizen.berill.api.ui.interaction.BerillTextField
import dev.akkraizen.berill.api.ui.interaction.interactive
import dev.akkraizen.berill.api.ui.layout.AutoScrollLazyColumn as LazyColumn
import dev.akkraizen.berill.api.ui.localDateTime
import dev.akkraizen.berill.api.ui.state.rememberDraft
import dev.akkraizen.berill.core.BerillCore
import dev.akkraizen.berill.core.notes.*
import dev.akkraizen.berill.core.ui.CoreText

@Composable
internal fun NotesList(core: BerillCore, openedId: String?) {
  val notes by core.notes.observe().collectAsState(emptyList())
  var query by rememberDraft("notes/list/query")
  val shown = remember(notes, query) {
    val q = query.trim()
    if (q.isEmpty())
      notes
    else
      notes.filter {
        it.value.title.contains(q, ignoreCase = true) ||
          it.value.body.contains(q, ignoreCase = true)
      }
  }

  Box(Modifier.fillMaxSize()) {
    LazyColumn(
      contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 96.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      item { Text(CoreText.notes(), style = MaterialTheme.typography.headlineMedium) }
      item {
        BerillTextField(
          query,
          { query = it },
          placeholder = { Text(CoreText.search()) },
          singleLine = true,
          shape = MaterialTheme.shapes.extraLarge,
          modifier = Modifier.fillMaxWidth()
        )
      }
      if (shown.isEmpty())
        item {
          Text(
            if (notes.isEmpty()) CoreText.noNotesYet() else CoreText.nothingFound(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 8.dp)
          )
        }
      items(shown, key = { it.id }) { item ->
        Box(Modifier.animateItem()) {
          NoteCard(item, selected = item.id == openedId, onClick = { core.nav.openNote(item.id) })
        }
      }
    }
    ExtendedFloatingActionButton(
      onClick = { core.nav.openNote(null) },
      icon = { Icon(BerillIcons.Add, contentDescription = null) },
      text = { Text(CoreText.noteFab()) },
      modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
    )
  }
}

@Composable
private fun NoteCard(item: Item<Note>, selected: Boolean, onClick: () -> Unit) {
  val note = item.value
  Surface(
    shape = MaterialTheme.shapes.large,
    color = if (selected)
      MaterialTheme.colorScheme.secondaryContainer
    else
      MaterialTheme.colorScheme.surfaceContainerHigh,
    modifier = Modifier.fillMaxWidth().interactive(MaterialTheme.shapes.large, onClick = onClick)
  ) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
      Text(
        note.displayTitle(),
        style = MaterialTheme.typography.titleMedium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
      val preview = note.preview()
      if (preview.isNotEmpty())
        Text(
          preview,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis
        )
      if (note.updatedAt > 0) {
        val t = localDateTime(note.updatedAt)
        Text(
          "${t.date.ddMMyyyy()} ${t.hhmm()}",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    }
  }
}
