package dev.akkraizen.berill.plugins.workouts.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import dev.akkraizen.berill.api.ui.cards.SectionCard
import dev.akkraizen.berill.api.ui.charts.ChartKind
import dev.akkraizen.berill.api.ui.charts.ChartSeries
import dev.akkraizen.berill.api.ui.charts.StatisticsChart
import dev.akkraizen.berill.api.ui.charts.StatisticsPeriod
import dev.akkraizen.berill.api.ui.charts.recentDates
import dev.akkraizen.berill.api.ui.dayMonthShort
import dev.akkraizen.berill.api.ui.feedback.EmptyHint
import dev.akkraizen.berill.api.ui.selection.ChoiceChips
import dev.akkraizen.berill.api.ui.selection.DropdownField
import dev.akkraizen.berill.plugins.workouts.data.WorkoutLog
import dev.akkraizen.berill.plugins.workouts.data.formatDuration
import dev.akkraizen.berill.plugins.workouts.data.formatKg
import dev.akkraizen.berill.plugins.workouts.data.logs
import dev.akkraizen.berill.plugins.workouts.data.workoutHistoryValues
import dev.akkraizen.berill.plugins.workouts.i18n.WorkoutsText
import dev.akkraizen.berill.plugins.workouts.i18n.pluginI18n

@Composable
internal fun WorkoutStatistics(logs: List<WorkoutLog>) {
  var period by rememberSaveable { mutableIntStateOf(30) }
  var metric by rememberSaveable { mutableIntStateOf(0) }
  var exercise by rememberSaveable { mutableStateOf<String?>(null) }
  val names = logs.filter { it.finished }.flatMap { it.exercises }.map { it.name.trim() }
    .filter { it.isNotEmpty() }.distinctBy { it.lowercase() }.sorted()
  val selected = exercise?.takeIf { it in names } ?: names.firstOrNull()
  val dates = recentDates(period)
  val labels = listOf("completed", "volume", "weight", "reps", "estimatedMax", "duration")
    .map { pluginI18n.text("workouts.stats.$it") }
  SectionCard(pluginI18n.text("workouts.stats.title"), Icons.Outlined.Insights) {
    StatisticsPeriod(period) { period = it }
    ChoiceChips(labels.indices.toList(), metric, { labels[it] }, { metric = it })
    if (metric >= 2) DropdownField(WorkoutsText.exercise(), names, selected, { it }, { exercise = it })
    Text(pluginI18n.text("workouts.stats.hint"))
    if (logs.none { it.finished } || (metric >= 2 && selected == null)) {
      EmptyHint(WorkoutsText.noLogs())
    } else {
      val values = workoutHistoryValues(logs, dates, metric, selected)
      val points = dates.zip(values).filter { metric < 2 || it.second != null }
      StatisticsChart(
        points.map { it.first.dayMonthShort() },
        listOf(ChartSeries(labels[metric], points.map { it.second })),
        positions = points.map { it.first.toEpochDays().toDouble() },
        kind = if (metric >= 2) ChartKind.Line else ChartKind.Bars,
        valueLabel = { value -> when (metric) {
          1, 2, 4 -> WorkoutsText.kgValue(formatKg(value))()
          5 -> formatDuration(value.toInt())
          else -> value.toInt().toString()
        } }
      )
    }
  }
}
