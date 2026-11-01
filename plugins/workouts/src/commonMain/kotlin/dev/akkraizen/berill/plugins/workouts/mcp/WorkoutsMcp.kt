package dev.akkraizen.berill.plugins.workouts.mcp

import dev.akkraizen.berill.api.PluginContext
import dev.akkraizen.berill.api.mcp.*
import dev.akkraizen.berill.api.ui.toDateOrNull
import dev.akkraizen.berill.api.ui.nowMillis
import dev.akkraizen.berill.plugins.workouts.WorkoutsPlugin
import dev.akkraizen.berill.plugins.workouts.data.*

internal class WorkoutsMcp(ctx: PluginContext) : McpSource {
  override val pluginId = ctx.pluginId
  private val editable: List<McpCollection> = listOf(
    TypedMcpCollection(ctx, WorkoutsPlugin.CYCLES, Cycle.serializer(), validate = { require(it.title.isNotBlank() && it.weeks in 1..104) { "Cycle title and valid number of weeks required" } }),
    TypedMcpCollection(ctx, WorkoutsPlugin.PROGRAMS, Program.serializer(), validate = { require(it.title.isNotBlank()) { "Program title required" } }),
    TypedMcpCollection(ctx, WorkoutsPlugin.LOGS, WorkoutLog.serializer(), validate = { require(it.title.isNotBlank() && it.date.toDateOrNull() != null && it.exercises.all { e -> e.sets.all { s -> s.weight == null || (s.weight.isFinite() && s.weight >= 0) } }) { "Valid workout title, date and weights required" } })
  )
  override val collections: List<McpCollection> = editable + (WorkoutsPlugin.collections - editable.map { it.name }.toSet()).map { ReadOnlyMcpCollection(ctx, it) }
}
