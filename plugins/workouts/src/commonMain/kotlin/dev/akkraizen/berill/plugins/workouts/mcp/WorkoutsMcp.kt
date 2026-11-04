package dev.akkraizen.berill.plugins.workouts.mcp

import dev.akkraizen.berill.api.Item
import dev.akkraizen.berill.api.PluginContext
import dev.akkraizen.berill.api.mcp.*
import dev.akkraizen.berill.api.ui.toDateOrNull
import dev.akkraizen.berill.plugins.workouts.WorkoutsPlugin
import dev.akkraizen.berill.plugins.workouts.data.*
import dev.akkraizen.berill.plugins.workouts.ui.setMax
import dev.akkraizen.berill.plugins.workouts.ui.weekNow
import dev.akkraizen.berill.plugins.workouts.ui.workoutPrefs
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal class WorkoutsMcp(private val ctx: PluginContext) : McpSource {
  override val pluginId = ctx.pluginId
  private val editable: List<McpCollection> = listOf(
    TypedMcpCollection(ctx, WorkoutsPlugin.CYCLES, Cycle.serializer(), validate = { require(it.title.isNotBlank() && it.weeks in 1..104) { "Cycle title and valid number of weeks required" } }),
    TypedMcpCollection(ctx, WorkoutsPlugin.PROGRAMS, Program.serializer(), validate = { require(it.title.isNotBlank() && it.days.size <= MAX_PROGRAM_DAYS) { "Program title and at most $MAX_PROGRAM_DAYS days required" } }),
    TypedMcpCollection(ctx, WorkoutsPlugin.LOGS, WorkoutLog.serializer(), validate = { require(it.title.isNotBlank() && it.date.toDateOrNull() != null && it.exercises.all { e -> e.sets.all { s -> s.weight == null || (s.weight.isFinite() && s.weight >= 0) } }) { "Valid workout title, date and weights required" } })
  )
  override val collections: List<McpCollection> = editable + (WorkoutsPlugin.collections - editable.map { it.name }.toSet()).map { ReadOnlyMcpCollection(ctx, it) }

  override val actions: List<McpAction> = listOf(
    McpAction(
      "week_plan",
      "Shows the computed plan of a training cycle: for a week, every day and exercise with sets, reps and the working weight " +
        "resolved from 1RM percentages, progression, deload and per-week overrides. Without `week` it uses the current week " +
        "(or week 1). With all_weeks=true it lists which program each week uses instead.",
      mcpInput {
        string("cycle", "Cycle id; defaults to the cycle running now, otherwise the newest one")
        integer("week", "Week number, 1-based", min = 1, max = 104)
        integer("day", "Only this day index, 0-based", min = 0, max = MAX_PROGRAM_DAYS - 1)
        boolean("all_weeks", "Summarize every week instead of one")
      },
      readOnly = true
    ) { args -> weekPlan(args) },
    McpAction(
      "assign_program",
      "Copies a saved program into the chosen weeks of a cycle, exactly like Assign program in the app. The weeks stay linked: " +
        "later edits of the program update them. Overrides of those weeks are cleared. Create the program first with " +
        "berill_workouts_edit if needed.",
      mcpInput {
        string("cycle", "Cycle id", required = true)
        string("program", "Program id from workout_programs", required = true)
        integers("weeks", "Week numbers, 1-based; use from/to for a range")
        integer("from", "First week of a range", min = 1, max = 104)
        integer("to", "Last week of a range", min = 1, max = 104)
      }
    ) { args -> assignProgram(args) },
    McpAction(
      "reset_weeks",
      "Removes the program assigned to the given weeks and their overrides, so they fall back to the cycle's own days.",
      mcpInput {
        string("cycle", "Cycle id", required = true)
        integers("weeks", "Week numbers, 1-based", required = true)
      },
      destructive = true
    ) { args -> resetWeeks(args) },
    McpAction(
      "set_override",
      "Changes one exercise in one week only (sets, reps, weight in kg or percent of 1RM, duration) without touching the program. " +
        "Pass clear=true to remove the override.",
      mcpInput {
        string("cycle", "Cycle id", required = true)
        integer("week", "Week number, 1-based", required = true, min = 1, max = 104)
        integer("day", "Day index, 0-based", required = true, min = 0, max = MAX_PROGRAM_DAYS - 1)
        string("exercise", "Exercise name, or its 0-based index in the day", required = true)
        integer("sets", "Sets", min = 1, max = 50)
        string("reps", "Reps, e.g. 5 or 8-10")
        number("weight", "Working weight in kg")
        number("percent", "Percent of the exercise 1RM")
        integer("duration_seconds", "Duration for timed exercises", min = 1, max = 86400)
        boolean("clear", "Remove the override")
      }
    ) { args -> setOverride(args) },
    McpAction(
      "set_one_rep_max",
      "Saves the one-repetition maximum used to turn percentages into kilograms. Pass weight 0 to remove it.",
      mcpInput {
        string("exercise", "Exercise name", required = true)
        number("weight", "1RM in kg", required = true)
      }
    ) { args -> setOneRepMax(args) },
  )

  private fun cycleItem(id: String?): Item<Cycle> {
    val all = cycles(ctx).all()
    if (id != null) return all.firstOrNull { it.id == id }?.let { Item(it.id, it.value) } ?: throw McpException("Cycle not found", McpErrorCode.NOT_FOUND)
    return all.firstOrNull { it.value.weekNow() != null }
      ?: all.maxByOrNull { it.value.createdAt }
      ?: throw McpException("There are no cycles yet; create one in workout_cycles", McpErrorCode.NOT_FOUND)
  }

  private fun weekPlan(args: JsonObject): JsonElement {
    val item = cycleItem(args.mcpOptionalString("cycle"))
    val cycle = item.value
    val prefs = workoutPrefs(ctx)
    val current = cycle.weekNow()
    return buildJsonObject {
      put("cycle", item.id)
      put("title", cycle.title)
      put("weeks", cycle.weeks)
      cycle.start?.let { put("start", it) }
      current?.let { put("currentWeek", it) }
      if (args.mcpOptionalBoolean("all_weeks") == true) {
        put("plan", buildJsonArray {
          for (week in 1..cycle.weeks) add(buildJsonObject {
            put("week", week)
            val assigned = cycle.weekPrograms[week]
            put("program", assigned?.title ?: cycle.title)
            assigned?.programId?.let { put("programId", it) }
            put("days", JsonArray(cycle.daysForWeek(week).map { JsonPrimitive(it.title) }))
            put("overrides", cycle.overrides.keys.count { it.substringBefore(':') == week.toString() })
            put("deload", cycle.deloadLast && cycle.weeks > 1 && week == cycle.weeks)
          })
        })
        return@buildJsonObject
      }
      val week = args.mcpOptionalInt("week", 1..cycle.weeks) ?: current ?: 1
      val onlyDay = args.mcpOptionalInt("day", 0 until MAX_PROGRAM_DAYS)
      put("week", week)
      val assigned = cycle.weekPrograms[week]
      put("program", assigned?.title ?: cycle.title)
      assigned?.programId?.let { put("programId", it) }
      put("deload", cycle.deloadLast && cycle.weeks > 1 && week == cycle.weeks)
      val days = cycle.daysForWeek(week)
      if (onlyDay != null && onlyDay !in days.indices) throw McpException("Week $week has ${days.size} days")
      put("days", buildJsonArray {
        days.forEachIndexed { d, day ->
          if (onlyDay != null && d != onlyDay) return@forEachIndexed
          add(buildJsonObject {
            put("day", d)
            put("title", day.title)
            put("exercises", buildJsonArray {
              day.exercises.indices.forEach { e ->
                val p = cycle.planned(week, d, e, prefs.maxes, prefs.rounding)
                add(buildJsonObject {
                  put("index", e)
                  put("name", p.name)
                  put("sets", p.sets)
                  if (p.reps.isNotBlank()) put("reps", p.reps)
                  p.weight?.let { put("weightKg", it) }
                  p.percent?.let { put("percentOf1RM", it) }
                  p.durationSeconds?.let { put("durationSeconds", it) }
                  put("restSeconds", p.restSeconds)
                  if (p.missingMax) put("missingOneRepMax", true)
                  if (p.overridden) put("overridden", true)
                })
              }
            })
          })
        }
      })
    }
  }

  private fun weeksArgument(args: JsonObject, cycle: Cycle): Set<Int> {
    val listed = args.mcpIntList("weeks").orEmpty()
    val from = args.mcpOptionalInt("from", 1..104)
    val to = args.mcpOptionalInt("to", 1..104)
    val range = when {
      from != null || to != null -> ((from ?: 1)..(to ?: cycle.weeks)).toList()
      else -> emptyList()
    }
    val weeks = (listed + range).toSet()
    if (weeks.isEmpty()) throw McpException("Pass weeks or from/to")
    val outside = weeks.filter { it !in 1..cycle.weeks }
    if (outside.isNotEmpty()) throw McpException("Cycle has ${cycle.weeks} weeks; invalid: ${outside.sorted()}")
    return weeks
  }

  private fun saveCycle(id: String, change: (Cycle) -> Cycle): Cycle {
    val collection = cycles(ctx)
    val fresh = collection.get(id) ?: throw McpException("Cycle not found", McpErrorCode.NOT_FOUND)
    val next = change(fresh)
    collection.save(id, next)
    return next
  }

  private fun assignProgram(args: JsonObject): JsonElement {
    val item = cycleItem(args.mcpString("cycle"))
    val programId = args.mcpString("program")
    val program = programs(ctx).get(programId) ?: throw McpException("Program not found", McpErrorCode.NOT_FOUND)
    if (program.days.isEmpty()) throw McpException("Program has no days")
    if (program.days.size > MAX_PROGRAM_DAYS) throw McpException("Program has more than $MAX_PROGRAM_DAYS days")
    val weeks = weeksArgument(args, item.value)
    saveCycle(item.id) { it.applyProgram(program, weeks, programId) }
    return weekPlan(buildJsonObject { put("cycle", item.id); put("all_weeks", true) })
  }

  private fun resetWeeks(args: JsonObject): JsonElement {
    val item = cycleItem(args.mcpString("cycle"))
    val weeks = weeksArgument(args, item.value)
    saveCycle(item.id) { fresh ->
      fresh.copy(
        weekPrograms = fresh.weekPrograms - weeks,
        overrides = fresh.overrides.filterKeys { it.substringBefore(':').toIntOrNull() !in weeks }
      )
    }
    return weekPlan(buildJsonObject { put("cycle", item.id); put("all_weeks", true) })
  }

  private fun setOverride(args: JsonObject): JsonElement {
    val item = cycleItem(args.mcpString("cycle"))
    val cycle = item.value
    val week = args.mcpInt("week", 1..cycle.weeks)
    val days = cycle.daysForWeek(week)
    val day = args.mcpInt("day", days.indices)
    val exercises = days[day].exercises
    val reference = args.mcpString("exercise").trim()
    val exercise = reference.toIntOrNull()?.takeIf { it in exercises.indices }
      ?: exercises.indexOfFirst { it.name.equals(reference, ignoreCase = true) }.takeIf { it >= 0 }
      ?: throw McpException("No exercise `$reference` on day $day of week $week")
    val key = overrideKey(week, day, exercise)
    if (args.mcpOptionalBoolean("clear") == true) {
      saveCycle(item.id) { it.copy(overrides = it.overrides - key) }
    } else {
      val weight = args.mcpOptionalDouble("weight")
      val percent = args.mcpOptionalDouble("percent")
      if (weight != null && weight < 0) throw McpException("Weight must not be negative")
      if (percent != null && percent !in 1.0..150.0) throw McpException("Percent must be between 1 and 150")
      val override = ExerciseOverride(
        sets = args.mcpOptionalInt("sets", 1..50),
        reps = args.mcpOptionalString("reps")?.trim()?.takeIf { it.isNotEmpty() },
        weight = weight,
        durationSeconds = args.mcpOptionalInt("duration_seconds", 1..86400),
        percent = percent
      )
      if (override == ExerciseOverride()) throw McpException("Pass at least one value to override, or clear=true")
      saveCycle(item.id) { it.copy(overrides = it.overrides + (key to override)) }
    }
    return weekPlan(buildJsonObject { put("cycle", item.id); put("week", week); put("day", day) })
  }

  private fun setOneRepMax(args: JsonObject): JsonElement {
    val exercise = args.mcpString("exercise").trim()
    val weight = args.mcpOptionalDouble("weight") ?: throw McpException("Missing number argument `weight`")
    if (weight < 0 || weight > 1000) throw McpException("Weight must be between 0 and 1000 kg")
    setMax(ctx, exercise, weight.takeIf { it > 0 })
    return buildJsonObject {
      put("maxes", JsonObject(workoutPrefs(ctx).maxes.mapValues { JsonPrimitive(it.value) }))
    }
  }
}
