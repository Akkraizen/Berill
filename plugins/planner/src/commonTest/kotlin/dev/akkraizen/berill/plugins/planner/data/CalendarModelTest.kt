package dev.akkraizen.berill.plugins.planner.data

import dev.akkraizen.berill.api.Item
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
}
