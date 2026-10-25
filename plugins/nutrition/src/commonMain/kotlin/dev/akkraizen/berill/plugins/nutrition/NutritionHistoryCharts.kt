package dev.akkraizen.berill.plugins.nutrition

import androidx.compose.runtime.Composable
import dev.akkraizen.berill.api.ui.cards.SectionCard
import dev.akkraizen.berill.api.ui.charts.ChartKind
import dev.akkraizen.berill.api.ui.charts.ChartSeries
import dev.akkraizen.berill.api.ui.charts.StatisticsChart
import dev.akkraizen.berill.api.ui.charts.aggregateDateValues
import dev.akkraizen.berill.api.ui.dayMonthShort
import dev.akkraizen.berill.api.ui.layout.TwoPane
import dev.akkraizen.berill.api.ui.toDateOrNull
import kotlinx.datetime.LocalDate

@Composable
internal fun NutritionHistoryCharts(days: List<LocalDate>, totals: Map<String, Totals>, water: List<WaterDay>, norm: Norm) {
  val labels = days.map { it.dayMonthShort() }
  TwoPane(left = {
    SectionCard(pluginI18n.text("nutrition.stats.macros")) {
      StatisticsChart(labels, listOf(
        ChartSeries(NutritionText.protein(), days.map { totals[it.toString()]?.protein }),
        ChartSeries(NutritionText.fat(), days.map { totals[it.toString()]?.fat }),
        ChartSeries(NutritionText.carbs(), days.map { totals[it.toString()]?.carbs })
      ), kind = ChartKind.Line, valueLabel = { NutritionText.gramsValue(formatNum(it))() })
    }
  }, right = {
    SectionCard(pluginI18n.text("nutrition.stats.water")) {
      val samples = water.mapNotNull { row -> row.date.toDateOrNull()?.let { it to row.ml.toDouble() } }
      StatisticsChart(
        labels,
        listOf(ChartSeries(NutritionText.water(), aggregateDateValues(samples, days))),
        target = norm.waterMl.toDouble(),
        valueLabel = { NutritionText.litersValue(formatNum(it / 1000))() }
      )
    }
  })
}
