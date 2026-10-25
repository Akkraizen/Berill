package dev.akkraizen.berill.core.external

import androidx.compose.runtime.snapshotFlow
import dev.akkraizen.berill.api.HttpException
import dev.akkraizen.berill.api.NetworkNotAllowedException
import dev.akkraizen.berill.api.PluginContext
import dev.akkraizen.berill.core.log.AppLog
import dev.akkraizen.berill.api.i18n.I18n
import dev.akkraizen.berill.sdk.UiNode
import dev.akkraizen.berill.sdk.protocol.FromRunner
import dev.akkraizen.berill.sdk.protocol.HostMethods
import dev.akkraizen.berill.sdk.protocol.PluginManifest
import dev.akkraizen.berill.sdk.protocol.Target
import dev.akkraizen.berill.sdk.protocol.ToRunner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

sealed interface RemoteState {
  data object Loading : RemoteState

  data class Ready(val nodes: List<UiNode>, val error: String? = null) : RemoteState

  data class Failed(val message: String) : RemoteState
}

private const val LOAD_TIMEOUT_MS = 20_000L
private const val RENDER_TIMEOUT_MS = 8_000L
private const val MAX_FIELDS_BYTES = 64 * 1024
private const val MAX_CRASHES = 3
private const val CRASH_WINDOW_MS = 60_000L

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class, ExperimentalTime::class)
class ExternalPluginClient(
  val manifest: PluginManifest,
  private val launcher: RunnerLauncher,
  private val ctx: PluginContext,
  private val scope: CoroutineScope
) {
  private val serial = Dispatchers.Default.limitedParallelism(1)
  private val states = MutableStateFlow<Map<Target, RemoteState>>(emptyMap())
  private val mounted = HashMap<Target, Int>()
  private val pending = HashMap<Long, CompletableDeferred<FromRunner.Tree>>()
  private var connection: RunnerConnection? = null
  private var loaded: CompletableDeferred<String?>? = null
  private var generation = 0
  private var nextId = 1L
  private var failure: String? = null
  private val crashes = ArrayDeque<Long>()

  private val _toast = MutableStateFlow<Pair<Long, String>?>(null)

  val toast: StateFlow<Pair<Long, String>?> = _toast

  private val _status = MutableStateFlow<String?>(null)

  val status: StateFlow<String?> = _status

  init {

    val sources = manifest.collections.map { name -> ctx.data.raw(name).observe() }
    if (sources.isNotEmpty()) {
      scope.launch { sources.merge().drop(sources.size).debounce(400).collect { refreshMounted() } }
    }

    scope.launch {
      snapshotFlow { I18n.language }
        .drop(1)
        .collect { lang ->
          withContext(serial) { connection?.send(ToRunner.Language(lang.code)) }
          refreshMounted()
        }
    }
  }

  fun state(target: Target): Flow<RemoteState> = states.map { it[target] ?: RemoteState.Loading }.distinctUntilChanged()

  fun mount(target: Target) {
    scope.launch(serial) {
      val n = (mounted[target] ?: 0) + 1
      mounted[target] = n
      if (n == 1) request(target) { ToRunner.Render(it, target) }
    }
  }

  fun unmount(target: Target) {
    scope.launch(serial) {
      val n = (mounted[target] ?: 1) - 1
      if (n <= 0) mounted.remove(target) else mounted[target] = n
    }
  }

  fun event(target: Target, action: String, value: String?) {
    scope.launch(serial) { request(target) { ToRunner.Event(it, target, action, value) } }
  }

  fun retry() {
    scope.launch(serial) {
      failure = null
      crashes.clear()
      stopProcess()
      mounted.keys.toList().forEach { t ->
        scope.launch(serial) { request(t) { ToRunner.Render(it, t) } }
      }
    }
  }

  private fun refreshMounted() {
    scope.launch(serial) {
      mounted.keys.toList().forEach { t ->
        scope.launch(serial) { request(t) { ToRunner.Render(it, t) } }
      }
    }
  }

  private fun setState(target: Target, state: RemoteState) {
    states.value = states.value + (target to state)
  }

  private suspend fun request(target: Target, make: (Long) -> ToRunner) {
    val conn = ensureStarted()
    if (conn == null) {
      setState(target, RemoteState.Failed(failure ?: ExternalText.notStarted()))
      return
    }
    val id = nextId++
    val deferred = CompletableDeferred<FromRunner.Tree>()
    pending[id] = deferred
    conn.send(make(id))
    val tree = withTimeoutOrNull(RENDER_TIMEOUT_MS) { deferred.await() }
    pending.remove(id)
    when {
      tree == null -> {

        registerCrash(ExternalText.notResponding())
        stopProcess()
        setState(target, RemoteState.Failed(ExternalText.notResponding()))
      }

      tree.error != null && tree.nodes.isEmpty() ->
        setState(target, RemoteState.Failed(tree.error ?: ""))

      else -> setState(target, RemoteState.Ready(tree.nodes, tree.error))
    }
  }

  private suspend fun ensureStarted(): RunnerConnection? {
    failure?.let {
      return null
    }
    var conn = connection
    if (conn == null) {
      val gen = ++generation
      val ready = CompletableDeferred<String?>()
      loaded = ready
      conn = try {
        launcher.start(manifest, I18n.language.code, listener(gen))
      } catch (e: CancellationException) {
        throw e
      } catch (e: Throwable) {
        failure = ExternalText.startFailed(e.message ?: e::class.simpleName.orEmpty())()
        _status.value = failure
        return null
      }
      connection = conn
      val result = withTimeoutOrNull(LOAD_TIMEOUT_MS) { ready.await() ?: "" }
      when {
        result == null -> {
          failure = ExternalText.notResponding()
          _status.value = failure
          stopProcess()
          return null
        }

        result.isNotEmpty() -> {
          failure = ExternalText.loadFailed(result)()
          _status.value = failure
          stopProcess()
          return null
        }

        else -> _status.value = null
      }
    } else {

      val ready = loaded
      if (ready != null && !ready.isCompleted) withTimeoutOrNull(LOAD_TIMEOUT_MS) { ready.await() }
    }
    return connection
  }

  private fun stopProcess() {
    generation++
    connection?.let { runCatching { it.close() } }
    connection = null
    loaded = null
    pending.values.forEach { it.cancel() }
    pending.clear()
  }

  private fun registerCrash(reason: String) {
    val now = Clock.System.now().toEpochMilliseconds()
    crashes.addLast(now)
    while (crashes.isNotEmpty() && now - crashes.first() > CRASH_WINDOW_MS) crashes.removeFirst()
    _status.value = reason
    if (crashes.size >= MAX_CRASHES) failure = ExternalText.tooManyCrashes(reason)()
  }

  private fun listener(gen: Int) =
    object : RunnerListener {
      override fun onMessage(message: FromRunner) {
        scope.launch(serial) { if (gen == generation) handle(message) }
      }

      override fun onExit(reason: String) {
        scope.launch(serial) {
          if (gen != generation) return@launch
          connection = null
          loaded?.complete(reason.ifEmpty { "exit" })
          pending.values.forEach {
            it.complete(FromRunner.Tree(-1, error = ExternalText.crashed(reason)()))
          }
          pending.clear()
          registerCrash(ExternalText.crashed(reason)())

          if (failure == null && mounted.isNotEmpty()) {
            delay(500)
            refreshMounted()
          } else if (failure != null) {
            mounted.keys.forEach { setState(it, RemoteState.Failed(failure!!)) }
          }
        }
      }
    }

  private fun handle(message: FromRunner) {
    when (message) {
      is FromRunner.Loaded -> loaded?.complete(if (message.ok) null else (message.error ?: "error"))
      is FromRunner.Tree -> pending[message.id]?.complete(message)
      is FromRunner.Call -> {
        val conn = connection
        scope.launch(Dispatchers.Default) {
          val reply = execute(message)
          runCatching { conn?.send(reply) }
        }
      }

      FromRunner.Invalidate -> refreshMounted()
      is FromRunner.Log -> AppLog.info("plugin:${manifest.id}", message.message)
    }
  }

  private suspend fun execute(call: FromRunner.Call): ToRunner.Reply {
    val a = call.args

    fun arg(name: String): String = a.string(name) ?: throw IllegalArgumentException("missing $name")
    return try {
      val result: JsonElement? = when (call.method) {
        HostMethods.DATA_NEW_ID -> JsonPrimitive(ctx.data.newId())
        HostMethods.DATA_GET ->
          ctx.data.raw(arg("collection")).get(arg("id"))?.fields ?: JsonNull

        HostMethods.DATA_LIST ->
          JsonArray(
            ctx.data.raw(arg("collection")).observe().first().map { r ->
              buildJsonObject {
                put("id", r.id)
                put("fields", r.fields)
              }
            }
          )

        HostMethods.DATA_PUT -> {
          val fields = a.obj("fields") ?: JsonObject(emptyMap())
          require(fields.toString().length <= MAX_FIELDS_BYTES) { "record is too large" }
          require(arg("id").length in 1..64) { "bad id" }
          ctx.data.raw(arg("collection")).patch(arg("id"), fields)
          null
        }

        HostMethods.DATA_DELETE -> {
          ctx.data.raw(arg("collection")).delete(arg("id"))
          null
        }

        HostMethods.SETTINGS_GET -> ctx.settings.get(arg("key")) ?: JsonNull
        HostMethods.SETTINGS_SET -> {
          val value = a["value"] ?: JsonNull
          require(value.toString().length <= MAX_FIELDS_BYTES) { "value is too large" }
          ctx.settings.set(arg("key"), value)
          null
        }

        HostMethods.HTTP_GET_JSON -> {
          val headers = a.obj("headers")
            ?.mapNotNull { (k, v) ->
              (v as? JsonPrimitive)?.contentOrNull?.let { k to it }
            }
            ?.toMap()
            .orEmpty()
          ctx.http.getJson(arg("url"), headers)
        }

        HostMethods.NAV_OPEN -> {
          val route = a.string("route")
          require(route == null || route.length <= 200) { "route is too long" }
          withContext(Dispatchers.Main) { ctx.nav.open(route) }
          null
        }

        HostMethods.NAV_OPEN_PAGE -> {
          val page = arg("page")
          require(manifest.pages.any { it.id == page }) { "unknown page $page" }
          withContext(Dispatchers.Main) { ctx.nav.openPage(page) }
          null
        }

        HostMethods.NAV_BACK -> {

          if (ctx.nav.route.value != null) withContext(Dispatchers.Main) { ctx.nav.back() }
          null
        }

        HostMethods.TOAST -> {
          _toast.value = Clock.System.now().toEpochMilliseconds() to arg("text").take(300)
          null
        }

        else -> throw IllegalArgumentException("unknown method ${call.method}")
      }
      ToRunner.Reply(call.callId, result)
    } catch (e: CancellationException) {
      throw e
    } catch (e: NetworkNotAllowedException) {
      ToRunner.Reply(call.callId, error = e.message, code = "denied")
    } catch (e: IllegalArgumentException) {
      ToRunner.Reply(call.callId, error = e.message, code = "denied")
    } catch (e: HttpException) {
      ToRunner.Reply(call.callId, error = e.message, code = "http_${e.status}")
    } catch (e: Throwable) {
      ToRunner.Reply(
        call.callId,
        error = e.message ?: e::class.simpleName,
        code = if (call.method == HostMethods.HTTP_GET_JSON) {
          "network"
        } else {
          "error"
        }
      )
    }
  }
}
