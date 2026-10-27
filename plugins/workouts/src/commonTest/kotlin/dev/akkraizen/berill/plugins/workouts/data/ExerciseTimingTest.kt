package dev.akkraizen.berill.plugins.workouts.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class ExerciseTimingTest {
  @Test
  fun durationInputSupportsMinutesAndSecondsAndRejectsMalformedValues() {
    assertEquals(1200, parseDuration("20:00"))
    assertEquals(45, parseDuration("0:45"))
    assertEquals(45, parseDuration("45"))
    assertEquals(0, parseDuration("0:00"))
    assertEquals("20:00", formatDuration(1200))
    listOf("", "-1", "1:60", "1:5", "1:00:00", "abc", "99999999999999999999999999", "10000:00")
      .forEach { assertNull(parseDuration(it), it) }
    assertEquals(MAX_DURATION_SECONDS, parseDuration(formatDuration(MAX_DURATION_SECONDS)))
  }

  @Test
  fun timedSetsPreserveSetsDurationAndRestAcrossProgramCycleAndJournal() {
    val ex = Exercise("Hold", sets = 3, durationSeconds = 45, durationPerSet = true, restSeconds = 30)
    val day = WorkoutDay(exercises = listOf(ex))
    val cycle = Cycle().applyProgram(Program(days = listOf(day)), setOf(2))
      .copy(overrides = mapOf(overrideKey(2, 0, 0) to ExerciseOverride(sets = 4, durationSeconds = 50)))
    val planned = cycle.planned(2, 0, 0, emptyMap(), 2.5)
    assertEquals(4, planned.sets)
    assertTrue(planned.durationPerSet)
    assertEquals(50, planned.durationSeconds)
    assertEquals(30, planned.restSeconds)
    val log = logFromPlan(listOf(planned)).single()
    assertEquals(4, log.sets.size)
    assertTrue(log.sets.all { it.durationSeconds == 50 && it.reps == null && it.weight == null })
    assertEquals(30, log.restSeconds)
    assertEquals(ExerciseMode.TIMED_SETS, log.mode)
    assertEquals(3, logFromDay(day).single().sets.size)
  }

  @Test
  fun singleDurationDoesNotTurnLegacySetsIntoIntervals() {
    val ex = Json.decodeFromString<Exercise>("""{"name":"Run","sets":3,"durationSeconds":1200}""")
    assertEquals(ExerciseMode.DURATION, ex.mode)
    assertEquals(1, logFromDay(WorkoutDay(exercises = listOf(ex))).single().sets.size)
    val cycle = Cycle(days = listOf(WorkoutDay(exercises = listOf(ex))))
    assertEquals(1, cycle.planned(1, 0, 0, emptyMap(), 2.5).sets)
  }

  @Test
  fun customCatalogTypeIsSerializedAndAppliedWhenPicking() {
    val custom = CustomExercise(name = "My hold", mode = ExerciseMode.TIMED_SETS)
    val restored = Json.decodeFromString<CustomExercise>(Json.encodeToString(custom))
    val entry = restored.toCatalog("custom")
    assertEquals(ExerciseMode.TIMED_SETS, entry.mode)
    val exercise = Exercise().withName(entry.name, entry.mode)
    assertEquals(ExerciseMode.TIMED_SETS, exercise.mode)
    assertEquals(3, logFromDay(WorkoutDay(exercises = listOf(exercise))).single().sets.size)
    val logged = LoggedExercise().withName(entry.name, entry.mode)
    assertEquals(3, logged.sets.size)
    assertEquals(ExerciseMode.TIMED_SETS, logged.mode)
    assertEquals(ExerciseMode.REPETITIONS, Json.decodeFromString<CustomExercise>("""{"name":"Old"}""").mode)
  }

  @Test
  fun switchingModesKeepsDurationAndLeavesRepetitionsUsable() {
    val timed = Exercise(durationSeconds = 45).withMode(ExerciseMode.TIMED_SETS)
    assertEquals(45, timed.durationSeconds)
    val reps = timed.withMode(ExerciseMode.REPETITIONS)
    assertNull(reps.durationSeconds)
    assertEquals(3, reps.sets)
    val log = LoggedExercise(sets = listOf(LoggedSet(durationSeconds = 45, done = true)))
    assertEquals(log, log.withMode(ExerciseMode.DURATION))
    val intervals = log.withMode(ExerciseMode.TIMED_SETS)
    assertEquals(3, intervals.sets.size)
    assertTrue(intervals.sets.all { it.durationSeconds == 45 && !it.done })
  }

  @Test
  fun timerPauseResumeUsesDeadlineWithoutAccumulatingTickDrift() {
    val running = WorkoutTimer(0, 1, 45000).resume(1000)
    assertEquals(45000L, running.remaining(1000))
    assertEquals(31000L, running.remaining(15000))
    val paused = running.pause(15000)
    assertNull(paused.deadlineMillis)
    assertEquals(31000L, paused.remaining(99000))
    val resumed = paused.resume(100000)
    assertEquals(1L, resumed.remaining(130999))
    assertEquals(0L, resumed.remaining(200000))
    assertEquals(resumed, resumed.resume(120000))
  }

  @Test
  fun completionMarksOnlyTargetSetAndStartsRestUntilLastInterval() {
    val exercise = LoggedExercise("Hold", List(3) { LoggedSet(durationSeconds = 45) },
      durationPerSet = true, restSeconds = 30)
    val log = WorkoutLog(exercises = listOf(exercise))
    val first = log.completeTimer(WorkoutTimer(0, 0, 0), 1000)
    assertEquals(listOf(true, false, false), first.workout.exercises.single().sets.map { it.done })
    assertTrue(first.next!!.resting)
    assertEquals(30000L, first.next!!.remaining(1000))
    val rested = first.workout.completeTimer(first.next!!, 31000)
    assertEquals(first.workout, rested.workout)
    assertNull(rested.next)
    val second = first.workout.completeTimer(WorkoutTimer(0, 1, 0), 32000)
    val last = second.workout.completeTimer(WorkoutTimer(0, 2, 0), 99000)
    assertTrue(last.workout.exercises.single().sets.all { it.done })
    assertNull(last.next)
    assertEquals(log, log.copy(finished = true).completeTimer(WorkoutTimer(0, 0, 0), 0).workout.copy(finished = false))
    assertEquals(log, log.completeTimer(WorkoutTimer(9, 0, 0), 0).workout)
    assertNull(log.copy(exercises = listOf(exercise.copy(restSeconds = 0)))
      .completeTimer(WorkoutTimer(0, 0, 0), 0).next)
  }

  @Test
  fun historyCountsOnlyCompletedIntervalsAndChartsDurationSeparately() {
    val sets = listOf(LoggedSet(durationSeconds = 45, done = true),
      LoggedSet(durationSeconds = 60, done = true), LoggedSet(durationSeconds = 90))
    val session = ExerciseSession("2026-10-23", sets)
    assertEquals(105, session.durationSeconds)
    assertEquals(60, session.longestSeconds)
    assertEquals(0.0, session.volume)
    assertNull(session.best)
    val entry = CustomExercise("Hold", mode = ExerciseMode.TIMED_SETS).toCatalog("hold")
    val log = WorkoutLog(date = session.date, finished = true,
      exercises = listOf(LoggedExercise("Hold", sets, durationPerSet = true)))
    assertEquals(105, entry.sessions(listOf(log)).single().durationSeconds)
    assertEquals(listOf(60.0), workoutHistoryValues(listOf(log), listOf(LocalDate(2026, 10, 23)), 5, "Hold"))
    assertEquals(listOf(null), workoutHistoryValues(listOf(log.copy(finished = false)), listOf(LocalDate(2026, 10, 23)), 5, "Hold"))
  }
}
