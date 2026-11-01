@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
package dev.akkraizen.berill.api.mcp

import dev.akkraizen.berill.api.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.json.*

enum class McpErrorCode { INVALID_ARGUMENT, NOT_FOUND, CONFLICT, UNAVAILABLE, READ_ONLY, INTERNAL }

class McpException(message: String, val code: McpErrorCode = McpErrorCode.INVALID_ARGUMENT) : IllegalArgumentException(message)

enum class McpOperation(val wire: String) { LIST("list"), GET("get"), CREATE("create"), UPDATE("update"), DELETE("delete") }

interface McpSource : Capability {
  val pluginId: String
  val collections: List<McpCollection>
}

interface McpCollection {
  val name: String
  val writable: Boolean
  val schema: JsonObject
  fun list(limit: Int, offset: Int): JsonObject
  fun get(id: String): JsonObject
  fun create(fields: JsonObject): JsonObject
  fun update(id: String, fields: JsonObject, expected: JsonObject): JsonObject
  fun delete(id: String, expected: JsonObject, confirm: Boolean): JsonObject
}

/** Shared storage mechanics; serializers, validation and lifecycle rules are supplied by each plugin. */
class TypedMcpCollection<T>(private val ctx: PluginContext, override val name: String, private val serializer: KSerializer<T>,
  override val writable: Boolean = true, private val fieldSchemas: Map<String, JsonObject> = emptyMap(), private val createId: (T) -> String = { ctx.data.newId() },
  private val normalize: (T?, T) -> T = { _, next -> next }, private val validate: (T) -> Unit = {}) : McpCollection {
  private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
  private val raw get() = ctx.data.raw(name)
  override val schema: JsonObject get() = fieldSchemas.entries.fold(descriptorSchema(serializer.descriptor)) { schema, (key, value) -> schema.withMcpField(key, value) }
  override fun list(limit: Int, offset: Int): JsonObject {
    require(limit in 1..200 && offset >= 0) { "Invalid pagination" }
    val values = raw.all().sortedBy { it.id }
    return buildJsonObject { put("collection", name); put("total", values.size); put("records", JsonArray(values.drop(offset).take(limit).map { record(it.id, it.fields) })) }
  }
  override fun get(id: String): JsonObject = raw.get(id)?.let { record(id, it.fields) } ?: throw McpException("Record not found: $name/$id", McpErrorCode.NOT_FOUND)
  private fun record(id: String, fields: JsonObject) = buildJsonObject { put("id", id); put("fields", fields); put("expected", fields) }
  private fun prepare(fields: JsonObject, previous: JsonObject?): Pair<T, JsonObject> {
    validateKeys(fields, serializer.descriptor)
    validateSchemaValues(fields, schema)
    val old = previous?.let { json.decodeFromJsonElement(serializer, it) }
    val value = normalize(old, json.decodeFromJsonElement(serializer, JsonObject(previous.orEmpty() + fields)))
    validate(value)
    val encoded = json.encodeToJsonElement(serializer, value).jsonObject
    val oldEncoded = old?.let { json.encodeToJsonElement(serializer, it).jsonObject }
    val patch = if (oldEncoded == null) encoded else JsonObject(encoded.filter { (key, value) -> key in fields || oldEncoded[key] != value })
    return value to patch
  }
  override fun create(fields: JsonObject): JsonObject {
    require(writable) { "Collection is read only" }
    val (value, normalized) = prepare(fields, null)
    val id = createId(value)
    require(id.isNotBlank()) { "Invalid record id" }
    if (!raw.createIfAbsent(id, normalized)) throw McpException("Record already exists or was deleted; read it before editing", McpErrorCode.CONFLICT)
    return get(id)
  }
  override fun update(id: String, fields: JsonObject, expected: JsonObject): JsonObject {
    require(writable && fields.isNotEmpty()) { "Writable collection and nonempty fields required" }
    val current = raw.get(id) ?: throw McpException("Record not found", McpErrorCode.NOT_FOUND)
    if (current.fields != expected) throw McpException("Record changed; read it again before editing", McpErrorCode.CONFLICT)
    val (_, normalized) = prepare(fields, current.fields)
    if (!raw.mutateIfCurrent(id, expected, normalized)) throw McpException("Record changed; read it again before editing", McpErrorCode.CONFLICT)
    return get(id)
  }
  override fun delete(id: String, expected: JsonObject, confirm: Boolean): JsonObject {
    require(writable && confirm) { "Deletion requires writable mode and confirm=true" }
    if (!raw.mutateIfCurrent(id, expected, null, delete = true)) throw McpException("Record changed or missing; read it again", McpErrorCode.CONFLICT)
    return buildJsonObject { put("deleted", id) }
  }
}

