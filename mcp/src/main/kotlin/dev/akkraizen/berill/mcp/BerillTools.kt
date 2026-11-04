package dev.akkraizen.berill.mcp

import dev.akkraizen.berill.core.BerillCore
import dev.akkraizen.berill.api.all
import dev.akkraizen.berill.api.mcp.*
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.contentOrNull
import dev.akkraizen.berill.core.notes.displayTitle
import dev.akkraizen.berill.core.notes.preview
import dev.akkraizen.berill.core.sync.SyncState
import dev.akkraizen.berill.protocol.Collections
import kotlin.time.Instant
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class ToolException(message: String, val code: McpErrorCode = McpErrorCode.INVALID_ARGUMENT) : Exception(message)

class BerillTools(private val core: BerillCore) {
  val scope get() = core.scope
  val enabled get() = core.plugins.enabled
  private val notes = dev.akkraizen.berill.core.notes.NoteMcp(core.data)
  fun sources(): List<McpSource> = core.plugins.capabilities.all<McpSource>()
  private fun collections(): List<McpCollection> = listOf(notes) + sources().flatMap { it.collections }.filter { it.name != Collections.SETTINGS }.distinctBy { it.name }
  private fun collection(name: String): McpCollection = collections().firstOrNull { it.name == name }
    ?: throw ToolException("Collection is unavailable or its plugin is disabled: $name", McpErrorCode.UNAVAILABLE)
  private fun editable(name: String): McpCollection = collection(name).also { require(it.writable) { "Collection is read only" } }
  private fun section(name: String): String = if (name == Collections.NOTES) "Notes" else core.collections.owner(name)?.title ?: name
  suspend fun prepareMutation() { core.editorSessions.prepareMutation() }
  fun reportFailure(error: Exception) { core.connections.errors.report(dev.akkraizen.berill.core.connections.FailureOperation.COMMAND, error) }

  fun runAction(pluginId: String, name: String, args: JsonObject): JsonElement {
    val source = sources().firstOrNull { it.pluginId == pluginId } ?: throw ToolException("Plugin disabled", McpErrorCode.UNAVAILABLE)
    val action = source.actions.firstOrNull { it.name == name } ?: throw ToolException("Unknown action", McpErrorCode.UNAVAILABLE)
    return action.run(args)
  }

  private fun dateArgument(value: String?): kotlinx.datetime.LocalDate? =
    value?.let { runCatching { kotlinx.datetime.LocalDate.parse(it.trim()) }.getOrNull() ?: throw ToolException("Dates must be YYYY-MM-DD") }

  private fun pluginTitle(id: String): String = core.plugins.enabled.value.firstOrNull { it.id == id }?.title ?: id

  private fun todayJson(item: dev.akkraizen.berill.api.TodayItem): JsonObject = buildJsonObject {
    put("title", item.title)
    put("kind", item.kind.name)
    put("section", pluginTitle(item.pluginId))
    item.detail?.let { put("detail", it) }
    item.time?.let { put("time", it) }
    item.due?.let { put("due", it.toString()) }
    item.done?.let { put("done", it) }
    if (item.overdue) put("overdue", true)
  }

  suspend fun today(date: String?): JsonElement {
    val day = dateArgument(date) ?: dev.akkraizen.berill.api.ui.today()
    val items = core.todayFeed.observe(day).first()
    return buildJsonObject {
      put("date", day.toString())
      put("done", items.count { it.done == true })
      put("checkable", items.count { it.done != null })
      put("overdue", items.count { it.overdue })
      put("items", JsonArray(items.map(::todayJson)))
    }
  }

