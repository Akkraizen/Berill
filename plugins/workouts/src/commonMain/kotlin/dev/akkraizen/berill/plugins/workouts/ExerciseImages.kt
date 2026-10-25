package dev.akkraizen.berill.plugins.workouts

import dev.akkraizen.berill.api.PluginContext
import dev.akkraizen.berill.api.list
import dev.akkraizen.berill.api.str
import dev.akkraizen.berill.api.strings
import dev.akkraizen.berill.api.ui.search.networkErrorText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

internal const val EXERCISE_DB_HOST = "raw.githubusercontent.com"
private const val DB_URL = "https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/dist/exercises.json"
private const val IMAGE_BASE = "https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/exercises/"

internal const val EXERCISE_IMAGES_KEY = "exerciseImages"

data class DbExercise(
  val id: String,
  val name: String,
  val images: List<String>,
  val equipment: String?,
  val instructions: List<String>
) {

  val urls: List<String>
    get() = images.map { IMAGE_BASE + it }
}

sealed interface ExerciseDbState {
  data object Idle : ExerciseDbState

  data object Loading : ExerciseDbState

  data class Failed(val message: String) : ExerciseDbState

  class Ready(val all: List<DbExercise>) : ExerciseDbState {
    val byId: Map<String, DbExercise> = all.associateBy { it.id }
    val byKey: Map<String, DbExercise> = buildMap {
      all.forEach { e ->
        getOrPut(normalize(e.name)) { e }
        getOrPut(normalize(e.id)) { e }
      }
    }
  }
}

internal object ExerciseDb {
  private val _state = MutableStateFlow<ExerciseDbState>(ExerciseDbState.Idle)
  val state: StateFlow<ExerciseDbState> = _state

  fun ensureLoaded(ctx: PluginContext, retry: Boolean = false) {
    val s = _state.value
    if (s is ExerciseDbState.Loading || s is ExerciseDbState.Ready) return
    if (s is ExerciseDbState.Failed && !retry) return
    _state.value = ExerciseDbState.Loading
    ctx.scope.launch {
      _state.value = try {
        val all = ctx.http.getJson(DB_URL).list().mapNotNull(::parse)
        if (all.isEmpty())
          ExerciseDbState.Failed(CatalogText.imagesEmpty())
        else
          ExerciseDbState.Ready(all)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Throwable) {
        ExerciseDbState.Failed(networkErrorText(e))
      }
    }
  }

  private fun parse(e: JsonElement): DbExercise? {
    val id = e.str("id") ?: return null
    val images = e.strings("images").filter { it.isNotBlank() && !it.contains("..") }
    if (images.isEmpty()) return null
    return DbExercise(
      id,
      e.str("name") ?: id,
      images,
      e.str("equipment"),
      e.strings("instructions")
    )
  }
}

internal fun normalize(s: String): String = s.lowercase().filter { it.isLetterOrDigit() }

