package dev.akkraizen.berill.plugins.finance

import androidx.compose.runtime.Composable
import dev.akkraizen.berill.api.ui.YearMonth
import dev.akkraizen.berill.api.ui.cards.SectionCard
import dev.akkraizen.berill.api.ui.charts.ChartSeries
import dev.akkraizen.berill.api.ui.charts.StatisticsChart
import dev.akkraizen.berill.api.ui.charts.aggregateDateValues
import dev.akkraizen.berill.api.ui.dayMonthShort
import dev.akkraizen.berill.api.ui.formatRub
import dev.akkraizen.berill.api.ui.plusDays
import dev.akkraizen.berill.api.ui.toDateOrNull
import dev.akkraizen.berill.api.ui.today

@Composable
internal fun FinanceStatistics(entries: List<Operation>, month: YearMonth) {
  val dates = (0 until month.lengthDays).map { month.first.plusDays(it) }
  val series = listOf(INCOME, EXPENSE).map { kind ->
    val samples = entries.filter { it.kind == kind }.mapNotNull { operation ->
      operation.date.toDateOrNull()?.let { it to operation.amount }
    }
    val values = aggregateDateValues(samples, dates).mapIndexed { i, value ->
      value ?: 0.0.takeIf { dates[i] <= today() }
    }
    ChartSeries(kindLabel(kind), values)
  }
  SectionCard(pluginI18n.text("finance.stats.flow")) {
    StatisticsChart(dates.map { it.dayMonthShort() }, series, valueLabel = ::formatRub)
  }
}
