package dev.akkraizen.berill.api

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

interface PluginSettings {
  fun get(key: String): JsonElement?

  fun observe(key: String): Flow<JsonElement?>

  fun set(key: String, value: JsonElement)
}

fun PluginSettings.string(key: String): String? = get(key)?.jsonPrimitive?.contentOrNull

fun PluginSettings.boolean(
  key: String,
  default: Boolean = false
): Boolean = get(key)?.jsonPrimitive?.booleanOrNull ?: default

fun PluginSettings.observeString(key: String): Flow<String?> = observe(key).map { it?.jsonPrimitive?.contentOrNull }

fun PluginSettings.set(key: String, value: String) = set(key, JsonPrimitive(value))

fun PluginSettings.set(key: String, value: Boolean) = set(key, JsonPrimitive(value))

fun PluginSettings.stringList(key: String): List<String>? = (get(key) as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }

fun PluginSettings.observeStringList(key: String): Flow<List<String>?> =
  observe(key).map { e ->
    (e as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
  }

fun PluginSettings.setStringList(
  key: String,
  value: List<String>
) = set(key, JsonArray(value.map { JsonPrimitive(it) }))

fun PluginSettings.observeBoolean(
  key: String,
  default: Boolean
): Flow<Boolean> = observe(key).map { (it as? JsonPrimitive)?.booleanOrNull ?: default }
