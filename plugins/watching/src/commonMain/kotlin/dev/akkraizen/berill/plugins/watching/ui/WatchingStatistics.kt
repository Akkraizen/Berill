package dev.akkraizen.berill.plugins.watching.ui

import androidx.compose.runtime.Composable
import dev.akkraizen.berill.api.ui.cards.SectionCard
import dev.akkraizen.berill.api.ui.charts.MonthlyActivityChart
import dev.akkraizen.berill.api.ui.toDateOrNull
import dev.akkraizen.berill.plugins.watching.data.Title
import dev.akkraizen.berill.plugins.watching.data.WATCHED
import dev.akkraizen.berill.plugins.watching.i18n.pluginI18n

@Composable
internal fun WatchingStatistics(entries: List<Title>) {
  SectionCard(pluginI18n.text("watching.stats.months")) {
    MonthlyActivityChart(
      entries.filter { it.normalizedStatus == WATCHED }.mapNotNull { it.finished.toDateOrNull() },
      pluginI18n.text("watching.stats.completed")
    )
  }
}
