package dev.akkraizen.berill.plugins.workouts.data

import dev.akkraizen.berill.api.PluginContext
import dev.akkraizen.berill.api.i18n.Tr
import dev.akkraizen.berill.plugins.workouts.WorkoutsPlugin
import dev.akkraizen.berill.plugins.workouts.i18n.pluginI18n
import kotlinx.serialization.Serializable

const val MUSCLE_CHEST = "chest"
const val MUSCLE_BACK = "back"
const val MUSCLE_SHOULDERS = "shoulders"
const val MUSCLE_BICEPS = "biceps"
const val MUSCLE_TRICEPS = "triceps"
const val MUSCLE_LEGS = "legs"
const val MUSCLE_GLUTES = "glutes"
const val MUSCLE_CALVES = "calves"
const val MUSCLE_CORE = "core"
const val MUSCLE_CARDIO = "cardio"
val MUSCLES = listOf(
  MUSCLE_CHEST,
  MUSCLE_BACK,
  MUSCLE_SHOULDERS,
  MUSCLE_BICEPS,
  MUSCLE_TRICEPS,
  MUSCLE_LEGS,
  MUSCLE_GLUTES,
  MUSCLE_CALVES,
  MUSCLE_CORE,
  MUSCLE_CARDIO
)

const val EQ_BARBELL = "barbell"
const val EQ_DUMBBELL = "dumbbell"
const val EQ_MACHINE = "machine"
const val EQ_CABLE = "cable"
const val EQ_BODYWEIGHT = "bodyweight"
const val EQ_KETTLEBELL = "kettlebell"
const val EQ_OTHER = "other"
val EQUIPMENT = listOf(EQ_BARBELL, EQ_DUMBBELL, EQ_MACHINE, EQ_CABLE, EQ_BODYWEIGHT, EQ_KETTLEBELL, EQ_OTHER)

@Serializable
data class CustomExercise(
  val name: String = "",
  val muscle: String = MUSCLE_CHEST,
  val equipment: String = EQ_BARBELL,
  val notes: String? = null,
  val createdAt: Long = 0,
  val mode: ExerciseMode = ExerciseMode.REPETITIONS
)

data class CatalogExercise(
  val id: String,
  val name: String,
  val aliases: List<String>,
  val muscle: String,
  val equipment: String,
  val description: String?,
  val custom: Boolean,
  val englishName: String = name,
  val builtinKey: String? = null,
  val mode: ExerciseMode = ExerciseMode.REPETITIONS
) {

  fun matches(exerciseName: String): Boolean {
    val n = exerciseName.trim().lowercase()
    return n.isNotEmpty() && aliases.any { it.trim().lowercase() == n }
  }
}

internal fun builtinMode(key: String?): ExerciseMode = when (key) {
  "plank", "farmers_walk", "wall_handstand", "side_plank", "hollow_hold", "l_sit", "mountain_climber", "high_knees", "battle_rope", "sled_push" -> ExerciseMode.TIMED_SETS
  "running", "rowing", "walking", "treadmill_walking", "treadmill_running", "cycling", "stationary_bike", "air_bike", "elliptical", "stair_climber", "swimming", "jump_rope", "ski_erg", "hiking" -> ExerciseMode.DURATION
  else -> ExerciseMode.REPETITIONS
}

internal fun Exercise.withName(name: String, mode: ExerciseMode? = null): Exercise {
  val selected = mode ?: builtinExercises().firstOrNull { it.matches(name) }?.mode
  return copy(name = name).let { if (selected != null && (name != this.name || mode != null)) it.withMode(selected) else it }
}

internal fun LoggedExercise.withName(name: String, mode: ExerciseMode? = null): LoggedExercise {
  val selected = mode ?: builtinExercises().firstOrNull { it.matches(name) }?.mode
  return copy(name = name).let { if (selected != null && (name != this.name || mode != null)) it.withMode(selected) else it }
}