  suspend fun weeklyReview(weekStart: String?): JsonElement {
    val today = dev.akkraizen.berill.api.ui.today()
    val start = dateArgument(weekStart) ?: today.minus(today.dayOfWeek.ordinal, kotlinx.datetime.DateTimeUnit.DAY)
    val days = (0 until 7).map { start.plus(it, kotlinx.datetime.DateTimeUnit.DAY) }
    val zone = kotlinx.datetime.TimeZone.currentSystemDefault()
    val edited = core.notes.all().filter { note ->
      note.value.updatedAt > 0 && Instant.fromEpochMilliseconds(note.value.updatedAt).toLocalDateTime(zone).date in days.first()..days.last()
    }
    val deadlines = core.plugins.capabilities.all<dev.akkraizen.berill.api.DeadlineSource>().flatMap { source ->
      runCatching { source.deadlines().first() }.getOrDefault(emptyList())
    }.filter { it.date > days.last() && it.date <= days.last().plus(7, kotlinx.datetime.DateTimeUnit.DAY) }.sortedBy { it.date }
    val perDay = days.map { day -> day to core.connections.day(day) }
    return buildJsonObject {
      put("weekStart", start.toString())
      put("weekEnd", days.last().toString())
      put("sections", buildJsonObject {
        perDay.flatMap { it.second }.groupBy { it.title }.forEach { (section, entries) -> put(section, entries.size) }
      })
      put("days", JsonArray(perDay.map { (day, entries) ->
        buildJsonObject {
          put("date", day.toString())
          put("weekday", day.dayOfWeek.name)
          put("entries", JsonArray(entries.map { entry -> buildJsonObject { put("section", entry.title); put("detail", entry.detail) } }))
        }
      }))
      put("notesEdited", JsonArray(edited.sortedByDescending { it.value.updatedAt }.take(30).map { note ->
        buildJsonObject { put("id", note.id); put("title", note.value.displayTitle()) }
      }))
      put("nextWeekDeadlines", JsonArray(deadlines.map { deadline ->
        buildJsonObject { put("title", deadline.title); put("date", deadline.date.toString()); put("section", pluginTitle(deadline.pluginId)) }
      }))
      if (today in days) put("today", today(null))
    }
  }

  suspend fun listCollections(): JsonElement = JsonArray(collections().map { c -> buildJsonObject {
    put("collection", c.name); put("section", section(c.name)); put("records", c.list(1, 0)["total"] ?: JsonPrimitive(0)); put("writable", c.writable); put("schema", c.schema)
  } })
  suspend fun readRecords(collection: String, limit: Int, offset: Int): JsonElement = collection(collection).list(limit, offset)
  fun getRecord(collection: String, id: String): JsonElement = collection(collection).get(id)
  fun createRecord(collection: String, fields: JsonObject): JsonElement = editable(collection).create(fields)
  fun updateRecord(collection: String, id: String, fields: JsonObject, etag: String?, expected: JsonObject?): JsonElement =
    editable(collection).let { it.update(id, fields, it.expectedFor(id, etag, expected)) }
  fun deleteRecord(collection: String, id: String, etag: String?, expected: JsonObject?, confirm: Boolean): JsonElement =
    editable(collection).let { it.delete(id, it.expectedFor(id, etag, expected), confirm) }

  suspend fun search(query: String, limit: Int): JsonElement {
    require(query.isNotBlank()) { "Query must not be empty" }
    val results = mutableListOf<JsonObject>()
    for (c in collections()) {
      var offset = 0
      do {
        val page = c.list(200, offset)
        val records = page["records"] as? JsonArray ?: break
        for (record in records) {
          val fields = record.jsonObject["fields"]?.jsonObject ?: continue
          val text = flatten(fields)
          val at = text.indexOf(query.trim(), ignoreCase = true)
          if (at < 0) continue
          results += buildJsonObject { put("collection", c.name); put("section", section(c.name)); put("id", record.jsonObject.getValue("id")); titleOf(fields)?.let { put("title", it) }; put("snippet", snippet(text, at, query.trim().length)) }
          if (results.size >= limit) return JsonArray(results)
        }
        offset += records.size
      } while (offset < ((page["total"] as? JsonPrimitive)?.intOrNull ?: 0) && records.isNotEmpty())
    }
    return JsonArray(results)
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
      val record = notes.get(id)
      put("etag", record.getValue("etag"))
      put("expected", record.getValue("expected"))
      put("updated", time(note.updatedAt))
    }
  }

  fun createNote(title: String, body: String): JsonElement = notes.create(buildJsonObject { put("title", title.trim()); put("body", body) })
  fun appendToNote(id: String, text: String): JsonElement {
    val current = notes.get(id).getValue("fields").jsonObject
    val body = (current["body"] as? JsonPrimitive)?.content.orEmpty()
    return notes.update(id, buildJsonObject { put("body", if (body.isBlank()) text else body.trimEnd('\n') + "\n\n" + text) }, current)
  }
  fun updateNote(id: String, title: String?, body: String?, etag: String? = null, expected: JsonObject? = null): JsonElement {
    require(title != null || body != null) { "Pass title or body" }
    return notes.update(id, buildJsonObject { title?.let { put("title", it) }; body?.let { put("body", it) } }, notes.expectedFor(id, etag, expected))
  }
  fun deleteNote(id: String, confirm: Boolean, etag: String? = null, expected: JsonObject? = null): JsonElement {
    if (!confirm) throw ToolException("Deletion requires confirm=true; ask the user first")
    return notes.delete(id, notes.expectedFor(id, etag, expected), confirm)
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
      throw ToolException("Sync is not configured. Set it up in Berill settings, or run `berill-mcp login` for the standalone server.")
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
