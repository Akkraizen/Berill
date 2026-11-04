package dev.akkraizen.berill.api.mcp

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put

class McpAction(
  val name: String,
  val description: String,
  val input: McpInput,
  val readOnly: Boolean = false,
  val destructive: Boolean = false,
  val run: (JsonObject) -> JsonElement,
) {
  init {
    require(NAME.matches(name)) { "Invalid MCP action name: $name" }
  }

  private companion object {
    val NAME = Regex("[a-z][a-z0-9_]{1,40}")
  }
}

class McpInput internal constructor(val properties: JsonObject, val required: List<String>)

fun mcpInput(block: McpInputBuilder.() -> Unit): McpInput = McpInputBuilder().apply(block).build()

class McpInputBuilder internal constructor() {
  private val properties = linkedMapOf<String, JsonObject>()
  private val required = mutableListOf<String>()

  fun string(name: String, description: String, required: Boolean = false) =
    add(name, required, buildJsonObject { put("type", "string"); put("description", description) })

  fun integer(name: String, description: String, required: Boolean = false, min: Int? = null, max: Int? = null) =
    add(name, required, buildJsonObject {
      put("type", "integer")
      put("description", description)
      min?.let { put("minimum", it) }
      max?.let { put("maximum", it) }
    })

  fun number(name: String, description: String, required: Boolean = false) =
    add(name, required, buildJsonObject { put("type", "number"); put("description", description) })

  fun boolean(name: String, description: String, required: Boolean = false) =
    add(name, required, buildJsonObject { put("type", "boolean"); put("description", description) })

  fun choice(name: String, description: String, values: List<String>, required: Boolean = false) =
    add(name, required, buildJsonObject {
      put("type", "string")
      put("description", description)
      put("enum", JsonArray(values.map(::JsonPrimitive)))
    })

  fun integers(name: String, description: String, required: Boolean = false) =
    add(name, required, buildJsonObject {
      put("type", "array")
      put("description", description)
      put("items", buildJsonObject { put("type", "integer") })
    })

  fun strings(name: String, description: String, required: Boolean = false) =
    add(name, required, buildJsonObject {
      put("type", "array")
      put("description", description)
      put("items", buildJsonObject { put("type", "string") })
    })

  fun obj(name: String, description: String, required: Boolean = false) =
    add(name, required, buildJsonObject { put("type", "object"); put("description", description) })

  private fun add(name: String, isRequired: Boolean, schema: JsonObject) {
    properties[name] = schema
    if (isRequired) required += name
  }

  internal fun build() = McpInput(JsonObject(properties), required.toList())
}

private fun JsonObject.primitive(name: String): JsonPrimitive? = (this[name] as? JsonPrimitive)?.takeUnless { it.content == "null" && !it.isString }

fun JsonObject.mcpOptionalString(name: String): String? =
  primitive(name)?.let { if (it.isString) it.content else throw McpException("`$name` must be text") }

fun JsonObject.mcpString(name: String): String =
  mcpOptionalString(name)?.takeIf { it.isNotBlank() } ?: throw McpException("Missing text argument `$name`")

fun JsonObject.mcpOptionalInt(name: String, range: IntRange = Int.MIN_VALUE..Int.MAX_VALUE): Int? {
  val value = primitive(name) ?: return null
  val number = value.takeUnless { it.isString }?.intOrNull ?: throw McpException("`$name` must be an integer")
  if (number !in range) throw McpException("`$name` must be between ${range.first} and ${range.last}")
  return number
}

fun JsonObject.mcpInt(name: String, range: IntRange = Int.MIN_VALUE..Int.MAX_VALUE): Int =
  mcpOptionalInt(name, range) ?: throw McpException("Missing integer argument `$name`")

fun JsonObject.mcpOptionalDouble(name: String): Double? {
  val value = primitive(name) ?: return null
  return value.takeUnless { it.isString }?.doubleOrNull?.takeIf { it.isFinite() } ?: throw McpException("`$name` must be a number")
}

fun JsonObject.mcpOptionalBoolean(name: String): Boolean? {
  val value = primitive(name) ?: return null
  return value.takeUnless { it.isString }?.booleanOrNull ?: throw McpException("`$name` must be true or false")
}

fun JsonObject.mcpIntList(name: String): List<Int>? {
  val value = this[name] ?: return null
  val array = value as? JsonArray ?: throw McpException("`$name` must be an array of integers")
  return array.map { (it as? JsonPrimitive)?.takeUnless { p -> p.isString }?.intOrNull ?: throw McpException("`$name` must contain integers") }
}

fun JsonObject.mcpStringList(name: String): List<String>? {
  val value = this[name] ?: return null
  val array = value as? JsonArray ?: throw McpException("`$name` must be an array of strings")
  return array.map { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content ?: throw McpException("`$name` must contain strings") }
}
