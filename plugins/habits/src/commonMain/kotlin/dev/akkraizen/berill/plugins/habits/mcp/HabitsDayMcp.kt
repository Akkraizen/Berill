package dev.akkraizen.berill.plugins.habits.mcp

import dev.akkraizen.berill.api.PluginContext
import dev.akkraizen.berill.api.mcp.*
import dev.akkraizen.berill.api.ui.toDateOrNull
import dev.akkraizen.berill.plugins.habits.HabitsPlugin
import kotlinx.serialization.json.*

internal class HabitsDayMcp(private val ctx: PluginContext) : McpCollection {
  override val name = HabitsPlugin.HABIT_DAYS
  override val writable = true
  private val raw get() = ctx.data.raw(name)
  override val schema = buildJsonObject {
    put("type", "object"); put("properties", buildJsonObject {
      put("date", buildJsonObject { put("type", "string"); put("description", "YYYY-MM-DD; immutable after creation") })
      put("values", buildJsonObject { put("type", "object"); put("description", "Habit id to nonnegative integer progress; updates patch only supplied habits") })
    }); put("required", JsonArray(listOf(JsonPrimitive("date"), JsonPrimitive("values"))))
  }
  private fun record(id: String, values: JsonObject) = buildJsonObject { put("id", id); put("fields", buildJsonObject { put("date", id); put("values", values) }); put("expected", values) }
  override fun list(limit: Int, offset: Int): JsonObject {
    require(limit in 1..200 && offset >= 0)
    val rows = raw.all().sortedByDescending { it.id }
    return buildJsonObject { put("total", rows.size); put("records", JsonArray(rows.drop(offset).take(limit).map { record(it.id, it.fields) })) }
  }
  override fun get(id: String) = raw.get(id)?.let { record(id, it.fields) } ?: throw McpException("Habit day not found")
  private fun values(fields: JsonObject): JsonObject {
    require(fields.keys.all { it == "date" || it == "values" }) { "Only date and values are accepted" }
    val values = fields["values"] as? JsonObject ?: throw McpException("Habit values object is required")
    require(values.isNotEmpty()) { "Pass at least one habit" }
    values.forEach { (id, value) ->
      require(ctx.data.raw(HabitsPlugin.HABITS).get(id) != null) { "Unknown habit: $id" }
      require((value as? JsonPrimitive)?.intOrNull?.let { it >= 0 } == true) { "Progress must be a nonnegative integer" }
    }
    return values
  }
  override fun create(fields: JsonObject): JsonObject {
    val date = (fields["date"] as? JsonPrimitive)?.content?.toDateOrNull()?.toString() ?: throw McpException("Valid date required")
    if (!raw.createIfAbsent(date, values(fields))) throw McpException("Day already exists; read before editing", McpErrorCode.CONFLICT)
    return get(date)
  }
  override fun update(id: String, fields: JsonObject, expected: JsonObject): JsonObject {
    require(id.toDateOrNull() != null && (fields["date"] == null || (fields["date"] as? JsonPrimitive)?.content == id)) { "Day date cannot change" }
    if (!raw.mutateIfCurrent(id, expected, values(fields))) throw McpException("Day changed; read it again", McpErrorCode.CONFLICT)
    return get(id)
  }
  override fun delete(id: String, expected: JsonObject, confirm: Boolean): JsonObject {
    require(confirm)
    if (!raw.mutateIfCurrent(id, expected, null, true)) throw McpException("Day changed; read it again", McpErrorCode.CONFLICT)
    return buildJsonObject { put("deleted", id) }
  }
}
