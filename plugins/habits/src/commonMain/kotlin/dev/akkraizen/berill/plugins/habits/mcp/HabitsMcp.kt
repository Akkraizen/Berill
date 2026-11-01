package dev.akkraizen.berill.plugins.habits.mcp

import dev.akkraizen.berill.api.PluginContext
import dev.akkraizen.berill.api.mcp.*
import dev.akkraizen.berill.api.ui.toDateOrNull
import dev.akkraizen.berill.api.ui.nowMillis
import dev.akkraizen.berill.plugins.habits.HabitsPlugin
import dev.akkraizen.berill.plugins.habits.data.*

internal class HabitsMcp(ctx: PluginContext) : McpSource {
  override val pluginId = ctx.pluginId
  private val editable: List<McpCollection> = listOf(
    HabitsDayMcp(ctx),
    TypedMcpCollection(ctx, HabitsPlugin.HABITS, HabitDef.serializer(), validate = { require(it.label.isNotBlank() && it.defaultDuration > 0) { "Habit label and positive target required" } })
  )
  override val collections: List<McpCollection> = editable + (HabitsPlugin.collections - editable.map { it.name }.toSet()).map { ReadOnlyMcpCollection(ctx, it) }
}
