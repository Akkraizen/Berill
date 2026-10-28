package dev.akkraizen.berill.plugins.books.widgets

import dev.akkraizen.berill.api.PluginContext
import dev.akkraizen.berill.api.i18n.Tr
import dev.akkraizen.berill.api.ui.toDateOrNull
import dev.akkraizen.berill.api.ui.today
import dev.akkraizen.berill.api.widgets.WidgetAction
import dev.akkraizen.berill.api.widgets.WidgetBlock
import dev.akkraizen.berill.api.widgets.WidgetContent
import dev.akkraizen.berill.api.widgets.WidgetIcon
import dev.akkraizen.berill.api.widgets.WidgetProvider
import dev.akkraizen.berill.api.widgets.WidgetRequest
import dev.akkraizen.berill.api.widgets.WidgetRow
import dev.akkraizen.berill.api.widgets.WidgetSize
import dev.akkraizen.berill.plugins.books.BooksPlugin
import dev.akkraizen.berill.plugins.books.data.Book
import dev.akkraizen.berill.plugins.books.data.READING
import dev.akkraizen.berill.plugins.books.data.books
import dev.akkraizen.berill.plugins.books.i18n.BooksText
import kotlinx.datetime.daysUntil

internal fun booksWidgets(ctx: PluginContext): List<WidgetProvider> = listOf(ReadingWidget(ctx))

private fun Book.readingInfo(): String? {
  val days = started.toDateOrNull()?.daysUntil(today())?.takeIf { it >= 0 }
  return listOfNotNull(
    days?.let { BooksText.widgetReadingDays(it + 1)() },
    pages?.takeIf { it > 0 }?.let { BooksText.widgetPages(it)() }
  ).joinToString(" · ").ifBlank { null }
}

private class ReadingWidget(private val ctx: PluginContext) : WidgetProvider {
  override val id = "reading"
  override val title: Tr = BooksText.widgetReadingTitle
  override val description: Tr = BooksText.widgetReadingDescription
  override val size = WidgetSize.Medium

  private fun reading() = books(ctx).all().filter { it.value.normalizedStatus == READING }.sortedByDescending { it.value.started ?: "" }

  override suspend fun content(request: WidgetRequest): WidgetContent {
    val list = reading()
    return WidgetContent(
      title = BooksText.readingNowCard(),
      icon = WidgetIcon.Book,
      open = WidgetAction.Open(),
      blocks = if (list.isEmpty())
        emptyList()
      else
        listOf(
          WidgetBlock.Rows(
            list.map { item ->
              WidgetRow(
                title = item.value.title.ifBlank { BooksText.bookFallback() },
                subtitle = listOfNotNull(item.value.author?.ifBlank { null }, item.value.readingInfo()).joinToString(" · ").ifBlank { null },
                action = WidgetAction.Open(route = BooksPlugin.BOOK_ROUTE + item.id),
                icon = WidgetIcon.Book
              )
            }
          )
        ),
      empty = BooksText.nothingReading()
    )
  }

  override suspend fun summary(request: WidgetRequest): WidgetRow? {
    val book = reading().firstOrNull() ?: return null
    return WidgetRow(
      title = BooksText.readingNowCard(),
      subtitle = listOfNotNull(book.value.title, book.value.author).joinToString(" — "),
      action = WidgetAction.Open(pluginId = BooksPlugin.id, route = BooksPlugin.BOOK_ROUTE + book.id),
      icon = WidgetIcon.Book
    )
  }
}
