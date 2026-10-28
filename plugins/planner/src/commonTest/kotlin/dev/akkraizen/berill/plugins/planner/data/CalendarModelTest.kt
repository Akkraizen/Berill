package dev.akkraizen.berill.plugins.planner.data

import dev.akkraizen.berill.api.Item
import dev.akkraizen.berill.api.ui.plusDays
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate

class CalendarModelTest {
  private val day = LocalDate(2026, 10, 28)

  private fun ev(id: String, start: String, end: String, date: String = "2026-10-28", endDate: String? = null) =
    Item(id, CalendarEvent(title = id, date = date, endDate = endDate, start = start, end = end))

  @Test
  fun overlappingEventsShareColumnsAndSeparateClustersReset() {
    val segs = daySegments(
      listOf(
        ev("a", "09:00", "10:00"),
        ev("b", "09:30", "11:00"),
        ev("c", "10:00", "10:30"),
        ev("d", "12:00", "13:00")
      ),
      day
    ).associateBy { it.id }
    assertEquals(0, segs.getValue("a").column)
    assertEquals(1, segs.getValue("b").column)
    assertEquals(0, segs.getValue("c").column)
    assertEquals(2, segs.getValue("a").columns)
    assertEquals(2, segs.getValue("c").columns)
    assertEquals(1, segs.getValue("d").columns)
  }

  @Test
  fun eventsCrossingMidnightAreClippedToEachDay() {
    val night = ev("n", "22:00", "02:00", endDate = "2026-10-29")
    val first = daySegments(listOf(night), day).single()
    assertEquals(22 * 60, first.startMinute)
    assertEquals(24 * 60, first.endMinute)
    val second = daySegments(listOf(night), LocalDate(2026, 10, 29)).single()
    assertEquals(0, second.startMinute)
    assertEquals(2 * 60, second.endMinute)
  }

  @Test
  fun endingAtMidnightDoesNotSpillIntoNextDay() {
    val e = ev("m", "20:00", "00:00", endDate = "2026-10-29").value
    assertTrue(e.occursOn(day))
    assertFalse(e.occursOn(LocalDate(2026, 10, 29)))
  }

  @Test
  fun allDayRangesAreInclusiveAndHiddenFromTheHourGrid() {
    val trip = Item("t", CalendarEvent(title = "trip", date = "2026-10-27", endDate = "2026-10-29"))
    assertTrue(trip.value.occursOn(day))
    assertTrue(trip.value.occursOn(LocalDate(2026, 10, 29)))
    assertTrue(daySegments(listOf(trip), day).isEmpty())
    assertEquals(listOf("t"), eventsOn(listOf(trip), day).map { it.id })
  }

  @Test
  fun deletedEventsAreHidden() {
    val gone = Item("g", CalendarEvent(title = "g", date = "2026-10-28", start = "10:00", end = "11:00", deleted = true))
    assertTrue(daySegments(listOf(gone), day).isEmpty())
    assertTrue(eventsOn(listOf(gone), day).isEmpty())
  }

  @Test
  fun monthGridStartsOnMondayAndCoversSixWeeks() {
    val grid = monthGrid(2026, 10)
    assertEquals(42, grid.size)
    assertEquals(DayOfWeek.MONDAY, grid.first().dayOfWeek)
    assertEquals(LocalDate(2026, 9, 28), grid.first())
    assertTrue(LocalDate(2026, 10, 31) in grid)
  }

  @Test
  fun shortEventsGetMinimumHeight() {
    val seg = daySegments(listOf(ev("s", "23:55", "23:55")), day).single()
    assertEquals(15, seg.endMinute - seg.startMinute)
    assertTrue(seg.endMinute <= 24 * 60)
  }

  private fun dates(event: CalendarEvent, from: LocalDate, days: Int): List<String> =
    (0 until days).map { from.plusDays(it) }.filter { event.occursOn(it) }.map { it.toString() }

  @Test
  fun weeklyRepeatUsesChosenWeekdaysIntervalAndUntil() {
    val e = CalendarEvent(
      "Йога", "2026-10-26", start = "08:00", end = "09:00",
      repeat = Repeat(WEEKLY, interval = 2, days = listOf(1, 3), until = "2026-11-25")
    )
    assertEquals(
      listOf("2026-10-26", "2026-10-28", "2026-11-09", "2026-11-11", "2026-11-23", "2026-11-25"),
      dates(e, LocalDate(2026, 10, 1), 70)
    )
  }

  @Test
  fun monthlyRepeatSkipsMonthsWithoutTheDayAndExceptionsAreHidden() {
    val e = CalendarEvent("Оплата", "2026-01-31", repeat = Repeat(MONTHLY), exceptions = listOf("2026-05-31"))
    assertEquals(listOf("2026-01-31", "2026-03-31", "2026-07-31"), dates(e, LocalDate(2026, 1, 1), 212))
  }

  @Test
  fun birthdaysRepeatYearlyWithAgeAndLeapDayFallback() {
    val b = CalendarEvent("Анна", "2000-02-29", kind = BIRTHDAY, birthYear = 2000, repeat = Repeat(YEARLY))
    assertTrue(b.occursOn(LocalDate(2027, 2, 28)))
    assertTrue(b.occursOn(LocalDate(2028, 2, 29)))
    assertTrue(!b.occursOn(LocalDate(2028, 2, 28)))
    assertEquals(28, b.ageOn(LocalDate(2028, 2, 29)))
    assertEquals(listOf("b"), eventsOn(listOf(Item("b", b), Item("x", CalendarEvent("x", "2027-02-28"))), LocalDate(2027, 2, 28)).take(1).map { it.id })
  }

  @Test
  fun occurrencesAreShiftedForTheHourGridAndMultiDaySpans() {
    val night = Item(
      "n",
      CalendarEvent("Смена", "2026-10-26", endDate = "2026-10-27", start = "22:00", end = "06:00", repeat = Repeat(DAILY, interval = 3))
    )
    val seg = daySegments(listOf(night), LocalDate(2026, 10, 30)).single()
    assertEquals(0, seg.startMinute)
    assertEquals(6 * 60, seg.endMinute)
    assertEquals("2026-10-29", night.value.occurrenceOn(LocalDate(2026, 10, 30))!!.date)
    assertTrue(daySegments(listOf(night), LocalDate(2026, 10, 31)).isEmpty())
  }
}
