package dev.akkraizen.berill.core

import dev.akkraizen.berill.api.i18n.AppLanguage
import dev.akkraizen.berill.api.i18n.I18n
import dev.akkraizen.berill.api.ui.YearMonth
import dev.akkraizen.berill.api.ui.charts.ChartBounds
import dev.akkraizen.berill.api.ui.charts.aggregateDateValues
import dev.akkraizen.berill.api.ui.charts.chartBounds
import dev.akkraizen.berill.api.ui.charts.monthlyActivity
import dev.akkraizen.berill.api.ui.charts.recentDates
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate

class ChartDataTest {
  @Test
  fun periodLabelsResolveForEverySupportedChartPeriod() {
    for (language in AppLanguage.entries) {
      for (count in listOf(7, 30, 90)) {
        val label = I18n.catalog.text(
          "charts.days",
          language.code,
          arguments = mapOf("count" to count),
          count = count
        )
        assertTrue(label.contains(count.toString()))
      }
    }
  }

  @Test
  fun recordedZeroIsDifferentFromMissingDataAndDuplicateDatesAreSummed() {
    val dates = recentDates(3, LocalDate(2026, 1, 3))
    val samples = listOf(dates[0] to 0.0, dates[1] to 12.0, dates[1] to 3.0, dates[2] to Double.NaN)
    assertEquals(listOf(0.0, 15.0, null), aggregateDateValues(samples, dates))
    assertEquals(listOf(0.0, 15.0, 0.0), aggregateDateValues(samples, dates, 0.0))
  }

  @Test
  fun monthlyCountsCrossYearBoundariesAndExcludeFutureEvents() {
    val end = LocalDate(2026, 1, 15)
    val dates = listOf(LocalDate(2025, 12, 31), end, end, LocalDate(2026, 1, 16))
    val result = monthlyActivity(dates, end, 3)
    assertEquals(listOf(YearMonth(2025, 11), YearMonth(2025, 12), YearMonth(2026, 1)), result.map { it.first })
    assertEquals(listOf(0.0, 1.0, 2.0), result.map { it.second })
  }

  @Test
  fun scalesHandleEmptyZeroConstantNegativeAndNonFiniteValues() {
    assertEquals(ChartBounds(0.0, 1.0), chartBounds(listOf(null, Double.NaN, Double.POSITIVE_INFINITY)))
    val zero = chartBounds(listOf(0.0))
    assertTrue(zero.high > zero.low)
    assertEquals(0f, zero.fraction(0.0))
    val flat = chartBounds(listOf(75.0, 75.0), includeZero = false)
    assertTrue(flat.low < 75 && flat.high > 75)
    val negative = chartBounds(listOf(-20.0, 10.0), target = 50.0)
    assertTrue(negative.low < -20 && negative.high >= 50)
    assertTrue(negative.fraction(-20.0) < negative.fraction(10.0))
  }

  @Test
  fun dateRangeIncludesLeapDayAndHasNoOffByOne() {
    assertEquals(
      listOf(LocalDate(2024, 2, 28), LocalDate(2024, 2, 29), LocalDate(2024, 3, 1)),
      recentDates(3, LocalDate(2024, 3, 1))
    )
    assertFailsWith<IllegalArgumentException> { recentDates(0) }
    assertFailsWith<IllegalArgumentException> { monthlyActivity(emptyList(), count = 0) }
  }
}
