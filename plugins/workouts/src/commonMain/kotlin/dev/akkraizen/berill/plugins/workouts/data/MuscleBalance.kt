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

data class WorkoutComparisonWeek(val start: LocalDate, val end: LocalDate, val through: LocalDate)
fun workoutComparisonWeek(today: LocalDate, offset: Int): WorkoutComparisonWeek {
  require(offset <= 0)
  val anchor = today.plus(offset * 7, DateTimeUnit.DAY)
  val start = anchor.minus(anchor.dayOfWeek.ordinal, DateTimeUnit.DAY)
  val end = start.plus(6, DateTimeUnit.DAY)
  return WorkoutComparisonWeek(start, end, minOf(end, today))
}

data class PlannedMuscleLoad(val values: List<Double>, val excludedSets: Int, val missingVolumeSets: Int)
fun plannedMuscleLoad(program: Program, catalog: List<CatalogExercise>, metric: MuscleMetric,
  maxes: Map<String, Double> = emptyMap(), rounding: Double = DEFAULT_ROUNDING): PlannedMuscleLoad {
  val values = DoubleArray(MuscleRegion.entries.size)
  val exercises = catalog.sortedByDescending { it.custom }
  var excluded = 0
  var missing = 0
  for (exercise in program.days.flatMap { it.exercises }) {
    val count = if (exercise.durationSeconds != null && !exercise.durationPerSet) 1 else exercise.sets.coerceIn(1, 50)
    val region = exercises.firstOrNull { it.matches(exercise.name) }?.let { muscleRegion(it.muscle) }
    if (region == null) { excluded += count; continue }
    val value = when (metric) {
      MuscleMetric.SETS -> count.toDouble()
      MuscleMetric.VOLUME -> {
        val weight = exercise.resolvedWeight(maxes, rounding)?.takeIf { it > 0 && it.isFinite() }
        val reps = if (exercise.durationSeconds == null) exercise.reps.trim().toIntOrNull()?.takeIf { it > 0 } else null
        val volume = if (weight != null && reps != null) weight * reps * count else null
        if (volume == null || !volume.isFinite()) { missing += count; 0.0 } else volume
      }
    }
    values[region.ordinal] = (values[region.ordinal] + value).takeIf { it.isFinite() } ?: values[region.ordinal]
  }
  return PlannedMuscleLoad(values.toList(), excluded, missing)
}
