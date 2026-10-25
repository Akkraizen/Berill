package dev.akkraizen.berill.core.external

import dev.akkraizen.berill.sdk.ExternalPlugin
import dev.akkraizen.berill.sdk.Host
import dev.akkraizen.berill.sdk.HostData
import dev.akkraizen.berill.sdk.HostException
import dev.akkraizen.berill.sdk.HostHttp
import dev.akkraizen.berill.sdk.HostNav
import dev.akkraizen.berill.sdk.HostSettings
import dev.akkraizen.berill.sdk.InternalSdkApi
import dev.akkraizen.berill.sdk.Record
import dev.akkraizen.berill.sdk.UiBuilder
import dev.akkraizen.berill.sdk.protocol.FromRunner
import dev.akkraizen.berill.sdk.protocol.HostMethods
import dev.akkraizen.berill.sdk.protocol.ProtocolJson
import dev.akkraizen.berill.sdk.protocol.Target
import dev.akkraizen.berill.sdk.protocol.TargetKind
import dev.akkraizen.berill.sdk.protocol.ToRunner
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

interface ReplySlot {
  fun await(timeoutMs: Long): ToRunner.Reply?
}

interface RunnerPlatform {

  fun execute(task: () -> Unit)

  fun slot(callId: Long): ReplySlot

  fun complete(reply: ToRunner.Reply)
}

private const val CALL_TIMEOUT_MS = 30_000L

@OptIn(InternalSdkApi::class)
class RunnerRuntime(
  private val platform: RunnerPlatform,
  private val send: (FromRunner) -> Unit,
  private val instantiate: (ToRunner.Load) -> ExternalPlugin,
  private val onCode: (ToRunner.Code) -> Unit = {}
) {
  private var plugin: ExternalPlugin? = null
  private var language = "ru"
  private var nextCallId = 1L
  private val actions = HashMap<Target, Map<String, (String?) -> Unit>>()

  fun onMessage(message: ToRunner) {
    if (message is ToRunner.Reply)
      platform.complete(message)
    else
      platform.execute { handle(message) }
  }

  private fun handle(message: ToRunner) {
    when (message) {
      is ToRunner.Code -> onCode(message)
      is ToRunner.Load -> load(message)
      is ToRunner.Language -> language = message.language
      is ToRunner.Render -> send(render(message.id, message.target))
      is ToRunner.Event -> {
        val handler = actions[message.target]?.get(message.action)
        val error = handler?.let { h -> runCatching { h(message.value) }.exceptionOrNull() }
        send(render(message.id, message.target, error))
      }

      is ToRunner.Reply -> platform.complete(message)
    }
  }

  private fun load(message: ToRunner.Load) {
    language = message.language
    val result = runCatching {
      instantiate(message).also { p ->
        p.attach(host)
        plugin = p
        p.onStart()
      }
    }
    send(FromRunner.Loaded(result.isSuccess, result.exceptionOrNull()?.describe()))
  }

  private fun render(id: Long, target: Target, previousError: Throwable? = null): FromRunner.Tree {
    val p = plugin ?: return FromRunner.Tree(id, error = "plugin is not loaded")
    val handlers = LinkedHashMap<String, (String?) -> Unit>()
    val builder = UiBuilder(handlers)
    val error = runCatching {
      with(p) {
        when (target.kind) {
          TargetKind.Page -> builder.page(target.id, target.route)
          TargetKind.HomeCard -> builder.homeCard(target.id)
          TargetKind.Settings -> builder.settings()
        }
      }
    }
      .exceptionOrNull()
    if (error != null) return FromRunner.Tree(id, error = error.describe())
    actions[target] = handlers
    return FromRunner.Tree(id, builder.build(), previousError?.describe())
  }

  private fun call(method: String, args: JsonObject = JsonObject(emptyMap())): JsonElement? {
    val callId = nextCallId++
    val slot = platform.slot(callId)
    send(FromRunner.Call(callId, method, args))
    val reply = slot.await(CALL_TIMEOUT_MS) ?: throw HostException("timeout", "Host did not answer $method")
    reply.error?.let { throw HostException(reply.code ?: "error", it) }
    return reply.result
  }

  private val host = object : Host {
    override val language: String
      get() = this@RunnerRuntime.language

    override val data = object : HostData {
      override fun newId(): String = call(HostMethods.DATA_NEW_ID)?.jsonPrimitive?.content ?: error("no id")

      override fun get(collection: String, id: String): JsonObject? =
        (
          call(
            HostMethods.DATA_GET,
            buildJsonObject {
              put("collection", collection)
              put("id", id)
            }
          )
            as? JsonObject
        )

      override fun list(collection: String): List<Record> {
        val result = call(HostMethods.DATA_LIST, buildJsonObject { put("collection", collection) })
          ?: return emptyList()
        return ProtocolJson.decodeFromJsonElement(ListSerializer(Record.serializer()), result)
      }

      override fun put(collection: String, id: String, fields: JsonObject) {
        call(
          HostMethods.DATA_PUT,
          buildJsonObject {
            put("collection", collection)
            put("id", id)
            put("fields", fields)
          }
        )
      }

      override fun delete(collection: String, id: String) {
        call(
          HostMethods.DATA_DELETE,
          buildJsonObject {
            put("collection", collection)
            put("id", id)
          }
        )
      }
    }

    override val settings = object : HostSettings {
      override fun get(key: String): JsonElement? =
        call(HostMethods.SETTINGS_GET, buildJsonObject { put("key", key) })?.takeUnless {
          it is JsonNull
        }

      override fun set(key: String, value: JsonElement) {
        call(
          HostMethods.SETTINGS_SET,
          buildJsonObject {
            put("key", key)
            put("value", value)
          }
        )
      }
    }

    override val http = object : HostHttp {
      override fun getJson(url: String, headers: Map<String, String>): JsonElement =
        call(
          HostMethods.HTTP_GET_JSON,
          buildJsonObject {
            put("url", url)
            put("headers", JsonObject(headers.mapValues { JsonPrimitive(it.value) }))
          }
        ) ?: JsonNull
    }

    override val nav = object : HostNav {
      override fun open(route: String?) {
        call(HostMethods.NAV_OPEN, buildJsonObject { put("route", route) })
      }

      override fun openPage(pageId: String) {
        call(HostMethods.NAV_OPEN_PAGE, buildJsonObject { put("page", pageId) })
      }

      override fun back() {
        call(HostMethods.NAV_BACK)
      }
    }

    override fun toast(text: String) {
      call(HostMethods.TOAST, buildJsonObject { put("text", text.take(300)) })
    }

    override fun invalidate() = send(FromRunner.Invalidate)

    override fun log(message: String) = send(FromRunner.Log(message.take(2000)))
  }
}

internal fun Throwable.describe(): String {
  val name = this::class.simpleName ?: "Error"
  val msg = message.orEmpty()
  return if (name == "NoClassDefFoundError" || name == "ClassNotFoundException") {
    "blocked API: ${
      msg.replace('/', '.').substringAfter("Berill: plugins may not use ").take(200)
    }"
  } else {
    "$name: ${msg.take(500)}"
  }
}

internal fun JsonObject.string(key: String): String? = this[key]?.takeUnless { it is JsonNull }?.jsonPrimitive?.contentOrNull

internal fun JsonObject.obj(key: String): JsonObject? = (this[key] as? JsonObject) ?: this[key]?.takeUnless { it is JsonNull }?.jsonObject
