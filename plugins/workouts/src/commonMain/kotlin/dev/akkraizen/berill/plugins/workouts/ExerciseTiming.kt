package dev.akkraizen.berill.plugins.workouts

import kotlinx.serialization.Serializable

@Serializable
enum class ExerciseMode { REPETITIONS, DURATION, TIMED_SETS }

internal val Exercise.mode: ExerciseMode
  get() = if (durationSeconds == null) ExerciseMode.REPETITIONS
    else if (durationPerSet) ExerciseMode.TIMED_SETS else ExerciseMode.DURATION

internal val LoggedExercise.mode: ExerciseMode
  get() = if (sets.none { it.durationSeconds != null }) ExerciseMode.REPETITIONS
    else if (durationPerSet) ExerciseMode.TIMED_SETS else ExerciseMode.DURATION

internal fun Exercise.withMode(mode: ExerciseMode): Exercise = copy(
  durationSeconds = if (mode == ExerciseMode.REPETITIONS) null else durationSeconds ?: 60,
  durationPerSet = mode == ExerciseMode.TIMED_SETS
)

internal fun LoggedExercise.withMode(mode: ExerciseMode): LoggedExercise {
  if (mode == this.mode) return this
  val seconds = sets.firstOrNull()?.durationSeconds ?: 60
  return copy(
    durationPerSet = mode == ExerciseMode.TIMED_SETS,
    sets = when (mode) {
      ExerciseMode.REPETITIONS -> List(sets.size.coerceAtLeast(1)) { LoggedSet() }
      ExerciseMode.DURATION -> listOf(LoggedSet(durationSeconds = seconds))
      ExerciseMode.TIMED_SETS -> List(sets.size.coerceAtLeast(3)) { LoggedSet(durationSeconds = seconds) }
    }
  )
}

internal const val MAX_DURATION_SECONDS = 599999

internal fun formatDuration(seconds: Int): String =
  "${seconds.coerceAtLeast(0) / 60}:${(seconds.coerceAtLeast(0) % 60).toString().padStart(2, '0')}"

internal fun parseDuration(text: String): Int? {
  val parts = text.trim().split(':')
  val value = when (parts.size) {
    1 -> parts[0].takeIf { it.isNotEmpty() && it.all(Char::isDigit) }?.toLongOrNull()
    2 -> {
      val minutes = parts[0].takeIf { it.isNotEmpty() && it.all(Char::isDigit) }?.toLongOrNull() ?: return null
      val seconds = parts[1].takeIf { it.length == 2 && it.all(Char::isDigit) }?.toIntOrNull() ?: return null
      if (seconds !in 0..59 || minutes > MAX_DURATION_SECONDS / 60) return null
      minutes * 60 + seconds
    }
    else -> null
  } ?: return null
  return value.takeIf { it in 0..MAX_DURATION_SECONDS.toLong() }?.toInt()
}

internal data class WorkoutTimer(
  val exercise: Int,
  val set: Int,
  val remainingMillis: Long,
  val deadlineMillis: Long? = null,
  val resting: Boolean = false
) {
  fun remaining(now: Long): Long = (deadlineMillis?.minus(now) ?: remainingMillis).coerceAtLeast(0)
  fun resume(now: Long): WorkoutTimer = if (deadlineMillis != null) this
    else copy(deadlineMillis = now + remainingMillis)
  fun pause(now: Long): WorkoutTimer = copy(remainingMillis = remaining(now), deadlineMillis = null)
}

internal data class TimerCompletion(val workout: WorkoutLog, val next: WorkoutTimer? = null)

internal fun WorkoutLog.completeTimer(timer: WorkoutTimer, now: Long): TimerCompletion {
  if (finished || timer.resting) return TimerCompletion(this)
  val exercise = exercises.getOrNull(timer.exercise) ?: return TimerCompletion(this)
  val set = exercise.sets.getOrNull(timer.set) ?: return TimerCompletion(this)
  if (set.durationSeconds == null || set.done) return TimerCompletion(this)
  val updated = exercise.copy(sets = exercise.sets.mapIndexed { index, entry ->
    if (index == timer.set) entry.copy(done = true) else entry
  })
  val result = copy(exercises = exercises.mapIndexed { index, entry ->
    if (index == timer.exercise) updated else entry
  })
  val rest = if (exercise.durationPerSet && exercise.restSeconds > 0 &&
    updated.sets.drop(timer.set + 1).any { !it.done }) {
    WorkoutTimer(timer.exercise, timer.set, exercise.restSeconds.toLong() * 1000, resting = true).resume(now)
  } else null
  return TimerCompletion(result, rest)
}
