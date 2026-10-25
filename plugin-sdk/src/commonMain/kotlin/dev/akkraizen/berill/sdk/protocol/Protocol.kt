package dev.akkraizen.berill.sdk.protocol

import dev.akkraizen.berill.sdk.UiNode
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

const val PROTOCOL_VERSION = 1

val ProtocolJson: Json = Json {
  ignoreUnknownKeys = true
  encodeDefaults = false
  classDiscriminator = "type"
}

@Serializable
enum class TargetKind {
  Page,
  HomeCard,
  Settings
}

@Serializable
data class Target(val kind: TargetKind, val id: String, val route: String? = null)

@Serializable
sealed interface ToRunner {

  @Serializable
  @SerialName("load")
  data class Load(
    val entry: String,
    val language: String,
    val code: String? = null,
    val protocol: Int = PROTOCOL_VERSION
  ) : ToRunner

  @Serializable
  @SerialName("code")
  data class Code(val chunk: String) : ToRunner

  @Serializable
  @SerialName("render")
  data class Render(val id: Long, val target: Target) : ToRunner

  @Serializable
  @SerialName("event")
  data class Event(val id: Long, val target: Target, val action: String, val value: String? = null) : ToRunner

  @Serializable
  @SerialName("reply")
  data class Reply(
    val callId: Long,
    val result: JsonElement? = null,
    val error: String? = null,
    val code: String? = null
  ) : ToRunner

  @Serializable
  @SerialName("language")
  data class Language(val language: String) : ToRunner
}

@Serializable
sealed interface FromRunner {
  @Serializable
  @SerialName("loaded")
  data class Loaded(val ok: Boolean, val error: String? = null) : FromRunner

  @Serializable
  @SerialName("tree")
  data class Tree(val id: Long, val nodes: List<UiNode> = emptyList(), val error: String? = null) : FromRunner

  @Serializable
  @SerialName("call")
  data class Call(val callId: Long, val method: String, val args: JsonObject = JsonObject(emptyMap())) : FromRunner

  @Serializable
  @SerialName("invalidate")
  data object Invalidate : FromRunner

  @Serializable
  @SerialName("log")
  data class Log(val message: String) : FromRunner
}

object HostMethods {
  const val DATA_NEW_ID = "data.newId"
  const val DATA_GET = "data.get"
  const val DATA_LIST = "data.list"
  const val DATA_PUT = "data.put"
  const val DATA_DELETE = "data.delete"
  const val SETTINGS_GET = "settings.get"
  const val SETTINGS_SET = "settings.set"
  const val HTTP_GET_JSON = "http.getJson"
  const val NAV_OPEN = "nav.open"
  const val NAV_OPEN_PAGE = "nav.openPage"
  const val NAV_BACK = "nav.back"
  const val TOAST = "toast"
}
