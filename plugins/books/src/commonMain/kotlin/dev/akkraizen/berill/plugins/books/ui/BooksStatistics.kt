package dev.akkraizen.berill.plugins.books.ui

import androidx.compose.runtime.Composable
import dev.akkraizen.berill.api.ui.cards.SectionCard
import dev.akkraizen.berill.api.ui.charts.MonthlyActivityChart
import dev.akkraizen.berill.api.ui.toDateOrNull
import dev.akkraizen.berill.plugins.books.data.Book
import dev.akkraizen.berill.plugins.books.data.DONE
import dev.akkraizen.berill.plugins.books.data.books
import dev.akkraizen.berill.plugins.books.i18n.pluginI18n

@Composable
internal fun BooksStatistics(entries: List<Book>) {
  SectionCard(pluginI18n.text("books.stats.months")) {
    MonthlyActivityChart(
      entries.filter { it.normalizedStatus == DONE }.mapNotNull { it.finished.toDateOrNull() },
      pluginI18n.text("books.stats.completed")
    )
  }
}
