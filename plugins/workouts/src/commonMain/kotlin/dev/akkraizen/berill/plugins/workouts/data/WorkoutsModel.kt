package dev.akkraizen.berill.plugins.workouts.data

import dev.akkraizen.berill.api.PluginContext
import dev.akkraizen.berill.api.ui.nowMillis
import dev.akkraizen.berill.api.ui.today
import dev.akkraizen.berill.plugins.workouts.WorkoutsPlugin
import kotlin.math.floor
import kotlin.math.round
import kotlinx.serialization.Serializable

@Serializable
data class Exercise(
  val name: String = "",
  val sets: Int = 3,
  val reps: String = "8",
  val weight: Double? = null,
  val percent: Double? = null,
  val note: String? = null,
  val durationSeconds: Int? = null,
  val durationPerSet: Boolean = false,
  val restSeconds: Int = 60
)

@Serializable
data class WorkoutDay(val title: String = "", val exercises: List<Exercise> = emptyList())

@Serializable
data class Program(
  val title: String = "",
  val days: List<WorkoutDay> = emptyList(),
  val notes: String? = null,
  val createdAt: Long = 0
)

@Serializable
data class Progression(val kind: String = PROGRESSION_KG, val step: Double = 2.5, val repsStep: Int = 0)

const val PROGRESSION_KG = "kg"
const val PROGRESSION_PERCENT = "percent"
const val PROGRESSION_NONE = "none"

@Serializable
data class ExerciseOverride(
  val sets: Int? = null,
  val reps: String? = null,
  val weight: Double? = null,
  val durationSeconds: Int? = null,
  val percent: Double? = null
)

@Serializable
data class Cycle(
  val title: String = "",
  val weeks: Int = 4,
  val start: String? = null,
  val days: List<WorkoutDay> = emptyList(),
  val progression: Progression = Progression(),
  val deloadLast: Boolean = false,
  val overrides: Map<String, ExerciseOverride> = emptyMap(),
  val createdAt: Long = 0,
  val weekPrograms: Map<Int, ProgramWeek> = emptyMap()
)

@Serializable
data class ProgramWeek(val title: String = "", val days: List<WorkoutDay> = emptyList(), val programId: String? = null)

const val MAX_PROGRAM_DAYS = 7

internal fun List<WorkoutDay>.appendDay(day: WorkoutDay): List<WorkoutDay> =
  if (size < MAX_PROGRAM_DAYS) this + day else this

fun Cycle.daysForWeek(week: Int): List<WorkoutDay> = weekPrograms[week]?.days ?: days

fun Cycle.applyProgram(program: Program, selectedWeeks: Set<Int>, programId: String? = null): Cycle {
  require(program.days.size <= MAX_PROGRAM_DAYS)
  require(selectedWeeks.isNotEmpty() && selectedWeeks.all { it in 1..weeks })
  val plans = selectedWeeks.associateWith {
    ProgramWeek(program.title, program.days.map { day -> day.copy(exercises = day.exercises.map { it.copy() }) }, programId)
  }
  return copy(
    weekPrograms = weekPrograms + plans,
    overrides = overrides.filterKeys { it.substringBefore(':').toIntOrNull() !in selectedWeeks }
  )
}

fun overrideKey(week: Int, day: Int, exercise: Int) = "$week:$day:$exercise"

@Serializable
data class LoggedSet(val weight: Double? = null, val reps: Int? = null, val done: Boolean = false, val durationSeconds: Int? = null)

@Serializable
data class LoggedExercise(
  val name: String = "",
  val sets: List<LoggedSet> = emptyList(),
  val durationPerSet: Boolean = false,
  val restSeconds: Int = 60
)

@Serializable
data class WorkoutLog(
  val date: String = "",
  val title: String = "",
  val source: String? = null,
  val exercises: List<LoggedExercise> = emptyList(),
  val notes: String? = null,
  val finished: Boolean = false,
  val createdAt: Long = 0
)

