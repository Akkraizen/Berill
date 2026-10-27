package dev.akkraizen.berill.plugins.languages.ui

import androidx.compose.runtime.Composable
import dev.akkraizen.berill.api.ui.cards.SectionCard
import dev.akkraizen.berill.api.ui.charts.MonthlyActivityChart
import dev.akkraizen.berill.api.ui.toDateOrNull
import dev.akkraizen.berill.plugins.languages.data.Lesson
import dev.akkraizen.berill.plugins.languages.data.lessons
import dev.akkraizen.berill.plugins.languages.i18n.LanguagesText
import dev.akkraizen.berill.plugins.languages.i18n.pluginI18n

@Composable
internal fun LanguageStatistics(lessons: List<Lesson>) {
  SectionCard(pluginI18n.text("languages.stats.lessons")) {
    MonthlyActivityChart(lessons.mapNotNull { it.date.toDateOrNull() }, LanguagesText.statLessons())
  }
}
