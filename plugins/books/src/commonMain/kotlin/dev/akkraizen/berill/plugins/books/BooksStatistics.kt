package dev.akkraizen.berill.plugins.books

import androidx.compose.runtime.Composable
import dev.akkraizen.berill.api.ui.cards.SectionCard
import dev.akkraizen.berill.api.ui.charts.MonthlyActivityChart
import dev.akkraizen.berill.api.ui.toDateOrNull

@Composable
internal fun BooksStatistics(entries: List<Book>) {
  SectionCard(pluginI18n.text("books.stats.months")) {
    MonthlyActivityChart(
      entries.filter { it.normalizedStatus == DONE }.mapNotNull { it.finished.toDateOrNull() },
      pluginI18n.text("books.stats.completed")
    )
  }
}
