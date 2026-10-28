package dev.akkraizen.berill.mcp

import dev.akkraizen.berill.core.BuildInfo
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
    ServerOptions(capabilities = ServerCapabilities(tools = ServerCapabilities.Tools(listChanged = false))),
    INSTRUCTIONS
  )
  val read = ToolAnnotations(readOnlyHint = true, openWorldHint = false)
  val write = ToolAnnotations(readOnlyHint = false, destructiveHint = false, openWorldHint = false)

  server.tool("search", "Full-text search across notes and every section. Returns matching records with snippets.",
    schema(required = listOf("query")) {
      string("query", "Text to look for, case-insensitive")
      integer("limit", "Maximum results, 1–50, default 20")
    }, read) { args -> tools.search(args.string("query"), args.int("limit", 20, 1..50)) }

  server.tool("list_collections", "Lists data collections, the section each belongs to and how many records it has.",
    schema {}, read) { tools.listCollections() }

  server.tool("read_records", "Reads records of one collection as JSON fields. Use offset and limit to page.",
    schema(required = listOf("collection")) {
      string("collection", "Collection name from list_collections")
      integer("limit", "Records per page, 1–200, default 50")
      integer("offset", "Records to skip, default 0")
    }, read) { args ->
    tools.readRecords(args.string("collection"), args.int("limit", 50, 1..200), args.int("offset", 0, 0..Int.MAX_VALUE))
  }

  server.tool("get_record", "Reads one record by collection and id.",
    schema(required = listOf("collection", "id")) {
      string("collection", "Collection name")
      string("id", "Record id")
    }, read) { args -> tools.getRecord(args.string("collection"), args.string("id")) }

  server.tool("list_notes", "Lists notes, newest first, with a short preview.",
    schema { integer("limit", "Maximum notes, 1–200, default 50") }, read) { args ->
    tools.listNotes(args.int("limit", 50, 1..200))
  }

  server.tool("read_note", "Reads the full Markdown text of a note.",
    schema(required = listOf("id")) { string("id", "Note id") }, read) { args -> tools.readNote(args.string("id")) }

  server.tool("sync_status", "Shows whether sync is configured, when it last ran and how many changes are pending.",
    schema {}, read) { tools.syncStatus() }

  server.tool("sync_now", "Synchronizes with the user's Berill server right away and returns the new status.",
    schema {}, ToolAnnotations(readOnlyHint = false, idempotentHint = true, openWorldHint = true)) { tools.syncNow() }

  if (!readOnly) {
    server.tool("create_note", "Creates a Markdown note.",
      schema(required = listOf("title", "body")) {
        string("title", "Note title")
        string("body", "Note text in Markdown")
      }, write) { args -> tools.createNote(args.string("title"), args.string("body")) }

    server.tool("append_to_note", "Adds Markdown text to the end of an existing note.",
      schema(required = listOf("id", "text")) {
        string("id", "Note id")
        string("text", "Markdown text to add")
      }, write) { args -> tools.appendToNote(args.string("id"), args.string("text")) }

    server.tool("update_note", "Replaces the title and/or the body of a note.",
      schema(required = listOf("id")) {
        string("id", "Note id")
        string("title", "New title")
        string("body", "New Markdown body")
      }, write) { args -> tools.updateNote(args.string("id"), args.optionalString("title"), args.optionalString("body")) }

    server.tool("delete_note", "Deletes a note on every device. Ask the user first and pass confirm=true.",
      schema(required = listOf("id", "confirm")) {
        string("id", "Note id")
        boolean("confirm", "Must be true; set it only after the user agreed")
      }, ToolAnnotations(readOnlyHint = false, destructiveHint = true, openWorldHint = false)) { args ->
      tools.deleteNote(args.string("id"), args.boolean("confirm"))
    }
  }
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
      CallToolResult(content = listOf(TextContent(output.encodeToString(JsonElement.serializer(), result))))
    } catch (e: ToolException) {
      CallToolResult(content = listOf(TextContent(e.message.orEmpty())), isError = true)
    } catch (e: Exception) {
      AppLog.error("mcp", "tool $name failed", e)
      CallToolResult(content = listOf(TextContent("$name failed: ${e.message ?: e::class.simpleName}")), isError = true)
    }
  }
}

private class SchemaBuilder {
  val properties = mutableMapOf<String, JsonObject>()

  fun string(name: String, description: String) = property(name, "string", description)

  fun integer(name: String, description: String) = property(name, "integer", description)

  fun boolean(name: String, description: String) = property(name, "boolean", description)

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
