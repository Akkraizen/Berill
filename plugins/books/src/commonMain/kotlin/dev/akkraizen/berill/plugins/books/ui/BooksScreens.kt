package dev.akkraizen.berill.plugins.books.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.akkraizen.berill.api.Item
import dev.akkraizen.berill.api.PluginContext
import dev.akkraizen.berill.api.migrateSecretFromSettings
import dev.akkraizen.berill.api.ui.BerillIcons
import dev.akkraizen.berill.api.ui.cards.SectionCard
import dev.akkraizen.berill.api.ui.display.Ic
import dev.akkraizen.berill.api.ui.display.Stars
import dev.akkraizen.berill.api.ui.fields.CommitTextField
import dev.akkraizen.berill.api.ui.interaction.BerillTextField
import dev.akkraizen.berill.api.ui.interaction.DialogKeys
import dev.akkraizen.berill.api.ui.interaction.interactive
import dev.akkraizen.berill.api.ui.layout.KanbanBoard
import dev.akkraizen.berill.api.ui.layout.KanbanColumn
import dev.akkraizen.berill.api.ui.layout.PluginList
import dev.akkraizen.berill.api.ui.media.Cover
import dev.akkraizen.berill.api.ui.selection.ChoiceChips
import dev.akkraizen.berill.plugins.books.BooksPlugin
import dev.akkraizen.berill.plugins.books.data.Book
import dev.akkraizen.berill.plugins.books.data.DONE
import dev.akkraizen.berill.plugins.books.data.GOOGLE_BOOKS_KEY
import dev.akkraizen.berill.plugins.books.data.NOT_STARTED
import dev.akkraizen.berill.plugins.books.data.READING
import dev.akkraizen.berill.plugins.books.data.STATUSES
import dev.akkraizen.berill.plugins.books.data.books
import dev.akkraizen.berill.plugins.books.i18n.BooksText

internal val ICONS: Map<String, ImageVector> = mapOf(
  NOT_STARTED to Icons.Outlined.BookmarkBorder,
  READING to Icons.Outlined.MenuBook,
  DONE to Icons.Outlined.TaskAlt
)

@Composable
internal fun BooksBoard(ctx: PluginContext) {
  val collection = remember(ctx) { books(ctx) }
  val all by remember(collection) { collection.observe() }.collectAsState(emptyList())
  var adding by remember { mutableStateOf(false) }
  var searching by rememberSaveable { mutableStateOf(false) }

  val byStatus = all.groupBy { it.value.normalizedStatus }
  val notStarted = byStatus[NOT_STARTED].orEmpty().sortedBy { it.value.title.lowercase() }
  val reading = byStatus[READING].orEmpty().sortedByDescending { it.value.started ?: "" }
  val done = byStatus[DONE].orEmpty().sortedByDescending { it.value.finished ?: "" }

  PluginList {
    item { Text(BooksText.board(), style = MaterialTheme.typography.titleLarge) }
    item {
      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        FilledTonalButton(onClick = { searching = !searching }) {
          Ic(Icons.Outlined.Search, size = 18.dp)
          Spacer(Modifier.width(8.dp))
          Text(BooksText.findBook())
        }
        OutlinedButton(onClick = { adding = true }) {
          Ic(BerillIcons.Add, size = 18.dp)
          Spacer(Modifier.width(8.dp))
          Text(BooksText.manually())
        }
        IconButton(onClick = { ctx.nav.openSettings() }) {
          Icon(Icons.Outlined.Settings, contentDescription = BooksText.sectionSettings())
        }
      }
    }
    if (searching) item { BookSearch(ctx) { searching = false } }
    item {
      KanbanBoard(
        columns = listOf(
          KanbanColumn(
            NOT_STARTED,
            BooksText.statusLabel(NOT_STARTED),
            notStarted,
            BooksText.noBooks(),
            ICONS[NOT_STARTED]
          ),
          KanbanColumn(
            READING,
            BooksText.statusLabel(READING),
            reading,
            BooksText.noBooks(),
            ICONS[READING]
          ),
          KanbanColumn(
            DONE,
            BooksText.statusLabel(DONE),
            done,
            BooksText.noBooks(),
            ICONS[DONE]
          )
        ),
        itemKey = { it.id },
        onMove = { item, status ->
          collection
            .get(item.id)
            ?.takeIf { it.normalizedStatus != status }
            ?.let { collection.save(item.id, it.moveTo(status)) }
        },
        card = { item -> BookCard(item) { ctx.nav.open(BooksPlugin.BOOK_ROUTE + item.id) } }
      )
    }
    item { BooksStatistics(all.map { it.value }) }
  }

  if (adding)
    AddBookDialog(
      onDismiss = { adding = false },
      onAdd = { book ->
        val id = ctx.data.newId()
        collection.save(id, book.moveTo(book.status))
        adding = false
      }
    )
}

@Composable
private fun BookCard(item: Item<Book>, onOpen: () -> Unit) {
  val b = item.value
  Row(
    Modifier.fillMaxWidth().interactive(bleed = 6.dp, onClick = onOpen),
    horizontalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    Cover(b.cover, Icons.Outlined.MenuBook, Modifier.size(48.dp, 72.dp))
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Text(
        b.title,
        style = MaterialTheme.typography.titleSmall,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis
      )
      if (!b.author.isNullOrBlank())
        Text(
          b.author,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 2
        )
      Stars(b.rating)
    }
  }
}

@Composable
private fun AddBookDialog(onDismiss: () -> Unit, onAdd: (Book) -> Unit) {
  var title by remember { mutableStateOf("") }
  var author by remember { mutableStateOf("") }
  var status by remember { mutableStateOf(NOT_STARTED) }
  val confirm: () -> Unit = {
    onAdd(Book(title = title.trim(), author = author.trim().ifEmpty { null }, status = status))
  }
  DialogKeys(onConfirm = confirm, confirmEnabled = title.isNotBlank(), onDismiss = onDismiss)
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(BooksText.newBook()) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BerillTextField(
          title,
          { title = it },
          label = { Text(BooksText.name(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
          singleLine = true
        )
        BerillTextField(
          author,
          { author = it },
          label = { Text(BooksText.author(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
          singleLine = true
        )
        ChoiceChips(
          STATUSES,
          status,
          { BooksText.statusLabel(it) },
          { status = it },
          icon = { ICONS[it] }
        )
      }
    },
    confirmButton = {
      TextButton(enabled = title.isNotBlank(), onClick = confirm) { Text(BooksText.add()) }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text(BooksText.cancel()) } }
  )
}

@Composable
internal fun BooksSettings(ctx: PluginContext) {
  val key by
  remember(ctx) {
    ctx.migrateSecretFromSettings(GOOGLE_BOOKS_KEY)
    ctx.secrets.observe(GOOGLE_BOOKS_KEY)
  }
    .collectAsState(ctx.secrets.get(GOOGLE_BOOKS_KEY))
  SectionCard("Google Books API", Icons.Outlined.Key) {
    Text(
      BooksText.settingsHint(),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    CommitTextField(
      key ?: "",
      { ctx.secrets.set(GOOGLE_BOOKS_KEY, it.trim()) },
      BooksText.apiKeyOptional()
    )
    Text(
      BooksText.keyStorageHint(),
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )
  }
}
