package dev.akkraizen.berill.plugins.planner.mcp

import dev.akkraizen.berill.api.PluginContext
import dev.akkraizen.berill.api.mcp.*
import dev.akkraizen.berill.api.ui.toDateOrNull
import dev.akkraizen.berill.api.ui.nowMillis
import dev.akkraizen.berill.plugins.planner.PlannerPlugin
import dev.akkraizen.berill.plugins.planner.data.*

internal class PlannerMcp(ctx: PluginContext) : McpSource {
  override val pluginId = ctx.pluginId
  private val editable: List<McpCollection> = listOf(
    TypedMcpCollection(ctx, PlannerPlugin.TASKS, PlannerTask.serializer(), normalize = { old, next -> if (old?.date != null && next.date != null && old.date != next.date) next.copy(done = false, deferredDates = (old.deferredDates + listOfNotNull(old.date)).distinct()) else next }, validate = { require(it.text.isNotBlank() && (it.date != null || it.week != null) && (it.date == null || it.date.toDateOrNull() != null)) { "Task text and valid date or week required" } }),
    PlannerEventMcp(ctx)
  )
  override val collections: List<McpCollection> = editable + (PlannerPlugin.collections - editable.map { it.name }.toSet()).map { ReadOnlyMcpCollection(ctx, it) }
}


private fun eventCollection(ctx: PluginContext): McpCollection = TypedMcpCollection(ctx, PlannerPlugin.EVENTS, CalendarEvent.serializer(), normalize = { old, next -> next.copy(calendarId = old?.calendarId, googleId = old?.googleId, etag = old?.etag, dirty = true, deleted = false, updatedAt = nowMillis()) }, validate = { require(it.title.isNotBlank() && it.date.toDateOrNull() != null && (it.start == null || parseTime(it.start) != null) && (it.end == null || parseTime(it.end) != null)) { "Event title, date and times required" } })

private class PlannerEventMcp(private val ctx: PluginContext) : McpCollection by eventCollection(ctx) {
  override fun delete(id: String, expected: kotlinx.serialization.json.JsonObject, confirm: Boolean): kotlinx.serialization.json.JsonObject {
    require(confirm) { "Deletion requires confirm=true" }
    val google = expected["googleId"] as? kotlinx.serialization.json.JsonPrimitive
    val linked = google != null && google.isString && google.content.isNotBlank()
    val patch = if (linked) kotlinx.serialization.json.buildJsonObject {
      put("deleted", kotlinx.serialization.json.JsonPrimitive(true)); put("dirty", kotlinx.serialization.json.JsonPrimitive(true)); put("updatedAt", kotlinx.serialization.json.JsonPrimitive(nowMillis()))
    } else null
    if (!ctx.data.raw(PlannerPlugin.EVENTS).mutateIfCurrent(id, expected, patch, delete = !linked)) throw McpException("Event changed; read it again", McpErrorCode.CONFLICT)
    return kotlinx.serialization.json.buildJsonObject { put("deleted", kotlinx.serialization.json.JsonPrimitive(id)) }
  }
}
