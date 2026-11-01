package dev.akkraizen.berill.plugins.finance

import dev.akkraizen.berill.plugins.finance.data.nextOccurrence
import kotlinx.datetime.LocalDate
import kotlin.test.*

class RecurringTest {
  @Test fun monthlyRecurrenceRetainsAnchorAcrossShortMonths() {
    val february = nextOccurrence(LocalDate(2024, 1, 31), true, 31)
    assertEquals(LocalDate(2024, 2, 29), february)
    assertEquals(LocalDate(2024, 3, 31), nextOccurrence(february, true, 31))
    assertEquals(LocalDate(2025, 2, 28), nextOccurrence(LocalDate(2025, 1, 31), true, 31))
  }
  @Test fun dailyRecurrenceCrossesYearBoundary() {
    assertEquals(LocalDate(2026, 1, 1), nextOccurrence(LocalDate(2025, 12, 31), false, 31))
  }
}
