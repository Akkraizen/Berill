package dev.akkraizen.berill.plugins.workouts.data

import kotlinx.datetime.LocalDate
import kotlin.test.*

class MuscleBalanceTest {
  private val end = LocalDate(2026, 11, 1)
  private val catalog = listOf(
    CatalogExercise("press", "Press", listOf("press", "жим"), MUSCLE_CHEST, EQ_BARBELL, null, false),
    CatalogExercise("curl", "Curl", listOf("curl"), MUSCLE_BICEPS, EQ_DUMBBELL, null, false),
    CatalogExercise("run", "Run", listOf("run"), MUSCLE_CARDIO, EQ_OTHER, null, false),
  )
  private fun log(date: String, name: String = "press", done: Boolean = true) = WorkoutLog(date, finished = true,
    exercises = listOf(LoggedExercise(name, listOf(LoggedSet(20.0, 10, done)))))
  @Test fun comparisonHasEqualNonOverlappingInclusivePeriods() {
    val result = muscleBalance(listOf(log("2026-11-01"), log("2026-10-26"), log("2026-10-25"), log("2026-10-19"), log("2026-10-18"), log("2026-11-02")), catalog, end, 7, MuscleMetric.SETS)
    assertEquals(2.0, result.current[MuscleRegion.CHEST.ordinal])
    assertEquals(2.0, result.previous[MuscleRegion.CHEST.ordinal])
  }
  @Test fun ignoresUnfinishedWorkoutsUncheckedSetsAndMalformedDates() {
    val result = muscleBalance(listOf(log("2026-11-01").copy(finished = false), log("2026-11-01", done = false), log("not-a-date")), catalog, end, 7, MuscleMetric.SETS)
    assertTrue(result.current.all { it == 0.0 })
  }
  @Test fun matchesAliasesAndCustomGroupsAndReportsExcludedSets() {
    val custom = CatalogExercise("custom", "Curl", listOf("curl"), MUSCLE_TRICEPS, EQ_DUMBBELL, null, true)
    val result = muscleBalance(listOf(log("2026-11-01", " ЖИМ "), log("2026-11-01", "curl"), log("2026-11-01", "run"), log("2026-11-01", "unknown")), catalog + custom, end, 7, MuscleMetric.VOLUME)
    assertEquals(200.0, result.current[MuscleRegion.CHEST.ordinal])
    assertEquals(200.0, result.current[MuscleRegion.ARMS.ordinal])
    assertEquals(2, result.excludedSets)
  }
  @Test fun bodyweightAndTimedSetsCountButDoNotInventTonnage() {
    val entry = WorkoutLog("2026-11-01", finished = true, exercises = listOf(LoggedExercise("press", listOf(LoggedSet(null, 10, true), LoggedSet(durationSeconds = 30, done = true), LoggedSet(Double.NaN, 10, true)))))
    assertEquals(3.0, muscleBalance(listOf(entry), catalog, end, 7, MuscleMetric.SETS).current[0])
    assertEquals(0.0, muscleBalance(listOf(entry), catalog, end, 7, MuscleMetric.VOLUME).current[0])
  }
}
