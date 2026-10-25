package dev.akkraizen.berill.plugins.projects

import androidx.compose.runtime.Composable
import dev.akkraizen.berill.api.ui.cards.SectionCard
import dev.akkraizen.berill.api.ui.charts.MonthlyActivityChart
import dev.akkraizen.berill.api.ui.toDateOrNull

@Composable
internal fun ProjectsStatistics(entries: List<Project>) {
  SectionCard(pluginI18n.text("projects.stats.months")) {
    MonthlyActivityChart(
      entries.filter { it.normalizedStatus == READY }.mapNotNull { it.finished.toDateOrNull() },
      pluginI18n.text("projects.stats.completed")
    )
  }
}
