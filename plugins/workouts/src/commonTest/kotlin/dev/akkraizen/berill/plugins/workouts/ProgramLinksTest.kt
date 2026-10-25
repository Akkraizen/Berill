package dev.akkraizen.berill.plugins.workouts

import dev.akkraizen.berill.api.Item
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ProgramLinksTest {
  private val squat = Exercise("Squat", weight = 50.0)
  private val press = Exercise("Press", percent = 70.0)
  private val program = Program("Strength", listOf(WorkoutDay("Day A", listOf(squat, press))))

  @Test
  fun linkedWeeksFollowSourceWhileOtherWeeksAndSettingsStayUnchanged() {
    val override = ExerciseOverride(percent = 80.0, sets = 4)
    val cycle = Cycle(weeks = 4, days = listOf(WorkoutDay("Default")), progression = Progression(step = 5.0))
      .applyProgram(program, setOf(1, 3), "source")
      .copy(overrides = mapOf(overrideKey(3, 0, 1) to override))
    val changed = program.copy(title = "New title", days = listOf(WorkoutDay("Day A", listOf(press.copy(percent = 75.0), squat.copy(weight = 60.0)))))
    val refreshed = cycle.linkedPrograms(listOf(Item("source", changed)))
    assertEquals(changed.days, refreshed.daysForWeek(1))
    assertEquals(changed.days, refreshed.daysForWeek(3))
    assertEquals(cycle.days, refreshed.daysForWeek(2))
    assertEquals("New title", refreshed.weekPrograms.getValue(1).title)
    assertEquals(mapOf(overrideKey(3, 0, 0) to override), refreshed.overrides)
    assertEquals(cycle.progression, refreshed.progression)
    assertEquals(80.0, refreshed.planned(3, 0, 0, mapOf("Press" to 100.0), 2.5).weight)
    assertEquals(refreshed, refreshed.linkedPrograms(listOf(Item("source", changed))))
    assertEquals(refreshed, Json.decodeFromString<Cycle>(Json.encodeToString(refreshed)))
  }

  @Test
  fun removedExercisesLoseOnlyTheirOverridesAndMissingSourceKeepsSnapshot() {
    val cycle = Cycle().applyProgram(program, setOf(1), "source").copy(overrides = mapOf(
      overrideKey(1, 0, 0) to ExerciseOverride(weight = 90.0),
      overrideKey(1, 0, 1) to ExerciseOverride(percent = 80.0)
    ))
    val changed = program.copy(days = listOf(WorkoutDay("Day A", listOf(press))))
    val refreshed = cycle.linkedPrograms(listOf(Item("source", changed)))
    assertEquals(mapOf(overrideKey(1, 0, 0) to ExerciseOverride(percent = 80.0)), refreshed.overrides)
    assertEquals(cycle, cycle.linkedPrograms(emptyList()))
    assertEquals(cycle, cycle.linkedPrograms(listOf(Item("other", program))))
  }

  @Test
  fun legacySnapshotsLinkOnlyWhenSourceIsUnambiguous() {
    val legacy = Cycle().applyProgram(program, setOf(1))
    assertEquals("source", legacy.linkedPrograms(listOf(Item("source", program))).weekPrograms.getValue(1).programId)
    assertEquals(legacy, legacy.linkedPrograms(listOf(Item("one", program), Item("two", program))))
    assertEquals(legacy, legacy.linkedPrograms(listOf(Item("source", program.copy(title = "Other")))))
    val old = Json.decodeFromString<ProgramWeek>("""{"title":"Old","days":[]}""")
    assertNull(old.programId)
    assertEquals("Old", old.title)
  }
}
