package dev.akkraizen.berill.plugins.books

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import dev.akkraizen.berill.api.BerillPlugin
import dev.akkraizen.berill.api.HomeCard
import dev.akkraizen.berill.api.PluginContext
import dev.akkraizen.berill.api.PluginPermissions
import dev.akkraizen.berill.api.ui.animation.AnimatedRoute
import dev.akkraizen.berill.plugins.books.data.books
import dev.akkraizen.berill.plugins.books.i18n.BooksText
import dev.akkraizen.berill.plugins.books.ui.BookScreen
import dev.akkraizen.berill.plugins.books.ui.BooksBoard
import dev.akkraizen.berill.plugins.books.ui.BooksSettings
import dev.akkraizen.berill.plugins.books.ui.ReadingNow
import dev.akkraizen.berill.plugins.books.widgets.booksWidgets

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

  override fun widgets(ctx: PluginContext) = booksWidgets(ctx)
}
