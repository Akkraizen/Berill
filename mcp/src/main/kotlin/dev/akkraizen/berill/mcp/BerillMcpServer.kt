package dev.akkraizen.berill.mcp

import dev.akkraizen.berill.core.BuildInfo
import dev.akkraizen.berill.api.mcp.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import dev.akkraizen.berill.core.log.AppLog
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequest
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolAnnotations
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put

private const val INSTRUCTIONS =
  "Berill is the user's personal workspace: notes, planner, habits, workouts, nutrition, finance, books, " +
    "watching, study, projects, languages and recipes. Data lives in collections of records; notes are in `notes`. " +
    "Start with search or list_collections, then read_records for a section. Changes made here sync to the " +
    "user's other devices. Never delete anything without asking the user first."

fun createBerillServer(tools: BerillTools, readOnly: Boolean): Server {
  val server = Server(
    Implementation(name = "berill", version = BuildInfo.VERSION_NAME, title = "Berill"),
    ServerOptions(capabilities = ServerCapabilities(tools = ServerCapabilities.Tools(listChanged = true))),
    INSTRUCTIONS
  )
  val read = ToolAnnotations(readOnlyHint = true, openWorldHint = false)
  val write = ToolAnnotations(readOnlyHint = false, destructiveHint = false, openWorldHint = false)

  fun register(name: String, description: String, schema: ToolSchema, annotations: ToolAnnotations, handler: suspend (JsonObject) -> JsonElement) {
    server.tool(name, description, schema, annotations) { args ->
      try { if (annotations.readOnlyHint != true) tools.prepareMutation(); handler(args) }
      catch (e: CancellationException) { throw e }
      catch (e: Exception) { if (e !is IllegalArgumentException && e !is ToolException) tools.reportFailure(e); throw e }
    }
  }

  register("search", "Full-text search across notes and every section. Returns matching records with snippets.",
    schema(required = listOf("query")) {
      string("query", "Text to look for, case-insensitive")
      integer("limit", "Maximum results, 1–50, default 20")
    }, read) { args -> tools.search(args.string("query"), args.int("limit", 20, 1..50)) }

  register("list_collections", "Lists data collections, the section each belongs to and how many records it has.",
    schema {}, read) { tools.listCollections() }

  register("read_records", "Reads records of one collection as JSON fields. Use offset and limit to page.",
    schema(required = listOf("collection")) {
      string("collection", "Collection name from list_collections")
      integer("limit", "Records per page, 1–200, default 50")
      integer("offset", "Records to skip, default 0")
    }, read) { args ->
    tools.readRecords(args.string("collection"), args.int("limit", 50, 1..200), args.int("offset", 0, 0..Int.MAX_VALUE))
  }

  register("get_record", "Reads one record by collection and id.",
    schema(required = listOf("collection", "id")) {
      string("collection", "Collection name")
      string("id", "Record id")
    }, read) { args -> tools.getRecord(args.string("collection"), args.string("id")) }

  register("list_notes", "Lists notes, newest first, with a short preview.",
    schema { integer("limit", "Maximum notes, 1–200, default 50") }, read) { args ->
    tools.listNotes(args.int("limit", 50, 1..200))
  }

  register("read_note", "Reads the full Markdown text of a note.",
    schema(required = listOf("id")) { string("id", "Note id") }, read) { args -> tools.readNote(args.string("id")) }

  register("sync_status", "Shows whether sync is configured, when it last ran and how many changes are pending.",
    schema {}, read) { tools.syncStatus() }

  register("sync_now", "Synchronizes with the user's Berill server right away and returns the new status.",
    schema {}, ToolAnnotations(readOnlyHint = false, idempotentHint = true, openWorldHint = true)) { tools.syncNow() }

  if (!readOnly) {
    register("create_note", "Creates a Markdown note.",
      schema(required = listOf("title", "body")) {
        string("title", "Note title")
        string("body", "Note text in Markdown")
      }, write) { args -> tools.createNote(args.string("title"), args.string("body")) }

    register("append_to_note", "Adds Markdown text to the end of an existing note.",
      schema(required = listOf("id", "text")) {
        string("id", "Note id")
        string("text", "Markdown text to add")
      }, write) { args -> tools.appendToNote(args.string("id"), args.string("text")) }

    register("update_note", "Replaces the title and/or body. Pass the etag from read_note or get_record; re-read on conflict.",
      schema(required = listOf("id")) {
        string("id", "Note id")
        string("title", "New title")
        string("body", "New Markdown body")
        string("etag", "etag from read_note or get_record; prevents overwriting newer edits")
        obj("expected", "Older alternative to etag: the expected object from get_record")
      }, write) { args -> tools.updateNote(args.string("id"), args.optionalString("title"), args.optionalString("body"), args.optionalString("etag"), args.optionalObj("expected")) }

    register("delete_note", "Moves a note to trash on synced devices. Requires the etag from the last read and confirm=true.",
      schema(required = listOf("id", "confirm")) {
        string("id", "Note id")
        boolean("confirm", "Must be true; set it only after the user agreed")
        string("etag", "etag from read_note or get_record")
        obj("expected", "Older alternative to etag")
      }, ToolAnnotations(readOnlyHint = false, destructiveHint = true, openWorldHint = false)) { args ->
      tools.deleteNote(args.string("id"), args.boolean("confirm"), args.optionalString("etag"), args.optionalObj("expected"))
    }
  }
  if (!readOnly) {
    register("create_record", "Creates a record using the owning plugin's model and validation. Discover fields via list_collections.", schema(listOf("collection", "fields")) { string("collection", "Enabled collection"); obj("fields", "New fields") }, write) { args -> tools.createRecord(args.string("collection"), args.obj("fields")) }
    register("update_record", "Updates fields atomically. Pass the etag returned by read_records or get_record. Re-read on conflict.",
      schema(listOf("collection", "id", "fields")) {
        string("collection", "Collection")
        string("id", "Record id")
        obj("fields", "Field patch")
        string("etag", "etag from the last read of this record")
        obj("expected", "Older alternative to etag: the expected object from get_record")
      }, write) { args ->
      tools.updateRecord(args.string("collection"), args.string("id"), args.obj("fields"), args.optionalString("etag"), args.optionalObj("expected"))
    }
    register("delete_record", "Moves a record to trash. Requires the etag from the last read and explicit confirm=true.",
      schema(listOf("collection", "id", "confirm")) {
        string("collection", "Collection")
        string("id", "Record id")
        string("etag", "etag from the last read of this record")
        obj("expected", "Older alternative to etag")
        boolean("confirm", "User approved deletion")
      }, ToolAnnotations(readOnlyHint = false, destructiveHint = true, openWorldHint = false)) { args ->
      tools.deleteRecord(args.string("collection"), args.string("id"), args.optionalString("etag"), args.optionalObj("expected"), args.boolean("confirm"))
    }
  }
  var registered = emptySet<String>()
  fun refreshPluginTools() {
    val active = tools.sources()
    val desired = active.flatMap { listOf("berill_${it.pluginId}_read") + if (readOnly || it.collections.none { c -> c.writable }) emptyList() else listOf("berill_${it.pluginId}_edit") }.toSet()
    (registered - desired).forEach(server::removeTool)
    active.forEach { source ->
      val readName = "berill_${source.pluginId}_read"
      if (readName !in registered) register(readName, "Read ${source.pluginId} records. Model and behavior supplied by its plugin.", schema(listOf("action", "collection")) { choice("action", "Read operation", listOf(McpOperation.LIST, McpOperation.GET).map { it.wire }); choice("collection", "Plugin collection", source.collections.map { it.name }); string("id", "Record id for get"); integer("limit", "1..200, default 50"); integer("offset", "Default 0") }, read) { args ->
        val current = tools.sources().firstOrNull { it.pluginId == source.pluginId } ?: throw ToolException("Plugin disabled", McpErrorCode.UNAVAILABLE)
        val collection = current.collections.firstOrNull { it.name == args.string("collection") } ?: throw ToolException("Unknown plugin collection")
        when (args.operation()) { McpOperation.LIST -> collection.list(args.int("limit", 50, 1..200), args.int("offset", 0, 0..Int.MAX_VALUE)); McpOperation.GET -> collection.get(args.string("id")); else -> throw ToolException("Use list or get") }
      }
      val editName = "berill_${source.pluginId}_edit"
      if (editName in desired && editName !in registered) register(editName, "Create, update or delete ${source.pluginId} records with plugin validation. Updates/deletes require the etag from the last read. Deletion requires confirm=true.", schema(listOf("action", "collection")) { choice("action", "Edit operation", listOf(McpOperation.CREATE, McpOperation.UPDATE, McpOperation.DELETE).map { it.wire }); choice("collection", "Editable plugin collection", source.collections.filter { it.writable }.map { it.name }); string("id", "Record id"); obj("fields", "Field patch"); string("etag", "etag from the last read"); obj("expected", "Older alternative to etag"); boolean("confirm", "Required for deletion") }, ToolAnnotations(readOnlyHint = false, destructiveHint = true, openWorldHint = false)) { args ->
        val current = tools.sources().firstOrNull { it.pluginId == source.pluginId } ?: throw ToolException("Plugin disabled", McpErrorCode.UNAVAILABLE)
        val collection = current.collections.firstOrNull { it.name == args.string("collection") && it.writable } ?: throw ToolException("Collection is not editable")
        when (args.operation()) { McpOperation.CREATE -> collection.create(args.obj("fields")); McpOperation.UPDATE -> args.string("id").let { id -> collection.update(id, args.obj("fields"), collection.expectedFor(id, args.optionalString("etag"), args.optionalObj("expected"))) }
          McpOperation.DELETE -> args.string("id").let { id -> collection.delete(id, collection.expectedFor(id, args.optionalString("etag"), args.optionalObj("expected")), args.boolean("confirm")) }; else -> throw ToolException("Unknown edit action") }
      }
    }
    registered = desired
  }
  refreshPluginTools()
  var watcher: kotlinx.coroutines.Job? = null
  server.onConnect { synchronized(server) { if (watcher == null) watcher = tools.scope.launch { tools.enabled.collect { refreshPluginTools() } } } }
  server.onClose { watcher?.cancel() }
  return server
}

