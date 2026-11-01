package dev.akkraizen.berill.plugins.workouts.data

import dev.akkraizen.berill.api.ui.toDateOrNull
import kotlinx.datetime.*

enum class MuscleRegion { CHEST, SHOULDERS, ARMS, CORE, LEGS, GLUTES, BACK }
enum class MuscleMetric { SETS, VOLUME }
data class MuscleBalance(val current: List<Double>, val previous: List<Double>, val excludedSets: Int)

private fun muscleRegion(muscle: String): MuscleRegion? = when (muscle) {
  MUSCLE_CHEST -> MuscleRegion.CHEST
  MUSCLE_SHOULDERS -> MuscleRegion.SHOULDERS
  MUSCLE_BICEPS, MUSCLE_TRICEPS -> MuscleRegion.ARMS
  MUSCLE_CORE -> MuscleRegion.CORE
  MUSCLE_LEGS, MUSCLE_CALVES -> MuscleRegion.LEGS
  MUSCLE_GLUTES -> MuscleRegion.GLUTES
  MUSCLE_BACK -> MuscleRegion.BACK
  else -> null
}

fun muscleBalance(logs: List<WorkoutLog>, catalog: List<CatalogExercise>, end: LocalDate, days: Int, metric: MuscleMetric): MuscleBalance {
  require(days in 1..366)
  val start = end.minus(days - 1, DateTimeUnit.DAY)
  val previousStart = start.minus(days, DateTimeUnit.DAY)
  val current = DoubleArray(MuscleRegion.entries.size)
  val previous = DoubleArray(MuscleRegion.entries.size)
  var excluded = 0
  val exercises = catalog.sortedByDescending { it.custom }
  for (log in logs) {
    if (!log.finished) continue
    val date = log.date.toDateOrNull() ?: continue
    if (date < previousStart || date > end) continue
    val target = if (date >= start) current else previous
    for (exercise in log.exercises) {
      val completed = exercise.sets.filter { it.done }
      val region = exercises.firstOrNull { it.matches(exercise.name) }?.let { muscleRegion(it.muscle) }
      if (region == null) { if (date >= start) excluded += completed.size; continue }
      val value = when (metric) {
        MuscleMetric.SETS -> completed.size.toDouble()
        MuscleMetric.VOLUME -> completed.sumOf { set ->
          val weight = set.weight?.takeIf { it > 0 && it.isFinite() } ?: 0.0
          val reps = set.reps?.takeIf { it > 0 } ?: 0
          (weight * reps).takeIf { it.isFinite() } ?: 0.0
        }
      }
      target[region.ordinal] = (target[region.ordinal] + value).takeIf { it.isFinite() } ?: target[region.ordinal]
    }
  }
  return MuscleBalance(current.toList(), previous.toList(), excluded)
}
