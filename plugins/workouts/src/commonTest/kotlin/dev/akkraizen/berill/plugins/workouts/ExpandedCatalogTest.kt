package dev.akkraizen.berill.plugins.workouts

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExpandedCatalogTest {
  @Test
  fun catalogEntriesHaveUniqueKeysAndLocalizedDescriptions() {
    val all = builtinExercises()
    assertTrue(all.size >= 100)
    assertEquals(all.size, all.map { it.id }.distinct().size)
    all.forEach { exercise ->
      assertFalse(exercise.name.isBlank(), exercise.id)
      assertFalse(exercise.englishName.startsWith("exercise."), exercise.id)
      assertTrue(exercise.aliases.distinct().size >= 2, exercise.id)
      assertFalse(exercise.description.isNullOrBlank(), exercise.id)
      assertFalse(exercise.description!!.startsWith("exercise."), exercise.id)
      assertTrue(exercise.muscle in MUSCLES, exercise.id)
      assertTrue(exercise.equipment in EQUIPMENT, exercise.id)
    }
  }

  @Test
  fun newExercisesUseRepetitionsHoldsAndCardioDuration() {
    val all = builtinExercises().associateBy { it.builtinKey }
    listOf("muscle_up", "pistol_squat", "ring_dip", "diamond_push_up", "jumping_jack").forEach {
      assertEquals(ExerciseMode.REPETITIONS, all.getValue(it).mode)
    }
    listOf("side_plank", "l_sit", "wall_handstand", "hollow_hold", "battle_rope").forEach { key ->
      val catalog = all.getValue(key)
      assertEquals(ExerciseMode.TIMED_SETS, catalog.mode)
      val exercise = Exercise().withName(catalog.name, catalog.mode)
      assertTrue(exercise.durationPerSet)
      assertEquals(60, exercise.durationSeconds)
    }
    listOf("cycling", "swimming", "walking", "jump_rope", "air_bike", "elliptical", "ski_erg").forEach { key ->
      val catalog = all.getValue(key)
      assertEquals(ExerciseMode.DURATION, catalog.mode)
      val exercise = Exercise().withName(catalog.name, catalog.mode)
      assertFalse(exercise.durationPerSet)
      assertEquals(1, logFromDay(WorkoutDay(exercises = listOf(exercise))).single().sets.size)
    }
  }
}
