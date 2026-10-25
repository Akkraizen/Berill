package dev.akkraizen.berill.plugins.nutrition

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.akkraizen.berill.api.ui.charts.ChartKind
import dev.akkraizen.berill.api.ui.charts.ChartSeries
import dev.akkraizen.berill.api.ui.charts.StatisticsChart
import dev.akkraizen.berill.api.ui.dayMonthShort
import kotlinx.datetime.LocalDate

@Composable
internal fun LineChart(points: List<Pair<LocalDate, Double>>, unit: (Double) -> String, modifier: Modifier = Modifier) {
  val sorted = points.sortedBy { it.first }
  StatisticsChart(
    sorted.map { it.first.toString() },
    listOf(ChartSeries(NutritionText.weightKg(), sorted.map { it.second })),
    modifier,
    kind = ChartKind.Line,
    positions = sorted.map { it.first.toEpochDays().toDouble() },
    valueLabel = unit
  )
}

@Composable
internal fun BarChart(days: List<LocalDate>, values: List<Double?>, target: Double, modifier: Modifier = Modifier) {
  StatisticsChart(
    days.map { it.dayMonthShort() },
    listOf(ChartSeries(NutritionText.kcal(), values)),
    modifier,
    target = target,
    valueLabel = { NutritionText.kcalValue(it.kcalText())() }
  )
}
