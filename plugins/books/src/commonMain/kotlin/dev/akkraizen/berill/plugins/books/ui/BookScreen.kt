package dev.akkraizen.berill.plugins.books.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.akkraizen.berill.api.PluginContext
import dev.akkraizen.berill.api.ui.cards.SectionCard
import dev.akkraizen.berill.api.ui.dialogs.DeleteButton
import dev.akkraizen.berill.api.ui.feedback.EmptyHint
import dev.akkraizen.berill.api.ui.fields.BodyEditor
import dev.akkraizen.berill.api.ui.fields.CommitTextField
import dev.akkraizen.berill.api.ui.fields.DateField
import dev.akkraizen.berill.api.ui.fields.IntField
import dev.akkraizen.berill.api.ui.layout.DetailHeader
import dev.akkraizen.berill.api.ui.layout.FieldsRow
import dev.akkraizen.berill.api.ui.layout.PluginList
import dev.akkraizen.berill.api.ui.layout.TwoPane
import dev.akkraizen.berill.api.ui.media.Cover
import dev.akkraizen.berill.api.ui.search.OnlineSearchPanel
import dev.akkraizen.berill.api.ui.selection.ChoiceChips
import dev.akkraizen.berill.plugins.books.BooksPlugin
import dev.akkraizen.berill.plugins.books.data.Book
import dev.akkraizen.berill.plugins.books.data.GoogleBooks
import dev.akkraizen.berill.plugins.books.data.NOT_STARTED
import dev.akkraizen.berill.plugins.books.data.STATUSES
import dev.akkraizen.berill.plugins.books.data.books
import dev.akkraizen.berill.plugins.books.i18n.BooksText

@Composable
internal fun BookScreen(ctx: PluginContext, id: String) {
  val collection = remember(ctx) { books(ctx) }
  val book by remember(collection, id) { collection.observe(id) }.collectAsState(collection.get(id))
  val b = book
    ?: run {
      EmptyHint(BooksText.bookDeleted())
      return
    }

  fun update(transform: (Book) -> Book) {
    val current = collection.get(id) ?: return
    collection.save(id, transform(current))
  }

  PluginList {
    item {
      DetailHeader(b.title.ifBlank { BooksText.untitled() }, Icons.Outlined.MenuBook) {
        DeleteButton(BooksText.deleteWhat()) {
          collection.delete(id)
          ctx.nav.back()
        }
      }
    }
    item {
      TwoPane(
        left = {
          SectionCard {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
              Cover(b.cover, Icons.Outlined.MenuBook, Modifier.size(110.dp, 165.dp))
              Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CommitTextField(
                  b.title,
                  { v -> update { it.copy(title = v.trim()) } },
                  BooksText.name()
                )
                CommitTextField(
                  b.author ?: "",
                  { v -> update { it.copy(author = v.trim().ifEmpty { null }) } },
                  BooksText.author()
                )
              }
            }
            ChoiceChips(
              STATUSES,
              b.normalizedStatus,
              { BooksText.statusLabel(it) },
              { s -> update { it.moveTo(s) } },
              icon = { ICONS[it] }
            )
          }
        },
        right = {
          SectionCard(BooksText.properties()) {
            CommitTextField(
              b.genre ?: "",
              { v -> update { it.copy(genre = v.trim().ifEmpty { null }) } },
              BooksText.genre()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              IntField(
                b.pages,
                { v -> update { it.copy(pages = v) } },
                BooksText.pages(),
                Modifier.weight(1f)
              )
              IntField(
                b.rating,
                { v -> update { it.copy(rating = v?.coerceIn(0, 10)) } },
                BooksText.rating(),
                Modifier.weight(1f)
              )
            }
            FieldsRow {
              DateField(
                b.started,
                { v -> update { it.copy(started = v) } },
                BooksText.started()
              )
              DateField(
                b.finished,
                { v -> update { it.copy(finished = v) } },
                BooksText.finished()
              )
            }
            CommitTextField(
              b.cover ?: "",
              { v -> update { it.copy(cover = v.trim().ifEmpty { null }) } },
              BooksText.coverLink()
            )
          }
        }
      )
    }
    item {
      SectionCard(BooksText.notes()) {
        BodyEditor(
          b.body ?: "",
          { v -> update { it.copy(body = v.ifBlank { null }) } },
          BooksText.notesPlaceholder()
        )
      }
    }
    item { Spacer(Modifier.width(1.dp)) }
  }
}

@Composable
internal fun BookSearch(ctx: PluginContext, onClose: () -> Unit) {
  val api = remember(ctx) { GoogleBooks(ctx) }
  val collection = remember(ctx) { books(ctx) }
  var status by rememberSaveable { mutableStateOf(NOT_STARTED) }
  OnlineSearchPanel(
    title = BooksText.searchTitle(),
    icon = Icons.Outlined.Search,
    placeholder = BooksText.searchPlaceholder(),
    fallbackIcon = Icons.Outlined.MenuBook,
    onClose = onClose,
    draftKey = "books/online-search",
    search = api::search,
    errorText = { GoogleBooks.errorText(it) },
    onPick = { hit ->
      val existing =
        collection.all().firstOrNull { it.value.title.equals(hit.title, ignoreCase = true) }
      if (existing != null) {
        ctx.nav.open(BooksPlugin.BOOK_ROUTE + existing.id)
        BooksText.alreadyInJournal(hit.title)()
      } else {
        val book = api.details(hit.ref).copy(status = status).let { it.moveTo(it.status) }
        collection.save(ctx.data.newId(), book)
        BooksText.added(book.title, BooksText.statusLabel(status).lowercase())()
      }
    },
    extra = {
      Text(
        BooksText.addAs(),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
      ChoiceChips(
        STATUSES,
        status,
        { BooksText.statusLabel(it) },
        { status = it },
        icon = { ICONS[it] }
      )
    }
  )
}
