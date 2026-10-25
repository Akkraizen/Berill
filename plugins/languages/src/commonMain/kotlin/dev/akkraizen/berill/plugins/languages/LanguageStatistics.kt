package dev.akkraizen.berill.plugins.languages

import androidx.compose.runtime.Composable
import dev.akkraizen.berill.api.ui.cards.SectionCard
import dev.akkraizen.berill.api.ui.charts.MonthlyActivityChart
import dev.akkraizen.berill.api.ui.toDateOrNull

@Composable
internal fun LanguageStatistics(lessons: List<Lesson>) {
  SectionCard(pluginI18n.text("languages.stats.lessons")) {
    MonthlyActivityChart(lessons.mapNotNull { it.date.toDateOrNull() }, LanguagesText.statLessons())
  }
}
