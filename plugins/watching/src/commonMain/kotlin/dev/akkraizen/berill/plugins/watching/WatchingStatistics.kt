package dev.akkraizen.berill.plugins.watching

import androidx.compose.runtime.Composable
import dev.akkraizen.berill.api.ui.cards.SectionCard
import dev.akkraizen.berill.api.ui.charts.MonthlyActivityChart
import dev.akkraizen.berill.api.ui.toDateOrNull

@Composable
internal fun WatchingStatistics(entries: List<Title>) {
  SectionCard(pluginI18n.text("watching.stats.months")) {
    MonthlyActivityChart(
      entries.filter { it.normalizedStatus == WATCHED }.mapNotNull { it.finished.toDateOrNull() },
      pluginI18n.text("watching.stats.completed")
    )
  }
}