private val output = Json { encodeDefaults = false }

private fun Server.tool(
  name: String,
  description: String,
  schema: ToolSchema,
  annotations: ToolAnnotations,
  handler: suspend (JsonObject) -> JsonElement
) {
  addTool(name = name, description = description, inputSchema = schema, toolAnnotations = annotations) { request: CallToolRequest ->
    try {
      val result = handler(request.arguments ?: JsonObject(emptyMap()))
      CallToolResult(content = listOf(TextContent(output.encodeToString(JsonElement.serializer(), result))), structuredContent = result as? JsonObject ?: buildJsonObject { put("items", result) })
    } catch (e: CancellationException) { throw e
    } catch (e: McpException) {
      toolError(e.code, e.message ?: "Invalid tool arguments")
    } catch (e: IllegalArgumentException) {
      toolError(McpErrorCode.INVALID_ARGUMENT, e.message ?: "Invalid tool arguments")
    } catch (e: ToolException) {
      toolError(e.code, e.message.orEmpty())
    } catch (e: Exception) {
      AppLog.error("mcp", "tool $name failed", e)
      toolError(McpErrorCode.INTERNAL, "$name failed; check the Berill notification center")
    }
  }
}

private class SchemaBuilder {
  val properties = mutableMapOf<String, JsonObject>()