data class PlannedExercise(
  val name: String,
  val sets: Int,
  val reps: String,
  val weight: Double?,
  val percent: Double?,
  val missingMax: Boolean,
  val overridden: Boolean,
  val deload: Boolean,
  val durationSeconds: Int? = null,
  val durationPerSet: Boolean = false,
  val restSeconds: Int = 60
)

fun roundTo(weight: Double, step: Double): Double = if (step <= 0.0) weight else round(weight / step) * step

fun Map<String, Double>.maxFor(name: String): Double? {
  val key = name.trim().lowercase()
  return entries.firstOrNull { it.key.trim().lowercase() == key }?.value
    ?: builtinExercises().firstOrNull { it.matches(name) }?.let { exercise ->
      entries.firstOrNull { exercise.matches(it.key) }?.value
    }
}

fun Cycle.planned(
  week: Int,
  day: Int,
  exercise: Int,
  maxes: Map<String, Double>,
  rounding: Double
): PlannedExercise {
  val base = daysForWeek(week)[day].exercises[exercise]
  if (base.durationSeconds != null) {
    val override = overrides[overrideKey(week, day, exercise)]
    return PlannedExercise(
      base.name, if (base.durationPerSet) (override?.sets ?: base.sets).coerceIn(1, 50) else 1,
      "", null, null, false, override != null, false,
      override?.durationSeconds ?: base.durationSeconds, base.durationPerSet, base.restSeconds
    )
  }
  val n = (week - 1).coerceAtLeast(0)
  val deload = deloadLast && weeks > 1 && week == weeks
  val growth = if (deload) 0 else n
  var percent = base.percent?.let { p ->
    if (progression.kind == PROGRESSION_PERCENT) p + progression.step * growth else p
  }
  val max = maxes.maxFor(base.name)
  var weight: Double? = when {
    percent != null -> max?.let { it * percent / 100.0 }
    base.weight != null ->
      base.weight + if (progression.kind == PROGRESSION_KG) progression.step * growth else 0.0

    else -> null
  }
  var sets = base.sets
  var reps = base.reps
  val numericReps = base.reps.trim().toIntOrNull()
  if (numericReps != null && progression.repsStep != 0 && !deload)
    reps = (numericReps + progression.repsStep * n).toString()
  if (deload) {
    weight = weight?.let { it * 0.9 }
    sets = (sets - 1).coerceAtLeast(1)
  }
  val o = overrides[overrideKey(week, day, exercise)]
  if (o != null) {
    o.sets?.let { sets = it }
    o.reps?.let { reps = it }
    when {
      o.percent != null -> {
        percent = o.percent
        weight = max?.let { it * o.percent / 100.0 }
      }
      o.weight != null -> {
        percent = null
        weight = o.weight
      }
    }
  }
  return PlannedExercise(
    name = base.name,
    sets = sets,
    reps = reps,
    weight = weight?.let { if (o?.weight != null && o.percent == null) it else roundTo(it, rounding) },
    percent = percent,
    missingMax = percent != null && max == null && o?.weight == null,
    overridden = o != null,
    deload = deload
  )
}

fun Cycle.currentWeek(daysSinceStart: Int?): Int? {
  if (daysSinceStart == null || daysSinceStart < 0) return null
  val week = daysSinceStart / 7 + 1
  return week.takeIf { it <= weeks }
}

fun targetReps(reps: String): Int? = Regex("""\d+""").find(reps)?.value?.toIntOrNull()

fun logFromPlan(exercises: List<PlannedExercise>): List<LoggedExercise> =
  exercises.map { exercise ->
    loggedExercise(exercise.name, exercise.sets, exercise.reps, exercise.weight,
      exercise.durationSeconds, exercise.durationPerSet, exercise.restSeconds)
  }

internal fun Exercise.resolvedWeight(maxes: Map<String, Double>, rounding: Double): Double? =
  if (durationSeconds != null) null else percent?.let { p -> maxes.maxFor(name)?.let { roundTo(it * p / 100.0, rounding) } }
    ?: weight.takeIf { percent == null }