private fun validateKeys(value: JsonElement, descriptor: SerialDescriptor) {
  if (value is JsonNull) return
  when (descriptor.kind) {
    StructureKind.CLASS, StructureKind.OBJECT -> if (value is JsonObject) value.forEach { (key, item) ->
      val index = descriptor.getElementIndex(key)
      require(index != CompositeDecoderUnknown) { "Unknown field: $key" }
      validateKeys(item, descriptor.getElementDescriptor(index))
    }
    StructureKind.LIST -> if (value is JsonArray) value.forEach { validateKeys(it, descriptor.getElementDescriptor(0)) }
    else -> Unit
  }
}
private const val CompositeDecoderUnknown = -3

private fun descriptorSchema(d: SerialDescriptor, depth: Int = 0): JsonObject {
  val base = buildJsonObject {
  if (depth > 8) { put("type", "object"); return@buildJsonObject }
  when (d.kind) {
    StructureKind.CLASS, StructureKind.OBJECT -> {
      put("type", "object")
      put("properties", buildJsonObject { repeat(d.elementsCount) { i -> put(d.getElementName(i), descriptorSchema(d.getElementDescriptor(i), depth + 1)) } })
      put("required", JsonArray((0 until d.elementsCount).filterNot { d.isElementOptional(it) }.map { JsonPrimitive(d.getElementName(it)) }))
      put("additionalProperties", false)
    }
    StructureKind.LIST -> { put("type", "array"); put("items", descriptorSchema(d.getElementDescriptor(0), depth + 1)) }
    StructureKind.MAP -> put("type", "object")
    SerialKind.ENUM -> { put("type", "string"); put("enum", JsonArray((0 until d.elementsCount).map { JsonPrimitive(d.getElementName(it)) })) }
    PrimitiveKind.BOOLEAN -> put("type", "boolean")
    PrimitiveKind.BYTE, PrimitiveKind.SHORT, PrimitiveKind.INT, PrimitiveKind.LONG -> put("type", "integer")
    PrimitiveKind.FLOAT, PrimitiveKind.DOUBLE -> put("type", "number")
    else -> put("type", "string")
  }
  }
  return if (d.isNullable) buildJsonObject { put("anyOf", JsonArray(listOf(base, buildJsonObject { put("type", "null") }))) } else base
}

class ReadOnlyMcpCollection(private val ctx: PluginContext, override val name: String) : McpCollection {
  override val writable = false
  override val schema = buildJsonObject { put("type", "object") }
  override fun list(limit: Int, offset: Int): JsonObject {
    require(limit in 1..200 && offset >= 0)
    val records = ctx.data.raw(name).all().sortedBy { it.id }
    return buildJsonObject { put("total", records.size); put("records", JsonArray(records.drop(offset).take(limit).map { record(it.id, it.fields) })) }
  }
  private fun record(id: String, fields: JsonObject) = buildJsonObject { put("id", id); put("fields", fields); put("expected", fields) }
  override fun get(id: String): JsonObject = ctx.data.raw(name).get(id)?.let { record(it.id, it.fields) } ?: throw McpException("Record not found", McpErrorCode.NOT_FOUND)
  override fun create(fields: JsonObject): JsonObject = throw McpException("Derived or imported collection is read only", McpErrorCode.READ_ONLY)
  override fun update(id: String, fields: JsonObject, expected: JsonObject): JsonObject = throw McpException("Derived or imported collection is read only", McpErrorCode.READ_ONLY)
  override fun delete(id: String, expected: JsonObject, confirm: Boolean): JsonObject = throw McpException("Derived or imported collection is read only", McpErrorCode.READ_ONLY)
}

fun <T> mcpSchema(serializer: KSerializer<T>): JsonObject = descriptorSchema(serializer.descriptor)

fun <E : Enum<E>> mcpEnumSchema(serializer: KSerializer<E>, values: List<E>): JsonObject = buildJsonObject {
  put("type", "string")
  put("enum", JsonArray((values.map { JsonPrimitive(it.name) } + values.map { Json.encodeToJsonElement(serializer, it) }).distinct()))
}

fun JsonObject.withMcpField(name: String, schema: JsonObject): JsonObject = JsonObject(this + ("properties" to JsonObject((this["properties"] as? JsonObject).orEmpty() + (name to schema))))

private fun validateSchemaValues(value: JsonElement, schema: JsonObject) {
  (schema["enum"] as? JsonArray)?.let { allowed -> require(value in allowed) { "Unsupported value $value; allowed values: $allowed" } }
  if (value is JsonObject) (schema["properties"] as? JsonObject)?.forEach { (key, rule) -> value[key]?.let { if (rule is JsonObject) validateSchemaValues(it, rule) } }
  if (value is JsonArray) (schema["items"] as? JsonObject)?.let { rule -> value.forEach { validateSchemaValues(it, rule) } }
  if (value != JsonNull) (schema["anyOf"] as? JsonArray)?.firstOrNull { (it as? JsonObject)?.get("type") != JsonPrimitive("null") }?.let { if (it is JsonObject) validateSchemaValues(value, it) }
}
