package dev.akkraizen.berill.plugins.workouts

import dev.akkraizen.berill.plugins.workouts.data.*
import dev.akkraizen.berill.plugins.workouts.ui.*
import kotlin.test.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

class QuickWorkoutTest {
  @Test fun repeatUsesSelectedDateAndResetsCompletionWithoutLosingWeightsOrTiming() {
    val previous = WorkoutLog("2026-10-28", "Strength", "program", listOf(
      LoggedExercise("Squat", listOf(LoggedSet(80.0, 8, true))),
      LoggedExercise("Plank", listOf(LoggedSet(done = true, durationSeconds = 60)), durationPerSet = true, restSeconds = 90)
    ), finished = true, createdAt = 123)
    val template = previous.asQuickTemplate("2026-10-29")
    assertEquals("2026-10-29", template.date)
    assertEquals("program", template.source)
    assertNull(template.activeId)
    assertTrue(template.exercises.flatMap { it.sets }.none { it.done })
    assertEquals(80.0, template.exercises[0].sets[0].weight)
    assertEquals(60, template.exercises[1].sets[0].durationSeconds)
    assertEquals(90, template.exercises[1].restSeconds)
    assertEquals(template, Json.decodeFromString<QuickWorkoutDraft>(Json.encodeToString(template)))
  }
}
