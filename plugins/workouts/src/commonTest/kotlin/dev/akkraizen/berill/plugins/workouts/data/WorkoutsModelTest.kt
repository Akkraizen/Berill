package dev.akkraizen.berill.plugins.workouts.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class WorkoutsModelTest {
  @Test
  fun weeklyPercentOverrideUsesMaxWithoutAdditionalProgressionOrDeload() {
    val cycle = Cycle(weeks = 4, days = listOf(WorkoutDay(exercises = listOf(Exercise("Squat", percent = 60.0)))),
      progression = Progression(PROGRESSION_PERCENT, 5.0), deloadLast = true,
      overrides = mapOf(overrideKey(4, 0, 0) to ExerciseOverride(percent = 73.0)))
    val planned = cycle.planned(4, 0, 0, mapOf("Squat" to 120.0), 2.5)
    assertEquals(73.0, planned.percent)
    assertEquals(87.5, planned.weight)
    assertFalse(planned.missingMax)
    assertEquals(87.5, logFromPlan(listOf(planned)).single().sets.first().weight)
    val missing = cycle.planned(4, 0, 0, emptyMap(), 2.5)
    assertTrue(missing.missingMax)
    assertEquals(null, missing.weight)
    val kilograms = cycle.copy(overrides = mapOf(overrideKey(4, 0, 0) to ExerciseOverride(weight = 42.0)))
      .planned(4, 0, 0, emptyMap(), 2.5)
    assertEquals(42.0, kilograms.weight)
    assertEquals(null, kilograms.percent)
    assertFalse(kilograms.missingMax)
    val legacy = Json.decodeFromString<ExerciseOverride>("""{"weight":50.0}""")
    assertEquals(null, legacy.percent)
    assertEquals(cycle, Json.decodeFromString<Cycle>(Json.encodeToString(cycle)))
  }

  @Test
  fun programPercentUsesSavedMaxAndRoundingWithoutChangingTemplate() {
    val exercise = Exercise("Squat", sets = 2, reps = "8", percent = 73.0)
    val day = WorkoutDay(exercises = listOf(exercise))
    assertEquals(87.5, logFromDay(day, mapOf(" squat " to 120.0), 2.5).single().sets.first().weight)
    assertEquals(null, logFromDay(day).single().sets.first().weight)
    assertEquals(73.0, exercise.percent)
    assertEquals(45.0, logFromDay(WorkoutDay(exercises = listOf(Exercise("Squat", weight = 45.0))), mapOf("Squat" to 120.0)).single().sets.first().weight)
  }

  @Test
  fun catalogMaxMatchesAcrossLanguages() {
    val exercise = builtinExercises().first { it.builtinKey == "bench_press" }
    val alias = exercise.aliases.first { it != exercise.englishName }
    assertEquals(100.0, mapOf(exercise.englishName to 100.0).maxFor(alias))
    assertEquals(70.0, Exercise(alias, percent = 70.0).resolvedWeight(mapOf(exercise.englishName to 100.0), 2.5))
  }

  @Test
  fun markingExercisePreservesSetValuesAndDoesNotMutateOriginal() {
    val original = LoggedExercise("Plank", listOf(
      LoggedSet(weight = 20.0, reps = 8, done = true),
      LoggedSet(durationSeconds = 90)
    ), durationPerSet = true, restSeconds = 45)
    val completed = original.markAllSetsDone()
    assertEquals(original.copy(sets = listOf(
      original.sets[0], original.sets[1].copy(done = true)
    )), completed)
    assertFalse(original.sets[1].done)
    assertEquals(completed, completed.markAllSetsDone())
    assertEquals(LoggedExercise(), LoggedExercise().markAllSetsDone())
  }

  @Test
  fun markingWorkoutCompletesAllExercisesWithoutFinishingWorkout() {
    val original = WorkoutLog(
      date = "2026-10-25", title = "Workout", source = "program:1:0",
      exercises = listOf(
        LoggedExercise("Squat", listOf(LoggedSet(50.0, 10))),
        LoggedExercise("Plank", listOf(LoggedSet(durationSeconds = 60))),
        LoggedExercise("Empty")
      ), notes = "Keep", createdAt = 42
    )
    val completed = original.markAllSetsDone()
    assertEquals(2, completed.doneSets())
    assertEquals(original.copy(exercises = original.exercises.map { it.markAllSetsDone() }), completed)
    assertFalse(completed.finished)
    assertEquals(0, original.doneSets())
    assertEquals(completed, completed.markAllSetsDone())
    assertEquals(WorkoutLog(), WorkoutLog().markAllSetsDone())
    assertTrue(original.copy(finished = true).markAllSetsDone().finished)
  }

  @Test
  fun timedExercisesKeepDurationThroughPlansOverridesAndLogs() {
    val exercise = Exercise("Plank", weight = 20.0, percent = 70.0, durationSeconds = 90)
    val day = WorkoutDay(exercises = listOf(exercise))
    val cycle = Cycle(weeks = 4, days = listOf(day), deloadLast = true,
      overrides = mapOf(overrideKey(4, 0, 0) to ExerciseOverride(durationSeconds = 120)))
    val planned = cycle.planned(4, 0, 0, emptyMap(), 2.5)
    assertEquals(120, planned.durationSeconds)
    assertEquals(null, planned.weight)
    assertFalse(planned.missingMax)
    assertFalse(planned.deload)
    val logged = logFromPlan(listOf(planned)).single().sets.single()
    assertEquals(120, logged.durationSeconds)
    assertEquals(null, logged.reps)
    assertEquals(null, logged.weight)
    assertEquals(90, logFromDay(day).single().sets.single().durationSeconds)
    assertEquals(exercise, Json.decodeFromString<Exercise>(Json.encodeToString(exercise)))
  }

  @Test
  fun legacyExercisesStillUseSetsAndRepetitions() {
    val legacy = Json.decodeFromString<Exercise>("""{"name":"Squat","sets":4,"reps":"10","weight":50.0}""")
    assertEquals(null, legacy.durationSeconds)
    val logged = logFromDay(WorkoutDay(exercises = listOf(legacy))).single()
    assertEquals(4, logged.sets.size)
    assertEquals(10, logged.sets.first().reps)
    assertEquals(null, logged.sets.first().durationSeconds)
  }

  private val base = WorkoutDay("Base", listOf(Exercise("Squat", weight = 50.0)))
  private val program = Program("Strength", listOf(WorkoutDay("Upper", listOf(Exercise("Press", weight = 30.0)))))

  @Test
  fun dayLimitPreventsAddingAnEighthDayWithoutTruncatingLegacyData() {
    val days = List(MAX_PROGRAM_DAYS) { base }
    assertEquals(7, days.appendDay(base).size)
    assertEquals(7, days.dropLast(1).appendDay(base).size)
    val legacy = List(9) { base }
    assertEquals(legacy, legacy.appendDay(base))
  }

  @Test
  fun assignsOneProgramToSeveralWeeksAndKeepsOtherPlans() {
    val oldPlan = ProgramWeek("Other", listOf(base))
    val cycle = Cycle(days = listOf(base), weekPrograms = mapOf(4 to oldPlan), overrides = mapOf(
      "1:0:0" to ExerciseOverride(weight = 90.0),
      "2:0:0" to ExerciseOverride(weight = 100.0)
    ))
    val result = cycle.applyProgram(program, setOf(2, 3))
    assertEquals(program.days, result.daysForWeek(2))
    assertEquals(program.days, result.daysForWeek(3))
    assertEquals(listOf(base), result.daysForWeek(1))
    assertEquals(oldPlan, result.weekPrograms[4])
    assertEquals(setOf("1:0:0"), result.overrides.keys)
    assertEquals("Strength", result.weekPrograms[2]?.title)
    assertTrue(cycle.weekPrograms.keys == setOf(4))
  }

  @Test
  fun snapshotsDoNotFollowChangesToSourceLists() {
    val exercises = mutableListOf(Exercise("Press"))
    val days = mutableListOf(WorkoutDay("Upper", exercises))
    val result = Cycle().applyProgram(Program(days = days), setOf(1, 2))
    exercises.clear()
    days.clear()
    assertEquals("Press", result.daysForWeek(1).single().exercises.single().name)
    assertEquals("Press", result.daysForWeek(2).single().exercises.single().name)
  }

  @Test
  fun rejectsInvalidAssignments() {
    val cycle = Cycle()
    assertFailsWith<IllegalArgumentException> { cycle.applyProgram(program, emptySet()) }
    assertFailsWith<IllegalArgumentException> { cycle.applyProgram(program, setOf(0)) }
    assertFailsWith<IllegalArgumentException> { cycle.applyProgram(program, setOf(5)) }
    assertFailsWith<IllegalArgumentException> {
      cycle.applyProgram(Program(days = List(8) { base }), setOf(1))
    }
  }

  @Test
  fun weekPlansFeedProgressionDeloadAndWorkoutLogs() {
    val cycle = Cycle(days = listOf(base), deloadLast = true).applyProgram(program, setOf(2, 4))
    val planned = cycle.planned(2, 0, 0, emptyMap(), 2.5)
    assertEquals("Press", planned.name)
    assertEquals(32.5, planned.weight)
    assertFalse(planned.deload)
    assertEquals("Press", logFromPlan(listOf(planned)).single().name)
    val deload = cycle.planned(4, 0, 0, emptyMap(), 0.0)
    assertTrue(deload.deload)
    assertEquals(27.0, deload.weight)
    assertEquals(2, deload.sets)
    assertEquals("Squat", cycle.planned(1, 0, 0, emptyMap(), 2.5).name)
  }

  @Test
  fun oldCyclesStillDecodeAndAssignedWeeksRoundTrip() {
    val legacy = Json.decodeFromString<Cycle>("""{"title":"Old","weeks":4}""")
    assertTrue(legacy.weekPrograms.isEmpty())
    val cycle = legacy.applyProgram(program, setOf(1, 3))
    assertEquals(cycle, Json.decodeFromString<Cycle>(Json.encodeToString(cycle)))
  }
}
