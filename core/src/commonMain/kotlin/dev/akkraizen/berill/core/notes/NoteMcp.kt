package dev.akkraizen.berill.core.notes

import dev.akkraizen.berill.api.*
import dev.akkraizen.berill.api.mcp.*
import dev.akkraizen.berill.protocol.Collections
import kotlinx.serialization.json.*
import kotlin.time.Clock

class NoteMcp(private val data: DataAccess) : McpCollection {
  override val name = Collections.NOTES
  override val writable = true
  private val raw get() = data.raw(name)
  override val schema = buildJsonObject { put("type", "object"); put("properties", buildJsonObject { put("title", buildJsonObject { put("type", "string") }); put("body", buildJsonObject { put("type", "string") }) }); put("additionalProperties", false) }
  override fun list(limit: Int, offset: Int): JsonObject {
    require(limit in 1..200 && offset >= 0)
    val records = raw.all().sortedByDescending { (it.fields["updatedAt"] as? JsonPrimitive)?.longOrNull ?: 0 }
    return buildJsonObject { put("collection", name); put("total", records.size); put("records", JsonArray(records.drop(offset).take(limit).map { record(it.id, it.fields) })) }
  }
  private fun record(id: String, fields: JsonObject) = buildJsonObject { put("id", id); put("fields", fields); put("expected", fields) }
  override fun get(id: String): JsonObject = raw.get(id)?.let { record(id, it.fields) } ?: throw McpException("Note not found")
  private fun prepare(fields: JsonObject, previous: JsonObject = JsonObject(emptyMap())): JsonObject {
    require(fields.keys.all { it in setOf("title", "body") }) { "Only title and body can be edited" }
    fields.values.forEach { require(it is JsonPrimitive && it.isString) { "Note fields must be strings" } }
    val next = previous + fields
    require(listOf("title", "body").any { (next[it] as? JsonPrimitive)?.content?.isNotBlank() == true }) { "A note needs a title or body" }
    return JsonObject(fields + ("updatedAt" to JsonPrimitive(Clock.System.now().toEpochMilliseconds())))
  }
  override fun create(fields: JsonObject): JsonObject {
    val patch = prepare(fields)
    val id = data.newId()
    check(raw.createIfAbsent(id, JsonObject(mapOf("title" to JsonPrimitive(""), "body" to JsonPrimitive("")) + patch)))
    return get(id)
  }
  override fun update(id: String, fields: JsonObject, expected: JsonObject): JsonObject {
    require(fields.isNotEmpty())
    val patch = prepare(fields, expected)
    if (!raw.mutateIfCurrent(id, expected, patch)) throw McpException("Note changed; read it again before editing", McpErrorCode.CONFLICT)
    return get(id)
  }
  override fun delete(id: String, expected: JsonObject, confirm: Boolean): JsonObject {
    require(confirm) { "Deletion requires confirm=true" }
    if (!raw.mutateIfCurrent(id, expected, null, delete = true)) throw McpException("Note changed or missing; read it again", McpErrorCode.CONFLICT)
    return buildJsonObject { put("deleted", id) }
  }
}
