package dev.akkraizen.berill.plugins.workouts.data

import dev.akkraizen.berill.api.ui.toDateOrNull
import kotlinx.datetime.LocalDate

internal fun workoutHistoryValues(
  logs: List<WorkoutLog>,
  dates: List<LocalDate>,
  metric: Int,
  exercise: String? = null
): List<Double?> {
  val byDate = logs.filter { it.finished }.groupBy { it.date.toDateOrNull() }
  return dates.map { date ->
    val entries = byDate[date].orEmpty()
    when (metric) {
      0 -> entries.size.toDouble()
      1 -> entries.sumOf { it.tonnage() }.takeIf { it.isFinite() }
      else -> entries.flatMap { it.exercises }
        .filter { it.name.trim().equals(exercise?.trim(), ignoreCase = true) }
        .flatMap { it.sets }.filter { it.done }
        .mapNotNull { set ->
          when (metric) {
            2 -> set.weight?.takeIf { it >= 0 && it.isFinite() }
            3 -> set.reps?.takeIf { it > 0 }?.toDouble()
            5 -> set.durationSeconds?.takeIf { it > 0 }?.toDouble()
            else -> set.weight?.let { weight -> set.reps?.let { estimateOneRepMax(weight, it) } }
          }
        }.filter { it.isFinite() }.maxOrNull()
    }
  }
}
