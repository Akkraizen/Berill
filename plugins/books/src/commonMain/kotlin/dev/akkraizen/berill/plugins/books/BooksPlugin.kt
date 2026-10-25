package dev.akkraizen.berill.plugins.books

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
import dev.akkraizen.berill.api.BerillPlugin
import dev.akkraizen.berill.api.HomeCard
import dev.akkraizen.berill.api.PluginContext
import dev.akkraizen.berill.api.PluginPermissions
import dev.akkraizen.berill.api.ui.animation.AnimatedRoute
import dev.akkraizen.berill.api.ui.feedback.EmptyHint
import dev.akkraizen.berill.api.ui.interaction.interactive
import dev.akkraizen.berill.api.ui.media.Cover
import dev.akkraizen.berill.api.ui.today
import kotlinx.serialization.Serializable

object BooksPlugin : BerillPlugin {
  const val BOOKS = "books"

  override val id = "books"
  override val title
    get() = BooksText.title()

  override val icon = Icons.Outlined.MenuBook
  override val order = 20
  override val description
    get() = BooksText.description()

  override val installedByDefault = true
  override val hasSettings = true

  @Composable
  override fun Settings(ctx: PluginContext) = BooksSettings(ctx)

  override val collections = listOf(BOOKS)

  override val permissions = PluginPermissions(network = listOf("www.googleapis.com"), anyImageHost = true)

  @Composable
  override fun Screen(ctx: PluginContext) {
    val route by ctx.nav.route.collectAsState()
    val r = route

    AnimatedRoute(r) { r ->
      when {
        r != null && r.startsWith(BOOK_ROUTE) -> BookScreen(ctx, r.removePrefix(BOOK_ROUTE))
        else -> BooksBoard(ctx)
      }
    }
  }

  override fun homeCards(ctx: PluginContext) =
    listOf(HomeCard("reading", BooksText.readingNowCard, order = 30) { ReadingNow(ctx) })

  internal const val BOOK_ROUTE = "book:"
}

const val NOT_STARTED = "не начато"
const val READING = "в процессе чтения"
const val DONE = "прочитано"
val STATUSES = listOf(NOT_STARTED, READING, DONE)

@Serializable
data class Book(
  val title: String = "",
  val author: String? = null,
  val status: String = NOT_STARTED,
  val genre: String? = null,
  val pages: Int? = null,
  val rating: Int? = null,
  val started: String? = null,
  val finished: String? = null,
  val cover: String? = null,
  val body: String? = null
) {

  val normalizedStatus: String
    get() = if (status in STATUSES) status else NOT_STARTED

  fun moveTo(target: String): Book {
    val now = today().toString()
    return when (target) {
      READING -> copy(status = target, started = started ?: now, finished = null)
      DONE -> copy(status = target, started = started ?: now, finished = finished ?: now)
      else -> copy(status = target, started = null, finished = null)
    }
  }
}

internal fun books(ctx: PluginContext) = ctx.data.collection(BooksPlugin.BOOKS, Book.serializer())

@Composable
private fun ReadingNow(ctx: PluginContext) {
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