  fun string(name: String, description: String) = property(name, "string", description)

  fun integer(name: String, description: String) = property(name, "integer", description)

  fun boolean(name: String, description: String) = property(name, "boolean", description)

  fun obj(name: String, description: String) = property(name, "object", description)

  fun choice(name: String, description: String, values: List<String>) {
    properties[name] = buildJsonObject { put("type", "string"); put("description", description); put("enum", JsonArray(values.map(::JsonPrimitive))) }
  }

  private fun property(name: String, type: String, description: String) {
    properties[name] = buildJsonObject {
      put("type", type)
      put("description", description)
    }
  }
}

private fun schema(required: List<String> = emptyList(), block: SchemaBuilder.() -> Unit): ToolSchema {
  val builder = SchemaBuilder().apply(block)
  return ToolSchema(properties = JsonObject(builder.properties), required = required.takeIf { it.isNotEmpty() })
}

private fun JsonObject.optionalString(name: String): String? = (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content

private fun JsonObject.string(name: String): String =
  optionalString(name) ?: throw ToolException("Missing text argument `$name`.")

private fun JsonObject.int(name: String, default: Int, range: IntRange): Int {
  val value = this[name] ?: return default
  val number = (value as? JsonPrimitive)?.intOrNull ?: throw ToolException("`$name` must be an integer.")
  if (number !in range) throw ToolException("`$name` must be between ${range.first} and ${range.last}.")
  return number
}

private fun JsonObject.boolean(name: String): Boolean =
  (this[name] as? JsonPrimitive)?.booleanOrNull ?: throw ToolException("Missing boolean argument `$name`.")

private fun JsonObject.obj(name: String): JsonObject = this[name] as? JsonObject ?: throw ToolException("Missing object argument: $name")

private fun JsonObject.optionalObj(name: String): JsonObject? = this[name] as? JsonObject

private fun JsonObject.operation(): McpOperation = McpOperation.entries.firstOrNull { it.wire == string("action") } ?: throw ToolException("Unknown operation")

private fun toolError(code: McpErrorCode, message: String) = CallToolResult(content = listOf(TextContent(message)), isError = true,
  structuredContent = buildJsonObject { put("error", buildJsonObject { put("code", code.name); put("message", message) }) })
