package dev.akkraizen.berill.plugins.habits.mcp

import dev.akkraizen.berill.api.PluginContext
import dev.akkraizen.berill.api.mcp.*
import dev.akkraizen.berill.api.ui.today
import dev.akkraizen.berill.api.ui.toDateOrNull
import dev.akkraizen.berill.plugins.habits.HabitsPlugin
import dev.akkraizen.berill.plugins.habits.data.*
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal class HabitsMcp(private val ctx: PluginContext) : McpSource {
  override val pluginId = ctx.pluginId
  private val editable: List<McpCollection> = listOf(
    HabitsDayMcp(ctx),
    TypedMcpCollection(ctx, HabitsPlugin.HABITS, HabitDef.serializer(), validate = { require(it.label.isNotBlank() && it.defaultDuration > 0) { "Habit label and positive target required" } })
  )
  override val collections: List<McpCollection> = editable + (HabitsPlugin.collections - editable.map { it.name }.toSet()).map { ReadOnlyMcpCollection(ctx, it) }

  override val actions: List<McpAction> = listOf(
    McpAction(
      "status",
      "Shows every active habit for a day (default today) with its target, progress, whether it is done and the current " +
        "streak of consecutive completed days.",
      mcpInput { string("date", "Day YYYY-MM-DD") },
      readOnly = true
    ) { args -> status(args) },
    McpAction(
      "mark",
      "Records progress for a habit on a day (default today). Without `value` the habit is marked complete with its target " +
        "amount; `value` 0 clears it; `add` true adds `value` to what is already recorded.",
      mcpInput {
        string("habit", "Habit id or exact label", required = true)
        string("date", "Day YYYY-MM-DD")
        integer("value", "Progress amount in the habit's unit", min = 0, max = 100000)
        boolean("add", "Add to the recorded amount instead of replacing it")
      }
    ) { args -> mark(args) }
  )

  private val data get() = HabitsData(ctx)

  private fun date(args: JsonObject): LocalDate =
    args.mcpOptionalString("date")?.let { it.toDateOrNull() ?: throw McpException("`date` must be YYYY-MM-DD") } ?: today()

  private fun streak(habitId: String, def: HabitDef, until: LocalDate): Int {
    val days = data.days
    var day = until
    if (days.get(day.toString()).valueOf(habitId) < def.defaultDuration) day = day.minus(1, DateTimeUnit.DAY)
    var count = 0
    while (count < 3660 && days.get(day.toString()).valueOf(habitId) >= def.defaultDuration) {
      count++
      day = day.minus(1, DateTimeUnit.DAY)
    }
    return count
  }

  private fun status(args: JsonObject): JsonElement {
    val date = date(args)
    val record = data.days.get(date.toString())
    val habits = data.defs.all().active()
    return buildJsonObject {
      put("date", date.toString())
      put("done", habits.count { record.valueOf(it.id) >= it.value.defaultDuration })
      put("total", habits.size)
      put("habits", JsonArray(habits.map { habit ->
        val value = record.valueOf(habit.id)
        buildJsonObject {
          put("id", habit.id)
          put("label", habit.value.label)
          put("unit", habit.value.unit)
          put("target", habit.value.defaultDuration)
          put("value", value)
          put("done", value >= habit.value.defaultDuration)
          put("streak", streak(habit.id, habit.value, date))
        }
      }))
    }
  }

  private fun mark(args: JsonObject): JsonElement {
    val reference = args.mcpString("habit").trim()
    val habit = data.defs.all().firstOrNull { it.id == reference }
      ?: data.defs.all().firstOrNull { it.value.label.trim().equals(reference, ignoreCase = true) }
      ?: throw McpException("Habit not found")
    val date = date(args)
    val current = data.days.get(date.toString()).valueOf(habit.id)
    val amount = args.mcpOptionalInt("value", 0..100000)
    val next = when {
      amount == null -> habit.value.defaultDuration
      args.mcpOptionalBoolean("add") == true -> current + amount
      else -> amount
    }
    data.setValue(habit.id, date, next)
    return buildJsonObject {
      put("habit", habit.id)
      put("label", habit.value.label)
      put("date", date.toString())
      put("value", next)
      put("target", habit.value.defaultDuration)
      put("done", next >= habit.value.defaultDuration)
      put("streak", streak(habit.id, habit.value, date))
    }
  }
}
