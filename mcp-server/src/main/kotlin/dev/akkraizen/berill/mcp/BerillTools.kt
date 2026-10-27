package dev.akkraizen.berill.mcp

import dev.akkraizen.berill.core.BerillCore
import dev.akkraizen.berill.core.notes.displayTitle
import dev.akkraizen.berill.core.notes.preview
import dev.akkraizen.berill.core.sync.SyncState
import dev.akkraizen.berill.protocol.Collections
import kotlin.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class ToolException(message: String) : Exception(message)

class BerillTools(private val core: BerillCore) {
  private val searchable: List<String>
    get() = core.collections.all.filter { it != Collections.SETTINGS }

  private fun section(collection: String): String =
    if (collection == Collections.NOTES) "Notes" else core.collections.owner(collection)?.let { "${it.title} (${it.id})" } ?: collection

  private fun requireCollection(collection: String) {
    if (collection == Collections.SETTINGS || !core.collections.isKnown(collection))
      throw ToolException("Unknown collection `$collection`. Call list_collections to see the available ones.")
  }

  suspend fun listCollections(): JsonElement = buildJsonArray {
    for (name in searchable) {
      add(buildJsonObject {
        put("collection", name)
        put("section", section(name))
        put("records", core.data.raw(name).observe().first().size)
      })
    }
  }

  suspend fun search(query: String, limit: Int): JsonElement {
    val needle = query.trim()
    if (needle.isEmpty()) throw ToolException("Query must not be empty.")
    val results = mutableListOf<JsonObject>()
    for (name in searchable) {
      for (record in core.data.raw(name).observe().first()) {
        val text = flatten(record.fields)
        val at = text.indexOf(needle, ignoreCase = true)
        if (at < 0) continue
        results += buildJsonObject {
          put("collection", name)
          put("section", section(name))
          put("id", record.id)
          titleOf(record.fields)?.let { put("title", it) }
          put("snippet", snippet(text, at, needle.length))
        }
        if (results.size >= limit) return JsonArray(results)
      }
    }
    return JsonArray(results)
  }

  suspend fun readRecords(collection: String, limit: Int, offset: Int): JsonElement {
    requireCollection(collection)
    val records = core.data.raw(collection).observe().first()
    return buildJsonObject {
      put("collection", collection)
      put("section", section(collection))
      put("total", records.size)
      put("records", buildJsonArray {
        records.drop(offset).take(limit).forEach { record ->
          add(buildJsonObject {
            put("id", record.id)
            put("fields", record.fields)
          })
        }
      })
    }
  }

  fun getRecord(collection: String, id: String): JsonElement {
    requireCollection(collection)
    val record = core.data.raw(collection).get(id) ?: throw ToolException("No record `$id` in `$collection`.")
    return buildJsonObject {
      put("collection", collection)
      put("id", record.id)
      put("fields", record.fields)
    }
  }

  suspend fun listNotes(limit: Int): JsonElement = buildJsonArray {
    core.notes.observe().first().take(limit).forEach { item ->
      add(buildJsonObject {
        put("id", item.id)
        put("title", item.value.displayTitle())
        put("updated", time(item.value.updatedAt))
        put("preview", item.value.preview())
      })
    }
  }

  fun readNote(id: String): JsonElement {
    val note = core.notes.get(id) ?: throw ToolException("No note `$id`.")
    return buildJsonObject {
      put("id", id)
      put("title", note.title)
      put("body", note.body)
      put("updated", time(note.updatedAt))
    }
  }

  fun createNote(title: String, body: String): JsonElement {
    if (title.isBlank() && body.isBlank()) throw ToolException("A note needs a title or a body.")
    val id = core.data.newId()
    core.notes.save(id, title.trim(), body)
    return buildJsonObject { put("id", id) }
  }

  fun appendToNote(id: String, text: String): JsonElement {
    val note = core.notes.get(id) ?: throw ToolException("No note `$id`.")
    val body = if (note.body.isBlank()) text else note.body.trimEnd('\n') + "\n\n" + text
    core.notes.save(id, note.title, body)
    return buildJsonObject { put("id", id) }
  }

  fun updateNote(id: String, title: String?, body: String?): JsonElement {
    val note = core.notes.get(id) ?: throw ToolException("No note `$id`.")
    if (title == null && body == null) throw ToolException("Pass a new title, a new body, or both.")
    core.notes.save(id, title ?: note.title, body ?: note.body)
    return buildJsonObject { put("id", id) }
  }

  fun deleteNote(id: String, confirm: Boolean): JsonElement {
    if (!confirm) throw ToolException("Deleting is permanent on every device. Ask the user, then call again with confirm=true.")
    core.notes.get(id) ?: throw ToolException("No note `$id`.")
    core.notes.delete(id)
    return buildJsonObject { put("deleted", id) }
  }

  fun syncStatus(): JsonElement {
    val d = core.sync.diagnostics()
    return buildJsonObject {
      put("configured", d.serverUrl != null && d.hasToken)
      d.serverUrl?.let { put("server", it) }
      d.user?.let { put("user", it) }
      put("state", when (val state = core.sync.state.value) {
        SyncState.NotConfigured -> "not configured"
        SyncState.Idle -> "idle"
        SyncState.Syncing -> "syncing"
        is SyncState.Done -> "done"
        is SyncState.Failed -> "failed: ${state.message}"
      })
      d.lastOkMillis?.let { put("lastSync", time(it)) }
      d.lastError?.let { put("lastError", it) }
      put("pendingChanges", d.pending)
      put("records", d.totalRecords)
      put("encryption", d.encryption.name.lowercase())
      put("deviceId", d.deviceId)
    }
  }

  suspend fun syncNow(): JsonElement {
    if (core.sync.serverUrl() == null || core.sync.token() == null)
      throw ToolException("Sync is not configured. Run `berill-mcp login` first.")
    core.sync.syncNow()
    return syncStatus()
  }

  private fun time(millis: Long): String = Instant.fromEpochMilliseconds(millis).toString()

  private fun titleOf(fields: JsonObject): String? =
    listOf("title", "name", "word", "text")
      .firstNotNullOfOrNull { (fields[it] as? JsonPrimitive)?.takeIf { p -> p.isString }?.content?.takeIf(String::isNotBlank) }
      ?.take(120)

  private fun flatten(element: JsonElement): String {
    val texts = mutableListOf<String>()
    val values = mutableListOf<String>()
    fun walk(e: JsonElement) {
      when (e) {
        is JsonPrimitive -> if (e.isString) texts += e.content else values += e.toString()
        is JsonArray -> e.forEach(::walk)
        is JsonObject -> e.values.forEach(::walk)
      }
    }
    walk(element)
    return (texts + values).joinToString(" ")
  }

  private fun snippet(text: String, at: Int, length: Int): String {
    val start = (at - 60).coerceAtLeast(0)
    val end = (at + length + 100).coerceAtMost(text.length)
    return (if (start > 0) "…" else "") + text.substring(start, end).replace(Regex("\\s+"), " ") + (if (end < text.length) "…" else "")
  }
}
