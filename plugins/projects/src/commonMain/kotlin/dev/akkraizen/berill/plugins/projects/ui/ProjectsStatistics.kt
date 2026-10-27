package dev.akkraizen.berill.plugins.projects.ui

import androidx.compose.runtime.Composable
import dev.akkraizen.berill.api.ui.cards.SectionCard
import dev.akkraizen.berill.api.ui.charts.MonthlyActivityChart
import dev.akkraizen.berill.api.ui.toDateOrNull
import dev.akkraizen.berill.plugins.projects.data.Project
import dev.akkraizen.berill.plugins.projects.data.READY
import dev.akkraizen.berill.plugins.projects.data.projects
import dev.akkraizen.berill.plugins.projects.i18n.pluginI18n

@Composable
internal fun ProjectsStatistics(entries: List<Project>) {
  SectionCard(pluginI18n.text("projects.stats.months")) {
    MonthlyActivityChart(
      entries.filter { it.normalizedStatus == READY }.mapNotNull { it.finished.toDateOrNull() },
      pluginI18n.text("projects.stats.completed")
    )
  }
}
