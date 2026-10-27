package dev.akkraizen.berill.plugins.books.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.akkraizen.berill.api.PluginContext
import dev.akkraizen.berill.api.ui.feedback.EmptyHint
import dev.akkraizen.berill.api.ui.interaction.interactive
import dev.akkraizen.berill.api.ui.media.Cover
import dev.akkraizen.berill.plugins.books.BooksPlugin
import dev.akkraizen.berill.plugins.books.data.READING
import dev.akkraizen.berill.plugins.books.data.books
import dev.akkraizen.berill.plugins.books.i18n.BooksText

@Composable
internal fun ReadingNow(ctx: PluginContext) {
  val all by remember(ctx) { books(ctx).observe() }.collectAsState(emptyList())
  val reading = all.filter { it.value.normalizedStatus == READING }
    .sortedByDescending { it.value.started ?: "" }
  if (reading.isEmpty()) EmptyHint(BooksText.nothingReading())
  reading.forEach { item ->
    Row(
      Modifier.interactive(bleed = 6.dp) {
        ctx.nav.openPlugin(BooksPlugin.id, BooksPlugin.BOOK_ROUTE + item.id)
      },
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Cover(item.value.cover, Icons.Outlined.MenuBook, Modifier.size(40.dp, 60.dp))
      Column {
        Text(
          item.value.title,
          style = MaterialTheme.typography.titleSmall,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis
        )
        Text(
          item.value.author ?: BooksText.bookFallback(),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    }
  }
}
