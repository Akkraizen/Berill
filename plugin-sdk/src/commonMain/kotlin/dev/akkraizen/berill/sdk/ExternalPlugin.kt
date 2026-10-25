package dev.akkraizen.berill.sdk

import dev.akkraizen.berill.sdk.i18n.TranslationCatalog
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.serializer

abstract class ExternalPlugin {
  private var boundHost: Host? = null

  val host: Host
    get() = boundHost ?: error("Host is not connected yet: use it from onStart()")

  @InternalSdkApi
  fun attach(host: Host) {
    boundHost = host
  }

  open fun onStart() {}

  abstract fun UiBuilder.page(pageId: String, route: String?)

  open fun UiBuilder.homeCard(cardId: String) {}

  open fun UiBuilder.settings() {}

  abstract val translations: TranslationCatalog

  fun tr(key: String, vararg arguments: Pair<String, Any?>): String =
    translations.text(key, host.language, arguments.toMap())
}

interface Host {

  val language: String
  val data: HostData
  val settings: HostSettings
  val http: HostHttp
  val nav: HostNav

  fun toast(text: String)

  fun invalidate()

  fun log(message: String)
}

@Serializable
data class Record(val id: String, val fields: JsonObject)

interface HostData {
  fun newId(): String

  fun get(collection: String, id: String): JsonObject?

  fun list(collection: String): List<Record>

  fun put(collection: String, id: String, fields: JsonObject)

  fun delete(collection: String, id: String)
}

interface HostSettings {
  fun get(key: String): JsonElement?

  fun set(key: String, value: JsonElement)
}

interface HostHttp {
  fun getJson(url: String, headers: Map<String, String> = emptyMap()): JsonElement
}

interface HostNav {

  fun open(route: String?)

  fun openPage(pageId: String)

  fun back()
}

class HostException(val code: String, message: String) : RuntimeException(message)

val SdkJson: Json = Json {
  ignoreUnknownKeys = true
  encodeDefaults = true
}

class TypedCollection<T>(
  private val data: HostData,
  val name: String,
  private val serializer: KSerializer<T>
) {
  fun get(id: String): T? = data.get(name, id)?.let { decode(it) }

  fun all(): List<Pair<String, T>> =
    data.list(name).mapNotNull { r -> decode(r.fields)?.let { r.id to it } }

  fun save(id: String, value: T) =
    data.put(name, id, SdkJson.encodeToJsonElement(serializer, value).jsonObject)

  fun delete(id: String) = data.delete(name, id)

  private fun decode(fields: JsonObject): T? =
    runCatching { SdkJson.decodeFromJsonElement(serializer, fields) }.getOrNull()
}

inline fun <reified T> HostData.collection(name: String): TypedCollection<T> =
  TypedCollection(this, name, serializer<T>())
