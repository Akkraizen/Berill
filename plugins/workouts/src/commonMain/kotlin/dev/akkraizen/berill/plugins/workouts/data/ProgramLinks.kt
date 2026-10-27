package dev.akkraizen.berill.plugins.workouts.data

import dev.akkraizen.berill.api.DataCollection
import dev.akkraizen.berill.api.Item
import dev.akkraizen.berill.api.PluginContext
import dev.akkraizen.berill.plugins.workouts.WorkoutsPlugin
import kotlinx.coroutines.flow.combine

internal fun Cycle.linkedPrograms(programs: List<Item<Program>>): Cycle {
  var updatedOverrides = overrides
  val updatedWeeks = weekPrograms.mapValues { (week, snapshot) ->
    val source = snapshot.findSource(programs) ?: return@mapValues snapshot
    val updated = ProgramWeek(source.value.title, source.value.days, source.id)
    if (updated.days != snapshot.days) {
      updatedOverrides = updatedOverrides.remapWeek(week, snapshot.days, updated.days)
    }
    updated
  }
  return copy(weekPrograms = updatedWeeks, overrides = updatedOverrides)
}

private fun ProgramWeek.findSource(programs: List<Item<Program>>): Item<Program>? =
  if (programId != null) programs.firstOrNull { it.id == programId }
  else programs.filter { it.value.title == title && it.value.days == days }.singleOrNull()

private fun Map<String, ExerciseOverride>.remapWeek(
  week: Int,
  previousDays: List<WorkoutDay>,
  updatedDays: List<WorkoutDay>
): Map<String, ExerciseOverride> {
  val weekOverrides = filterKeys { it.substringBefore(':') == week.toString() }
  val remapped = weekOverrides.mapNotNull { (key, value) ->
    remapExercise(key, week, previousDays, updatedDays)?.let { it to value }
  }
  return this - weekOverrides.keys + remapped
}

private fun remapExercise(
  key: String,
  week: Int,
  previousDays: List<WorkoutDay>,
  updatedDays: List<WorkoutDay>
): String? {
  val indices = key.split(':').map { it.toIntOrNull() }
  val previousDayIndex = indices.getOrNull(1) ?: return null
  val previousExerciseIndex = indices.getOrNull(2) ?: return null
  val previousDay = previousDays.getOrNull(previousDayIndex) ?: return null
  val previousExercise = previousDay.exercises.getOrNull(previousExerciseIndex) ?: return null

  val matchingDays = updatedDays.indices.filter { updatedDays[it].title == previousDay.title }
  val dayIndex = matchingDays.singleOrNull() ?: previousDayIndex.takeIf {
    previousDays.size == updatedDays.size && updatedDays.getOrNull(it)?.exerciseNames() == previousDay.exerciseNames()
  } ?: return null
  val updatedDay = updatedDays[dayIndex]
  val matchingExercises = updatedDay.exercises.indices.filter { updatedDay.exercises[it].name == previousExercise.name }
  val exerciseIndex = matchingExercises.singleOrNull() ?: previousExerciseIndex.takeIf {
    updatedDay.exerciseNames() == previousDay.exerciseNames()
  } ?: return null
  return overrideKey(week, dayIndex, exerciseIndex)
}

private fun WorkoutDay.exerciseNames(): List<String> = exercises.map { it.name }

internal fun linkedCycles(ctx: PluginContext): DataCollection<Cycle> {
  val stored = ctx.data.collection(WorkoutsPlugin.CYCLES, Cycle.serializer())
  val sources = programs(ctx)
  return object : DataCollection<Cycle> by stored {
    override fun get(id: String) = stored.get(id)?.linkedPrograms(sources.all())

    override fun all(): List<Item<Cycle>> {
      val programs = sources.all()
      return stored.all().map { it.copy(value = it.value.linkedPrograms(programs)) }
    }

    override fun observe() = combine(stored.observe(), sources.observe()) { values, programs ->
      values.map { it.copy(value = it.value.linkedPrograms(programs)) }
    }

    override fun observe(id: String) = combine(stored.observe(id), sources.observe()) { value, programs ->
      value?.linkedPrograms(programs)
    }
  }
}

internal fun saveProgram(ctx: PluginContext, id: String, value: Program) {
  val collection = programs(ctx)
  val previous = collection.all()
  val next = previous.filterNot { it.id == id } + Item(id, value)
  val stored = ctx.data.collection(WorkoutsPlugin.CYCLES, Cycle.serializer())
  val changes = stored.all().mapNotNull { item ->
    val updated = item.value.linkedPrograms(previous).linkedPrograms(next)
    if (updated == item.value) null else item.copy(value = updated)
  }
  collection.save(id, value)
  changes.forEach { stored.save(it.id, it.value) }
}
