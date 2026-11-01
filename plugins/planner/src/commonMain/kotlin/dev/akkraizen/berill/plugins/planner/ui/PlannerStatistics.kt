package dev.akkraizen.berill.plugins.planner.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import dev.akkraizen.berill.api.ui.cards.SectionCard
import dev.akkraizen.berill.api.ui.charts.ChartSeries
import dev.akkraizen.berill.api.ui.charts.StatisticsChart
import dev.akkraizen.berill.api.ui.dayMonthShort
import dev.akkraizen.berill.plugins.planner.data.plannerDayTotals
import dev.akkraizen.berill.plugins.planner.data.PlannerTask
import dev.akkraizen.berill.plugins.planner.i18n.pluginI18n
import kotlinx.datetime.LocalDate

@Composable
internal fun PlannerStatistics(tasks: List<PlannerTask>, dates: List<LocalDate>) {
  val totals = dates.map { plannerDayTotals(tasks, it) }
  SectionCard(pluginI18n.text("planner.stats.week")) {
    StatisticsChart(dates.map { it.dayMonthShort() }, listOf(
      ChartSeries(pluginI18n.text("planner.stats.planned"), totals.map { it.planned.toDouble() }),
      ChartSeries(pluginI18n.text("planner.stats.done"), totals.map { it.completed.toDouble() }),
      ChartSeries(pluginI18n.text("planner.stats.unfinished"), totals.map { it.unfinished.toDouble() })
    ))
    Text(pluginI18n.text("planner.stats.hint"))
  }
}
