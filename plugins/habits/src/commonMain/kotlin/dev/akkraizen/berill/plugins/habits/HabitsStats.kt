package dev.akkraizen.berill.plugins.habits

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import dev.akkraizen.berill.api.Item
import dev.akkraizen.berill.api.ui.BerillIcons
import dev.akkraizen.berill.api.ui.DAYS_SHORT
import dev.akkraizen.berill.api.ui.MONTHS_SHORT
import dev.akkraizen.berill.api.ui.YearMonth
import dev.akkraizen.berill.api.ui.cards.SectionCard
import dev.akkraizen.berill.api.ui.charts.ChartSeries
import dev.akkraizen.berill.api.ui.charts.StatisticsChart
import dev.akkraizen.berill.api.ui.feedback.EmptyHint
import dev.akkraizen.berill.api.ui.fields.IntField
import dev.akkraizen.berill.api.ui.layout.DetailHeader
import dev.akkraizen.berill.api.ui.layout.PluginList
import dev.akkraizen.berill.api.ui.layout.TwoPane
import dev.akkraizen.berill.api.ui.minusDays
import dev.akkraizen.berill.api.ui.mondayOfWeek
import dev.akkraizen.berill.api.ui.plusDays
import dev.akkraizen.berill.api.ui.selection.ChoiceChips
import dev.akkraizen.berill.api.ui.toDateOrNull
import dev.akkraizen.berill.api.ui.today
import dev.akkraizen.berill.api.ui.weekdayIndex
import kotlin.math.roundToInt
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil

private val PERIODS = listOf(7, 30, 90, 365)

internal class HabitStats(
  val habits: List<Item<HabitDef>>,
  private val values: Map<String, Map<String, Int>>,
  val today: LocalDate
) {

  val allDays: List<LocalDate> = run {
    val first = values.keys.mapNotNull { it.toDateOrNull() }.filter { it <= today }.minOrNull() ?: today
    (0..first.daysUntil(today)).map { first.plusDays(it) }
  }

  fun value(day: LocalDate, habitId: String): Int = values[day.toString()]?.get(habitId) ?: 0

  fun isDone(day: LocalDate, h: Item<HabitDef>) = value(day, h.id) >= h.value.defaultDuration

  fun doneCount(day: LocalDate) = habits.count { isDone(day, it) }

  fun isPerfect(day: LocalDate) = habits.isNotEmpty() && doneCount(day) == habits.size

  fun currentStreak(test: (LocalDate) -> Boolean): Int {
    var day = if (test(today)) today else today.minusDays(1)
    var n = 0
    while (allDays.isNotEmpty() && day >= allDays.first() && test(day)) {
      n++
      day = day.minusDays(1)
    }
    return n
  }

  fun bestStreak(test: (LocalDate) -> Boolean): Int {
    var best = 0
    var run = 0
    for (d in allDays) {
      run = if (test(d)) run + 1 else 0
      best = maxOf(best, run)
    }
    return best
  }

  fun lastDays(n: Int): List<LocalDate> = (n - 1 downTo 0).map { today.minusDays(it) }

  fun completion(period: Int): Float {
    if (habits.isEmpty()) return 0f
    val days = lastDays(period)
    return days.sumOf { doneCount(it) } / (period * habits.size).toFloat()
  }

  fun habitRate(h: Item<HabitDef>, period: Int): Float = lastDays(period).count { isDone(it, h) } / period.toFloat()

  fun sumInMonth(h: Item<HabitDef>, month: YearMonth): Int =
    values
      .filterKeys { it.toDateOrNull()?.let { d -> d in month } == true }
      .values
      .sumOf { it[h.id] ?: 0 }

  fun sumTotal(h: Item<HabitDef>): Int = values.values.sumOf { it[h.id] ?: 0 }

  fun perfectIn(predicate: (LocalDate) -> Boolean): Int = allDays.count { predicate(it) && isPerfect(it) }

  fun weekdayRates(period: Int): List<Float> =
    (0..6).map { wd ->
      val days = lastDays(period).filter { it.weekdayIndex == wd }
      if (days.isEmpty() || habits.isEmpty())
        0f
      else
        days.sumOf { doneCount(it) } / (days.size * habits.size).toFloat()
    }

  fun months(): List<Triple<YearMonth, Float, Int>> =
    (5 downTo 0).map { back ->
      val month = YearMonth.of(today) + -back
      val days = allDays.filter { it in month }
      val rate = if (days.isEmpty() || habits.isEmpty())
        0f
      else
        days.sumOf { doneCount(it) } / (days.size * habits.size).toFloat()
      Triple(month, rate, days.count { isPerfect(it) })
    }
}