const val BUILTIN_PREFIX = "b:"
const val CUSTOM_PREFIX = "c:"

private class Builtin(
  val key: String,
  val name: Tr,
  val muscle: String,
  val equipment: String,
  val description: Tr
)

private fun b(key: String, muscle: String, eq: String) =
  Builtin(key, pluginI18n.tr("exercise.$key.name"), muscle, eq, pluginI18n.tr("exercise.$key.description"))

private val BUILTINS = listOf(
  b("bench_press", MUSCLE_CHEST, EQ_BARBELL),
  b("incline_bench", MUSCLE_CHEST, EQ_BARBELL),
  b("db_bench", MUSCLE_CHEST, EQ_DUMBBELL),
  b("incline_db_bench", MUSCLE_CHEST, EQ_DUMBBELL),
  b("db_fly", MUSCLE_CHEST, EQ_DUMBBELL),
  b("cable_crossover", MUSCLE_CHEST, EQ_CABLE),
  b("dips", MUSCLE_CHEST, EQ_BODYWEIGHT),
  b("push_up", MUSCLE_CHEST, EQ_BODYWEIGHT),
  b("chest_press_machine", MUSCLE_CHEST, EQ_MACHINE),
  b("deadlift", MUSCLE_BACK, EQ_BARBELL),
  b("pull_up", MUSCLE_BACK, EQ_BODYWEIGHT),
  b("chin_up", MUSCLE_BACK, EQ_BODYWEIGHT),
  b("barbell_row", MUSCLE_BACK, EQ_BARBELL),
  b("db_row", MUSCLE_BACK, EQ_DUMBBELL),
  b("lat_pulldown", MUSCLE_BACK, EQ_CABLE),
  b("seated_cable_row", MUSCLE_BACK, EQ_CABLE),
  b("t_bar_row", MUSCLE_BACK, EQ_BARBELL),
  b("back_extension", MUSCLE_BACK, EQ_BODYWEIGHT),
  b("ohp", MUSCLE_SHOULDERS, EQ_BARBELL),
  b("db_shoulder_press", MUSCLE_SHOULDERS, EQ_DUMBBELL),
  b("lateral_raise", MUSCLE_SHOULDERS, EQ_DUMBBELL),
  b("rear_delt_fly", MUSCLE_SHOULDERS, EQ_DUMBBELL),
  b("face_pull", MUSCLE_SHOULDERS, EQ_CABLE),
  b("upright_row", MUSCLE_SHOULDERS, EQ_BARBELL),
  b("barbell_curl", MUSCLE_BICEPS, EQ_BARBELL),
  b("db_curl", MUSCLE_BICEPS, EQ_DUMBBELL),
  b("hammer_curl", MUSCLE_BICEPS, EQ_DUMBBELL),
  b("preacher_curl", MUSCLE_BICEPS, EQ_BARBELL),
  b("close_grip_bench", MUSCLE_TRICEPS, EQ_BARBELL),
  b("skull_crusher", MUSCLE_TRICEPS, EQ_BARBELL),
  b("triceps_pushdown", MUSCLE_TRICEPS, EQ_CABLE),
  b("overhead_extension", MUSCLE_TRICEPS, EQ_DUMBBELL),
  b("squat", MUSCLE_LEGS, EQ_BARBELL),
  b("front_squat", MUSCLE_LEGS, EQ_BARBELL),
  b("leg_press", MUSCLE_LEGS, EQ_MACHINE),
  b("lunge", MUSCLE_LEGS, EQ_DUMBBELL),
  b("bulgarian_split_squat", MUSCLE_LEGS, EQ_DUMBBELL),
  b("leg_extension", MUSCLE_LEGS, EQ_MACHINE),
  b("goblet_squat", MUSCLE_LEGS, EQ_KETTLEBELL),
  b("romanian_deadlift", MUSCLE_GLUTES, EQ_BARBELL),
  b("hip_thrust", MUSCLE_GLUTES, EQ_BARBELL),
  b("leg_curl", MUSCLE_GLUTES, EQ_MACHINE),
  b("kb_swing", MUSCLE_GLUTES, EQ_KETTLEBELL),
  b("standing_calf_raise", MUSCLE_CALVES, EQ_MACHINE),
  b("seated_calf_raise", MUSCLE_CALVES, EQ_MACHINE),
  b("plank", MUSCLE_CORE, EQ_BODYWEIGHT),
  b("hanging_leg_raise", MUSCLE_CORE, EQ_BODYWEIGHT),
  b("crunch", MUSCLE_CORE, EQ_BODYWEIGHT),
  b("cable_crunch", MUSCLE_CORE, EQ_CABLE),
  b("ab_wheel", MUSCLE_CORE, EQ_OTHER),
  b("rowing", MUSCLE_CARDIO, EQ_MACHINE),
  b("running", MUSCLE_CARDIO, EQ_OTHER),
  b("burpee", MUSCLE_CARDIO, EQ_BODYWEIGHT),
  b("farmers_walk", MUSCLE_CARDIO, EQ_DUMBBELL),
  b("wide_pull_up", MUSCLE_BACK, EQ_BODYWEIGHT),
  b("neutral_pull_up", MUSCLE_BACK, EQ_BODYWEIGHT),
  b("inverted_row", MUSCLE_BACK, EQ_BODYWEIGHT),
  b("ring_row", MUSCLE_BACK, EQ_OTHER),
  b("muscle_up", MUSCLE_BACK, EQ_BODYWEIGHT),
  b("scapular_pull_up", MUSCLE_BACK, EQ_BODYWEIGHT),
  b("diamond_push_up", MUSCLE_TRICEPS, EQ_BODYWEIGHT),
  b("incline_push_up", MUSCLE_CHEST, EQ_BODYWEIGHT),
  b("decline_push_up", MUSCLE_CHEST, EQ_BODYWEIGHT),
  b("archer_push_up", MUSCLE_CHEST, EQ_BODYWEIGHT),
  b("ring_push_up", MUSCLE_CHEST, EQ_OTHER),
  b("ring_dip", MUSCLE_TRICEPS, EQ_OTHER),
  b("pike_push_up", MUSCLE_SHOULDERS, EQ_BODYWEIGHT),
  b("handstand_push_up", MUSCLE_SHOULDERS, EQ_BODYWEIGHT),
  b("wall_handstand", MUSCLE_SHOULDERS, EQ_BODYWEIGHT),
  b("bodyweight_squat", MUSCLE_LEGS, EQ_BODYWEIGHT),
  b("pistol_squat", MUSCLE_LEGS, EQ_BODYWEIGHT),
  b("assisted_pistol_squat", MUSCLE_LEGS, EQ_BODYWEIGHT),
  b("bodyweight_lunge", MUSCLE_LEGS, EQ_BODYWEIGHT),
  b("step_up", MUSCLE_LEGS, EQ_BODYWEIGHT),
  b("nordic_curl", MUSCLE_GLUTES, EQ_BODYWEIGHT),
  b("glute_bridge", MUSCLE_GLUTES, EQ_BODYWEIGHT),
  b("single_leg_calf_raise", MUSCLE_CALVES, EQ_BODYWEIGHT),
  b("side_plank", MUSCLE_CORE, EQ_BODYWEIGHT),
  b("hollow_hold", MUSCLE_CORE, EQ_BODYWEIGHT),
  b("l_sit", MUSCLE_CORE, EQ_BODYWEIGHT),
  b("hanging_knee_raise", MUSCLE_CORE, EQ_BODYWEIGHT),
  b("reverse_crunch", MUSCLE_CORE, EQ_BODYWEIGHT),
  b("dead_bug", MUSCLE_CORE, EQ_BODYWEIGHT),
  b("walking", MUSCLE_CARDIO, EQ_OTHER),
  b("treadmill_walking", MUSCLE_CARDIO, EQ_MACHINE),
  b("treadmill_running", MUSCLE_CARDIO, EQ_MACHINE),
  b("cycling", MUSCLE_CARDIO, EQ_OTHER),
  b("stationary_bike", MUSCLE_CARDIO, EQ_MACHINE),
  b("air_bike", MUSCLE_CARDIO, EQ_MACHINE),
  b("elliptical", MUSCLE_CARDIO, EQ_MACHINE),
  b("stair_climber", MUSCLE_CARDIO, EQ_MACHINE),
  b("swimming", MUSCLE_CARDIO, EQ_OTHER),
  b("jump_rope", MUSCLE_CARDIO, EQ_OTHER),
  b("ski_erg", MUSCLE_CARDIO, EQ_MACHINE),
  b("hiking", MUSCLE_CARDIO, EQ_OTHER),
  b("jumping_jack", MUSCLE_CARDIO, EQ_BODYWEIGHT),
  b("mountain_climber", MUSCLE_CARDIO, EQ_BODYWEIGHT),
  b("high_knees", MUSCLE_CARDIO, EQ_BODYWEIGHT),
  b("battle_rope", MUSCLE_CARDIO, EQ_OTHER),
  b("sled_push", MUSCLE_CARDIO, EQ_OTHER),
  b("db_row_chest_supported", MUSCLE_BACK, EQ_DUMBBELL),
  b("db_pullover", MUSCLE_CHEST, EQ_DUMBBELL),
  b("db_lateral_lunge", MUSCLE_LEGS, EQ_DUMBBELL),
  b("cable_pull_through", MUSCLE_GLUTES, EQ_CABLE)
)

