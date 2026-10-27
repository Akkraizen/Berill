package dev.akkraizen.berill.plugins.habits.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import dev.akkraizen.berill.api.ui.cards.SectionCard
import dev.akkraizen.berill.api.ui.charts.ChartKind
import dev.akkraizen.berill.api.ui.charts.ChartSeries
import dev.akkraizen.berill.api.ui.charts.StatisticsChart
import dev.akkraizen.berill.api.ui.dayMonthShort
import dev.akkraizen.berill.api.ui.selection.DropdownField
import dev.akkraizen.berill.plugins.habits.data.formatAmount
import dev.akkraizen.berill.plugins.habits.i18n.pluginI18n
import kotlin.math.roundToInt

@Composable
internal fun HabitTrendChart(stats: HabitStats, period: Int) {
  var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
  val selected = stats.habits.firstOrNull { it.id == selectedId }
  val dates = stats.lastDays(period)
  val label = selected?.value?.label ?: pluginI18n.text("habits.stats.completion")
  val values = dates.map { date ->
    if (selected == null) stats.doneCount(date) * 100.0 / stats.habits.size.coerceAtLeast(1)
    else stats.value(date, selected.id).toDouble()
  }
  SectionCard(pluginI18n.text("habits.stats.trend")) {
    DropdownField(
      pluginI18n.text("habits.stats.metric"),
      listOf("") + stats.habits.map { it.id },
      selected?.id ?: "",
      { id -> stats.habits.firstOrNull { it.id == id }?.value?.label ?: pluginI18n.text("habits.stats.completion") },
      { selectedId = it.takeIf { it.isNotEmpty() } }
    )
    StatisticsChart(
      dates.map { it.dayMonthShort() },
      listOf(ChartSeries(label, values)),
      kind = ChartKind.Line,
      target = selected?.value?.defaultDuration?.toDouble() ?: 100.0,
      valueLabel = { value -> if (selected == null) "${value.roundToInt()}%" else formatAmount(selected.value.unit, value.roundToInt()) }
    )
  }
}
