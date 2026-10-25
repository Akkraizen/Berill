package dev.akkraizen.berill.plugins.study

import androidx.compose.runtime.Composable
import dev.akkraizen.berill.api.ui.cards.SectionCard
import dev.akkraizen.berill.api.ui.charts.CategoryChart

@Composable
internal fun StudyStatistics(tasks: List<StudyTask>) {
  SectionCard(pluginI18n.text("study.stats.subjects")) {
    CategoryChart(tasks.filter { it.normalizedStatus != SUBMITTED }
      .groupBy { it.subject?.takeIf(String::isNotBlank) ?: StudyText.noSubject() }
      .map { it.key to it.value.size.toDouble() }.sortedByDescending { it.second })
  }
}
