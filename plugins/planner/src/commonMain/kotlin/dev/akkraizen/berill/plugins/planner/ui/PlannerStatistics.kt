package dev.akkraizen.berill.plugins.planner.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import dev.akkraizen.berill.api.ui.cards.SectionCard
import dev.akkraizen.berill.api.ui.charts.ChartSeries
import dev.akkraizen.berill.api.ui.charts.StatisticsChart
import dev.akkraizen.berill.api.ui.dayMonthShort
import dev.akkraizen.berill.plugins.planner.data.PlannerTask
import dev.akkraizen.berill.plugins.planner.i18n.pluginI18n
import kotlinx.datetime.LocalDate

@Composable
internal fun PlannerStatistics(tasks: List<PlannerTask>, dates: List<LocalDate>) {
  val byDate = tasks.groupBy { it.date }
  SectionCard(pluginI18n.text("planner.stats.week")) {
    StatisticsChart(dates.map { it.dayMonthShort() }, listOf(
      ChartSeries(pluginI18n.text("planner.stats.planned"), dates.map { byDate[it.toString()].orEmpty().size.toDouble() }),
      ChartSeries(pluginI18n.text("planner.stats.done"), dates.map { byDate[it.toString()].orEmpty().count { task -> task.done }.toDouble() })
    ))
    Text(pluginI18n.text("planner.stats.hint"))
  }
}
