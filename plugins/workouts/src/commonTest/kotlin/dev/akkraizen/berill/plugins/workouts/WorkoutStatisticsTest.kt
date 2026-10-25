package dev.akkraizen.berill.plugins.workouts

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class WorkoutStatisticsTest {
  private val dates = listOf(LocalDate(2026, 10, 21), LocalDate(2026, 10, 22))
  private val finished = WorkoutLog(date = dates[0].toString(), finished = true, exercises = listOf(
    LoggedExercise(" Squat ", listOf(LoggedSet(50.0, 8, true), LoggedSet(60.0, 6, true), LoggedSet(100.0, 1, false))),
    LoggedExercise("Press", listOf(LoggedSet(80.0, 3, true)))
  ))

  @Test
  fun volumeOnlyIncludesCompletedSetsFromFinishedWorkouts() {
    val entries = listOf(finished, finished.copy(finished = false))
    assertEquals(listOf(1.0, 0.0), workoutHistoryValues(entries, dates, 0))
    assertEquals(listOf(1000.0, 0.0), workoutHistoryValues(entries, dates, 1))
  }

  @Test
  fun exerciseRecordsUseTheBestDailySetAndMatchNormalizedNames() {
    val entries = listOf(finished, finished.copy(exercises = listOf(LoggedExercise("squat", listOf(LoggedSet(65.0, 4, true))))))
    assertEquals(listOf(65.0, null), workoutHistoryValues(entries, dates, 2, "SQUAT"))
    assertEquals(listOf(8.0, null), workoutHistoryValues(entries, dates, 3, "squat"))
    assertEquals(listOf(null, null), workoutHistoryValues(entries, dates, 2, "Deadlift"))
  }

  @Test
  fun estimatedMaxUsesCompletedSetsAndKeepsMissingWeightsMissing() {
    val entry = finished.copy(exercises = listOf(LoggedExercise("Squat", listOf(LoggedSet(60.0, 6, true)))))
    assertEquals(listOf(estimateOneRepMax(60.0, 6), null), workoutHistoryValues(listOf(entry), dates, 4, "Squat"))
    val noWeight = entry.copy(exercises = listOf(LoggedExercise("Squat", listOf(LoggedSet(null, 10, true)))))
    assertEquals(listOf(null, null), workoutHistoryValues(listOf(noWeight), dates, 2, "Squat"))
  }
}
