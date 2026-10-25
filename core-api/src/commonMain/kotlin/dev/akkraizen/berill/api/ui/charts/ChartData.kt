package dev.akkraizen.berill.api.ui.charts

import dev.akkraizen.berill.api.ui.*
import kotlin.math.abs
import kotlin.math.round
import kotlinx.datetime.LocalDate

data class ChartSeries(val label: String, val values: List<Double?>)

enum class ChartKind { Bars, Line }

data class ChartBounds(val low: Double, val high: Double) {
  fun fraction(value: Double): Float = ((value - low) / (high - low)).toFloat().coerceIn(0f, 1f)
}

fun chartBounds(
  values: List<Double?>,
  target: Double? = null,
  includeZero: Boolean = true
): ChartBounds {
  val finite = (values + target).filterNotNull().filter { it.isFinite() }
  if (finite.isEmpty()) return ChartBounds(0.0, 1.0)
  var low = finite.min()
  var high = finite.max()
  if (includeZero) {
    low = minOf(0.0, low); high = maxOf(0.0, high)
  }
  val padding = ((high - low) * 0.1).coerceAtLeast(if (includeZero) 1.0 else 0.5)
  if (low < 0 || !includeZero) low -= padding
  if (high > 0 || low == high || !includeZero) high += padding
  return ChartBounds(low, high)
}

fun aggregateDateValues(
  samples: List<Pair<LocalDate, Double>>,
  dates: List<LocalDate>,
  missingValue: Double? = null
): List<Double?> {
  val totals = samples.filter { it.second.isFinite() }.groupBy { it.first }
    .mapValues { (_, values) -> values.sumOf { it.second } }
  return dates.map { totals[it] ?: missingValue }
}

fun recentDates(count: Int, end: LocalDate = today()): List<LocalDate> {
  require(count in 1..366)
  return (count - 1 downTo 0).map { end.minusDays(it) }
}

fun monthlyActivity(
  dates: List<LocalDate>,
  end: LocalDate = today(),
  count: Int = 6
): List<Pair<YearMonth, Double>> {
  require(count in 1..120)
  val totals = dates.filter { it <= end }.groupingBy { YearMonth.of(it) }.eachCount()
  return (count - 1 downTo 0).map { back ->
    val month = YearMonth.of(end) + -back
    month to (totals[month] ?: 0).toDouble()
  }
}

fun chartNumber(value: Double): String {
  if (!value.isFinite()) return "—"
  val rounded = round(value * 10) / 10
  return if (abs(rounded) < 1e15 && rounded % 1.0 == 0.0) rounded.toLong()
    .toString() else rounded.toString()
}
