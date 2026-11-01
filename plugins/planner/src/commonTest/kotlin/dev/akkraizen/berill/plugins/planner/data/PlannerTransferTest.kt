package dev.akkraizen.berill.plugins.planner.data

import kotlinx.datetime.LocalDate
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.*
import kotlin.test.*

class PlannerTransferTest {
 private val original = LocalDate(2026, 10, 31)
 private val next = LocalDate(2026, 11, 1)
 @Test fun movedTaskIsPlannedAndUnfinishedOnBothDates() {
  val task = PlannerTask(text = "Call", date = original.toString()).rescheduled(next)
  assertEquals(next.toString(), task.date)
  assertFalse(task.done)
  val oldStats = plannerDayTotals(listOf(task), original)
  val newStats = plannerDayTotals(listOf(task), next)
  assertEquals(PlannerDayTotals(1, 0, 1), oldStats)
  assertEquals(1, oldStats.unfinished)
  assertEquals(PlannerDayTotals(1, 0, 0), newStats)
  assertEquals(1, newStats.unfinished)
 }
 @Test fun laterCompletionDoesNotRewriteTheMissedDay() {
  val task = PlannerTask(date = original.toString()).rescheduled(next).copy(done = true)
  assertEquals(1, plannerDayTotals(listOf(task), original).unfinished)
  assertEquals(0, plannerDayTotals(listOf(task), original).completed)
  assertEquals(PlannerDayTotals(1, 1, 0), plannerDayTotals(listOf(task), next))
 }
 @Test fun repeatedMovesKeepEachScheduledDateOnceAcrossWeeks() {
  val last = LocalDate(2026, 11, 2)
  val task = PlannerTask(date = original.toString()).rescheduled(next).rescheduled(last)
  assertEquals(listOf(original.toString(), next.toString()), task.deferredDates)
  for (date in listOf(original, next, last)) {
   val stats = plannerDayTotals(listOf(task), date)
   assertEquals(1, stats.planned)
   assertEquals(1, stats.unfinished)
  }
  val returned = task.rescheduled(original).copy(done = true)
  assertEquals(1, plannerDayTotals(listOf(returned), original).planned)
  assertEquals(1, plannerDayTotals(listOf(returned), original).completed)
 }
 @Test fun sameDateIsIdempotentAndMovingResetsCompletion() {
  val completed = PlannerTask(date = original.toString(), done = true)
  assertEquals(completed, completed.rescheduled(original))
  assertFalse(completed.rescheduled(next).done)
  assertEquals(emptyList(), PlannerTask(week = "2026-W44").deferredDates)
 }
 @Test fun legacyTasksAndHistoryRoundTripWithoutMigration() {
  val legacy = Json.decodeFromString<PlannerTask>("""{"text":"Existing","date":"2026-10-31","done":false}""")
  assertTrue(legacy.deferredDates.isEmpty())
  assertEquals(PlannerDayTotals(1, 0, 0), plannerDayTotals(listOf(legacy), original))
  val task = legacy.rescheduled(next)
  assertEquals(task, Json.decodeFromString<PlannerTask>(Json.encodeToString(task)))
  assertTrue(Json.parseToJsonElement(Json.encodeToString(task)).jsonObject.containsKey("deferred_dates"))
 }
}
