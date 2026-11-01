package dev.akkraizen.berill.plugins.workouts

import dev.akkraizen.berill.plugins.workouts.data.*
import kotlin.test.*

class PreviousWeightsTest {
  @Test fun calendarDateOrdersImportedWorkoutsWithoutCreationTimestamps() {
    val old = WorkoutLog(date = "2026-01-01", exercises = listOf(LoggedExercise("Bench", listOf(LoggedSet(40.0, 8, true)))), finished = true, createdAt = 1000)
    val recent = old.copy(date = "2026-01-02", createdAt = 0, exercises = listOf(LoggedExercise("Bench", listOf(LoggedSet(50.0, 8, true)))))
    assertEquals(50.0, withPreviousWeights(listOf(LoggedExercise("Bench", listOf(LoggedSet(reps = 8)))), listOf(old, recent)).single().sets.single().weight)
  }
  @Test fun priorLoadsFillMissingWeightsButPreserveExplicitAndTimedSets() {
    val old = WorkoutLog(exercises = listOf(LoggedExercise("Bench", listOf(LoggedSet(40.0, 8, true)))), finished = true, createdAt = 1)
    val current = LoggedExercise("bench", listOf(LoggedSet(reps = 8), LoggedSet(0.0, 8), LoggedSet(30.0, 8), LoggedSet(durationSeconds = 30)))
    val result = withPreviousWeights(listOf(current), listOf(old)).single().sets
    assertEquals(listOf(40.0, 0.0, 30.0, null), result.map { it.weight })
  }
}