private val HINTS: Map<String, List<String>> = mapOf(
  "bench_press" to listOf("Barbell_Bench_Press_-_Medium_Grip", "Barbell Bench Press"),
  "incline_bench" to listOf("Barbell_Incline_Bench_Press_-_Medium_Grip"),
  "db_bench" to listOf("Dumbbell Bench Press"),
  "incline_db_bench" to listOf("Incline Dumbbell Press"),
  "db_fly" to listOf("Dumbbell Flyes", "Dumbbell Fly"),
  "cable_crossover" to listOf("Cable Crossover"),
  "dips" to listOf("Dips - Chest Version", "Bench_Dips"),
  "push_up" to listOf("Pushups", "Push-Ups", "Push Up"),
  "chest_press_machine" to listOf("Leverage Chest Press", "Machine Bench Press"),
  "deadlift" to listOf("Barbell_Deadlift"),
  "pull_up" to listOf("Pullups", "Pull-Ups", "Band_Assisted_Pull-Up"),
  "chin_up" to listOf("Chin-Up", "Chin-Ups"),
  "barbell_row" to listOf("Bent_Over_Barbell_Row"),
  "db_row" to listOf("One-Arm Dumbbell Row", "Bent_Over_Two-Dumbbell_Row"),
  "lat_pulldown" to listOf("Wide-Grip Lat Pulldown", "Close-Grip Front Lat Pulldown"),
  "seated_cable_row" to listOf("Seated Cable Rows", "Seated Cable Row"),
  "t_bar_row" to listOf("T-Bar Row with Handle", "Lying T-Bar Row"),
  "back_extension" to
    listOf("Hyperextensions (Back Extensions)", "Hyperextensions With No Hyperextension Bench"),
  "ohp" to listOf("Barbell_Shoulder_Press", "Standing Military Press"),
  "db_shoulder_press" to
    listOf("Seated Dumbbell Press", "Dumbbell Shoulder Press", "Arnold_Dumbbell_Press"),
  "lateral_raise" to listOf("Side Lateral Raise"),
  "rear_delt_fly" to
    listOf("Reverse Flyes", "Bent_Over_Dumbbell_Rear_Delt_Raise_With_Head_On_Bench"),
  "face_pull" to listOf("Face Pull"),
  "upright_row" to listOf("Upright Barbell Row"),
  "barbell_curl" to listOf("Barbell_Curl"),
  "db_curl" to listOf("Dumbbell Bicep Curl", "Alternate_Incline_Dumbbell_Curl"),
  "hammer_curl" to listOf("Hammer Curls", "Alternate_Hammer_Curl"),
  "preacher_curl" to listOf("Preacher Curl"),
  "close_grip_bench" to listOf("Close-Grip Barbell Bench Press"),
  "skull_crusher" to
    listOf("EZ-Bar Skullcrusher", "Lying Triceps Press", "Band_Skull_Crusher"),
  "triceps_pushdown" to listOf("Triceps Pushdown", "Triceps Pushdown - Rope Attachment"),
  "overhead_extension" to
    listOf("Standing Dumbbell Triceps Extension", "Seated Triceps Press"),
  "squat" to listOf("Barbell_Squat"),
  "front_squat" to listOf("Front Barbell Squat", "Front Squat (Clean Grip)"),
  "leg_press" to listOf("Leg Press"),
  "lunge" to listOf("Dumbbell Lunges", "Barbell_Walking_Lunge"),
  "bulgarian_split_squat" to listOf("Split Squat with Dumbbells", "Barbell_Side_Split_Squat"),
  "leg_extension" to listOf("Leg Extensions"),
  "goblet_squat" to listOf("Goblet Squat"),
  "romanian_deadlift" to listOf("Romanian Deadlift", "Stiff-Legged Barbell Deadlift"),
  "hip_thrust" to listOf("Barbell_Hip_Thrust"),
  "leg_curl" to listOf("Lying Leg Curls", "Seated Leg Curl"),
  "kb_swing" to listOf("One-Arm Kettlebell Swings", "Two-Arm Kettlebell Swing"),
  "standing_calf_raise" to listOf("Standing Calf Raises", "Calf Raise On A Dumbbell"),
  "seated_calf_raise" to listOf("Seated Calf Raise", "Barbell_Seated_Calf_Raise"),
  "plank" to listOf("Plank"),
  "hanging_leg_raise" to listOf("Hanging Leg Raise"),
  "crunch" to listOf("Crunches", "Crunch - Hands Overhead"),
  "cable_crunch" to listOf("Cable Crunch", "Ab_Crunch_Machine"),
  "ab_wheel" to listOf("Ab_Roller"),
  "rowing" to listOf("Rowing, Stationary"),
  "running" to listOf("Running, Treadmill", "Jogging, Treadmill"),
  "burpee" to listOf("Burpee", "Burpees"),
  "farmers_walk" to listOf("Farmer's Walk", "Farmers Walk")
)

private val EQUIPMENT_WORDS = mapOf(
  EQ_BARBELL to "barbell",
  EQ_DUMBBELL to "dumbbell",
  EQ_MACHINE to "machine",
  EQ_CABLE to "cable",
  EQ_BODYWEIGHT to "body",
  EQ_KETTLEBELL to "kettlebell"
)

private fun tokens(s: String): Set<String> =
  s.lowercase()
    .split(Regex("[^a-z0-9]+"))
    .filter { it.length > 1 }
    .map { it.removeSuffix("s") }
    .toSet()

internal fun ExerciseDbState.Ready.imageFor(ex: CatalogExercise, overrides: Map<String, String>): DbExercise? {
  overrides[ex.id]?.let { chosen ->
    return if (chosen.isEmpty()) null else byId[chosen]
  }
  ex.builtinKey?.let { key ->
    HINTS[key].orEmpty().forEach { hint ->
      (byId[hint] ?: byKey[normalize(hint)])?.let {
        return it
      }
    }
  }
  byKey[normalize(ex.englishName)]?.let {
    return it
  }

  val wanted = tokens(ex.englishName)
  if (wanted.isEmpty()) return null
  val equipment = EQUIPMENT_WORDS[ex.equipment]
  return all.map { e ->
    val t = tokens(e.name)
    val score = (wanted intersect t).size.toDouble() / (wanted union t).size
    val bonus = if (equipment != null && e.equipment?.contains(equipment, ignoreCase = true) == true)
      0.1
    else
      0.0
    e to score + bonus
  }
    .filter { it.second >= 0.5 }
    .maxByOrNull { it.second }
    ?.first
}

internal fun ExerciseDbState.Ready.search(query: String, limit: Int = 60): List<DbExercise> {
  val words = query.lowercase().split(' ').filter { it.isNotBlank() }
  if (words.isEmpty()) return all.take(limit)
  return all.filter { e -> words.all { w -> e.name.lowercase().contains(w) } }.take(limit)
}

internal fun readImageOverrides(value: JsonElement?): Map<String, String> =
  (value as? JsonObject)
    ?.mapNotNull { (k, v) -> (v as? JsonPrimitive)?.contentOrNull?.let { k to it } }
    ?.toMap()
    .orEmpty()

internal fun setExerciseImage(ctx: PluginContext, exerciseId: String, dbId: String?) {
  val current = readImageOverrides(ctx.settings.get(EXERCISE_IMAGES_KEY))
  val next = if (dbId == null) current - exerciseId else current + (exerciseId to dbId)
  ctx.settings.set(EXERCISE_IMAGES_KEY, JsonObject(next.mapValues { JsonPrimitive(it.value) }))
}
