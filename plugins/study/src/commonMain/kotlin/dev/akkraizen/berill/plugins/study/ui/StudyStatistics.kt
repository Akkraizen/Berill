package dev.akkraizen.berill.plugins.study.ui

import androidx.compose.runtime.Composable
import dev.akkraizen.berill.api.ui.cards.SectionCard
import dev.akkraizen.berill.api.ui.charts.CategoryChart
import dev.akkraizen.berill.plugins.study.data.SUBMITTED
import dev.akkraizen.berill.plugins.study.data.StudyTask
import dev.akkraizen.berill.plugins.study.data.tasks
import dev.akkraizen.berill.plugins.study.i18n.StudyText
import dev.akkraizen.berill.plugins.study.i18n.pluginI18n

@Composable
internal fun StudyStatistics(tasks: List<StudyTask>) {
  SectionCard(pluginI18n.text("study.stats.subjects")) {
    CategoryChart(tasks.filter { it.normalizedStatus != SUBMITTED }
      .groupBy { it.subject?.takeIf(String::isNotBlank) ?: StudyText.noSubject() }
      .map { it.key to it.value.size.toDouble() }.sortedByDescending { it.second })
  }
}