fun logFromDay(day: WorkoutDay, maxes: Map<String, Double> = emptyMap(), rounding: Double = DEFAULT_ROUNDING): List<LoggedExercise> =
  day.exercises.map { exercise ->
    loggedExercise(exercise.name, exercise.sets, exercise.reps, exercise.resolvedWeight(maxes, rounding),
      exercise.durationSeconds, exercise.durationPerSet, exercise.restSeconds)
  }

private fun loggedExercise(
  name: String,
  sets: Int,
  reps: String,
  weight: Double?,
  durationSeconds: Int?,
  durationPerSet: Boolean,
  restSeconds: Int
): LoggedExercise {
  val loggedSets = if (durationSeconds != null) {
    val count = if (durationPerSet) sets.coerceIn(1, 50) else 1
    List(count) { LoggedSet(durationSeconds = durationSeconds) }
  } else {
    List(sets.coerceAtLeast(1)) { LoggedSet(weight, targetReps(reps)) }
  }
  return LoggedExercise(name, loggedSets, durationPerSet, restSeconds)
}

fun WorkoutLog.tonnage(): Double = exercises.sumOf { e -> e.sets.filter { it.done }.sumOf { (it.weight ?: 0.0) * (it.reps ?: 0) } }

fun WorkoutLog.doneSets(): Int = exercises.sumOf { e -> e.sets.count { it.done } }

fun WorkoutLog.totalSets(): Int = exercises.sumOf { it.sets.size }

fun estimateOneRepMax(weight: Double, reps: Int): Double? {
  if (weight <= 0 || reps <= 0) return null
  if (reps == 1) return weight
  val epley = weight * (1 + reps / 30.0)
  val brzycki = if (reps < 37) weight * 36.0 / (37 - reps) else epley
  return (epley + brzycki) / 2
}

fun repsAtPercent(percent: Double): Int = if (percent >= 100) 1 else floor(30 * (100 / percent - 1)).toInt().coerceAtLeast(1)

data class PlateLoad(val perSide: List<Double>, val total: Double, val remainder: Double)

fun loadPlates(target: Double, bar: Double, plates: List<Double>): PlateLoad {
  val sorted = plates.filter { it > 0 }.distinct().sortedDescending()
  var side = ((target - bar) / 2).coerceAtLeast(0.0)
  val result = mutableListOf<Double>()
  for (plate in sorted) {
    while (side + 1e-9 >= plate) {
      result += plate
      side -= plate
    }
  }
  val total = bar + result.sum() * 2
  return PlateLoad(result, total, ((target - total).coerceAtLeast(0.0) * 1000).let { round(it) / 1000 })
}

fun formatKg(weight: Double): String {
  val r = round(weight * 100) / 100
  return if (r % 1.0 == 0.0) r.toLong().toString() else r.toString().trimEnd('0').trimEnd('.')
}

val DEFAULT_PLATES = listOf(25.0, 20.0, 15.0, 10.0, 5.0, 2.5, 1.25)
const val DEFAULT_BAR = 20.0
const val DEFAULT_ROUNDING = 2.5

internal fun LoggedExercise.markAllSetsDone(): LoggedExercise =
  copy(sets = sets.map { it.copy(done = true) })

internal fun WorkoutLog.markAllSetsDone(): WorkoutLog =
  copy(exercises = exercises.map { it.markAllSetsDone() })

internal fun programs(ctx: PluginContext) = ctx.data.collection(WorkoutsPlugin.PROGRAMS, Program.serializer())

internal fun cycles(ctx: PluginContext) = linkedCycles(ctx)

internal fun logs(ctx: PluginContext) = ctx.data.collection(WorkoutsPlugin.LOGS, WorkoutLog.serializer())

internal fun startWorkout(ctx: PluginContext, title: String, source: String?, exercises: List<LoggedExercise>) {
  val id = ctx.data.newId()
  logs(ctx)
    .save(id, WorkoutLog(today().toString(), title, source, exercises, createdAt = nowMillis()))
  ctx.nav.openPlugin(WorkoutsPlugin.id, WorkoutsPlugin.LOG_ROUTE + id, WorkoutsPlugin.PAGE_JOURNAL)
}