@Composable
internal fun HabitsStatsScreen(data: HabitsData) {
  val defs by remember(data) { data.defs.observe() }.collectAsState(emptyList())
  val days by remember(data) { data.days.observe() }.collectAsState(emptyList())
  var period by rememberSaveable { mutableStateOf(30) }
  val now = today()
  val stats = remember(defs, days, now) {
    HabitStats(
      habits = defs.active(),
      values = days.associate { r ->
        r.id to r.fields.keys.associateWith { k -> dayValue(r.fields, k) }
      },
      today = now
    )
  }
  PluginList {
    item { DetailHeader(HabitsText.statistics(), Icons.Outlined.Insights) }
    if (stats.habits.isEmpty()) {
      item { EmptyHint(HabitsText.noHabits()) }
      return@PluginList
    }
    item {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(HabitsText.periodForPercent(), color = MaterialTheme.colorScheme.onSurfaceVariant)
        ChoiceChips(PERIODS, period, { if (it == 365) HabitsText.year() else HabitsText.days(it)() }, { period = it })
      }
    }
    item { Heatmap(stats) }
    item {
      TwoPane(left = { WeekdayChart(stats, period) }, right = { MonthsChart(stats) }, leftWeight = 1f)
    }
    item { HabitTrendChart(stats, period) }
  }
}

@Composable
private fun Heatmap(stats: HabitStats) {
  val start = stats.today.mondayOfWeek().minusDays(25 * 7)
  val primary = MaterialTheme.colorScheme.primary
  val empty = MaterialTheme.colorScheme.surfaceContainerHighest
  SectionCard(HabitsText.halfYearActivity(), Icons.Outlined.CalendarMonth) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
      Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        (0..6).forEach { wd ->
          Box(Modifier.size(width = 22.dp, height = 14.dp)) {
            if (wd % 2 == 0)
              Text(DAYS_SHORT[wd], style = MaterialTheme.typography.labelSmall)
          }
        }
      }
      (0 until 26).forEach { week ->
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
          (0..6).forEach { wd ->
            val day = start.plusDays(week * 7 + wd)
            val ratio = if (day > stats.today)
              -1f
            else
              stats.doneCount(day) / stats.habits.size.toFloat()
            Box(
              Modifier.size(14.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(
                  when {
                    ratio < 0f -> empty.copy(alpha = 0f)
                    ratio == 0f -> empty
                    else -> primary.copy(alpha = 0.25f + 0.75f * ratio)
                  }
                )
                .then(
                  if (day == stats.today)
                    Modifier.border(1.5.dp, MaterialTheme.colorScheme.onSurface, RoundedCornerShape(3.dp))
                  else
                    Modifier
                )
            )
          }
        }
      }
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
      Text(HabitsText.less(), style = MaterialTheme.typography.labelSmall)
      listOf(0f, 0.25f, 0.5f, 0.75f, 1f).forEach { r ->
        Box(
          Modifier.size(12.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(if (r == 0f) empty else primary.copy(alpha = 0.25f + 0.75f * r))
        )
      }
      Text(HabitsText.more(), style = MaterialTheme.typography.labelSmall)
    }
  }
}

@Composable
private fun BarChart(values: List<Float>, labels: List<String>) {
  StatisticsChart(
    labels,
    listOf(ChartSeries(HabitsText.statistics(), values.map { it * 100.0 })),
    target = 100.0,
    valueLabel = { "${it.roundToInt()}%" }
  )
}

@Composable
private fun WeekdayChart(stats: HabitStats, period: Int) {
  val rates = stats.weekdayRates(period)
  SectionCard(
    HabitsText.byWeekday(),
    Icons.Outlined.DateRange,
    trailing = {
      Text(
        HabitsText.overDays(period)(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
  ) {
    BarChart(rates, DAYS_SHORT)
  }
}

@Composable
private fun MonthsChart(stats: HabitStats) {
  val months = stats.months()
  SectionCard(HabitsText.byMonth(), Icons.Outlined.Insights) {
    BarChart(months.map { it.second }, months.map { MONTHS_SHORT[it.first.month - 1] })
  }
}

@Composable
internal fun HabitsSettings(data: HabitsData) {
  val goalMonth by
    remember(data) { data.observePerfectGoal(GOAL_MONTH, 20) }
      .collectAsState(data.perfectGoal(GOAL_MONTH, 20))
  val goalYear by
    remember(data) { data.observePerfectGoal(GOAL_YEAR, 250) }
      .collectAsState(data.perfectGoal(GOAL_YEAR, 250))
  SectionCard(HabitsText.perfectGoals(), Icons.Outlined.Flag) {
    Text(
      HabitsText.perfectGoalsHint(),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      IntField(
        goalMonth,
        { v -> v?.let { data.setPerfectGoal(GOAL_MONTH, it.coerceAtLeast(0)) } },
        HabitsText.perMonth(),
        Modifier.weight(1f)
      )
      IntField(
        goalYear,
        { v -> v?.let { data.setPerfectGoal(GOAL_YEAR, it.coerceAtLeast(0)) } },
        HabitsText.perYear(),
        Modifier.weight(1f)
      )
    }
  }
  SectionCard(HabitsText.habitList(), BerillIcons.Edit) {
    Text(
      HabitsText.habitListHint(),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    TextButton(onClick = { data.ctx.nav.openPlugin(HabitsPlugin.id, HabitsPlugin.EDIT) }) {
      Text(HabitsText.openHabitList())
    }
  }
}