fun builtinExercises(): List<CatalogExercise> =
  BUILTINS.map {
    CatalogExercise(
      id = BUILTIN_PREFIX + it.key,
      name = it.name(),
      aliases = it.name.translations.values.toList(),
      muscle = it.muscle,
      equipment = it.equipment,
      description = it.description(),
      custom = false,
      englishName = it.name.get("en"),
      builtinKey = it.key,
      mode = builtinMode(it.key)
    )
  }

fun CustomExercise.toCatalog(id: String) =
  CatalogExercise(
    id = CUSTOM_PREFIX + id,
    name = name,
    aliases = listOf(name),
    muscle = muscle,
    equipment = equipment,
    description = notes,
    custom = true,
    mode = mode
  )

internal fun customExercises(ctx: PluginContext) = ctx.data.collection(WorkoutsPlugin.EXERCISES, CustomExercise.serializer())

internal const val HIDDEN_EXERCISES_KEY = "hiddenExercises"

data class ExerciseSession(val date: String, val sets: List<LoggedSet>) {
  val best: Pair<LoggedSet, Double>?
    get() =
      sets
        .filter { it.done && (it.weight ?: 0.0) > 0 && (it.reps ?: 0) > 0 }
        .mapNotNull { s -> estimateOneRepMax(s.weight!!, s.reps!!)?.let { s to it } }
        .maxByOrNull { it.second }

  val volume: Double
    get() = sets.filter { it.done }.sumOf { (it.weight ?: 0.0) * (it.reps ?: 0) }

  val durationSeconds: Int
    get() = sets.filter { it.done }.sumOf { it.durationSeconds ?: 0 }

  val longestSeconds: Int?
    get() = sets.filter { it.done }.mapNotNull { it.durationSeconds }.maxOrNull()

  val doneSets: Int
    get() = sets.count { it.done }
}

fun CatalogExercise.sessions(logs: List<WorkoutLog>): List<ExerciseSession> =
  logs
    .flatMap { log ->
      log.exercises.filter { matches(it.name) }.map { ExerciseSession(log.date, it.sets) }
    }
    .filter { s -> s.sets.any { it.done } }
    .sortedByDescending { it.date }
