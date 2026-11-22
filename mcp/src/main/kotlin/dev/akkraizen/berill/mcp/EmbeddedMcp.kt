package dev.akkraizen.berill.mcp

import dev.akkraizen.berill.core.BerillCore
import dev.akkraizen.berill.core.log.AppLog
import dev.akkraizen.berill.core.platform.McpAccess
import dev.akkraizen.berill.core.platform.McpClientConfig
import dev.akkraizen.berill.core.platform.McpStatus
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.respondText
import io.modelcontextprotocol.kotlin.sdk.server.mcpStreamableHttp
import java.io.File
import java.net.BindException
import java.net.InetAddress
import java.net.ServerSocket
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class McpEndpoint(val url: String, val pid: Long)

class EmbeddedMcp(
  private val core: BerillCore,
  private val dataDir: File,
  override val bridgeCommand: String? = null,
  private val preferredPort: Int = DEFAULT_PORT,
  private val reclaimInterval: Duration = 30.seconds
) : McpAccess {
  private val store = core.store
  private val vault = core.vault
  private val lock = Any()
  private var server: EmbeddedServer<*, *>? = null
  private var reclaim: Job? = null

  private val _status = MutableStateFlow(McpStatus(enabled = flag(ENABLED), writable = flag(WRITABLE)))
  override val status: StateFlow<McpStatus> = _status

  private fun flag(key: String) = store.meta(key) == "1"

  override fun start() {
    if (flag(ENABLED)) restart()
  }

  override fun stop() =
    synchronized(lock) {
      shutdown()
      _status.update { it.copy(running = false, url = null) }
    }

  override fun setEnabled(enabled: Boolean) {
    store.setMeta(ENABLED, if (enabled) "1" else "0")
    _status.update { it.copy(enabled = enabled, error = null) }
    if (enabled) restart() else stop()
  }

  override fun setWritable(writable: Boolean) {
    store.setMeta(WRITABLE, if (writable) "1" else "0")
    _status.update { it.copy(writable = writable) }
    if (_status.value.enabled) restart()
  }

  override fun token(): String =
    synchronized(lock) {
      vault.read(TOKEN_SECRET)?.takeIf { it.isNotBlank() } ?: newToken().also { vault.write(TOKEN_SECRET, it) }
    }

  override fun regenerateToken() {
    synchronized(lock) { vault.write(TOKEN_SECRET, newToken()) }
    if (_status.value.enabled) restart()
  }

  override fun desktopClientConfig(): String = McpClients.render(McpClient.CLAUDE_DESKTOP, SERVER_NAME, preferredLaunch())

  override fun httpClientConfig(): String = McpClients.render(McpClient.GENERIC, SERVER_NAME, httpLaunch())

  override fun claudeCodeCommand(): String = McpClients.render(McpClient.CLAUDE_CODE, SERVER_NAME, preferredLaunch())

  override fun clientConfigs(): List<McpClientConfig> = McpClients.all(SERVER_NAME, preferredLaunch())

  private fun preferredLaunch(): McpLaunch = bridgeCommand?.let { McpLaunch.Stdio(it, listOf(BRIDGE_FLAG)) } ?: httpLaunch()

  private fun httpLaunch(): McpLaunch.Http = McpLaunch.Http(url(), token())

  private fun url(): String = _status.value.url ?: urlFor(store.meta(PORT)?.toIntOrNull() ?: preferredPort)

  private fun restart() =
    synchronized(lock) {
      shutdown()
      val token = token()
      val writable = flag(WRITABLE)
      // The default port comes first: clients configured with a URL point at it, so a fallback chosen while
      // it was busy (for example while a previous instance was still exiting) must not become permanent.
      val ports = listOfNotNull(preferredPort.takeIf { it > 0 }, store.meta(PORT)?.toIntOrNull()?.takeIf { it > 0 }, 0).distinct()
      var lastError: Throwable? = null
      for (port in ports) {
        // CIO binds in a background coroutine: a busy port also surfaces there as an uncaught crash and leaves
        // a half-started engine behind, so check the port first and stop a server that failed anyway.
        if (port != 0 && !portIsFree(port)) {
          lastError = BindException("port $port is busy")
          continue
        }
        val candidate = runCatching { createServer(port, token, writable) }.onFailure { lastError = it }.getOrNull() ?: continue
        val started = runCatching { candidate.also { it.start(wait = false) } }
          .onFailure {
            lastError = it
            runCatching { candidate.stop(gracePeriodMillis = 0, timeoutMillis = 500) }
          }
          .getOrNull() ?: continue
        val actual = runBlocking { started.engine.resolvedConnectors().first().port }
        server = started
        store.setMeta(PORT, actual.toString())
        val url = urlFor(actual)
        writeEndpoint(url)
        _status.update { it.copy(running = true, url = url, error = null) }
        AppLog.info(TAG, "MCP server listening on $url, writable=$writable")
        if (preferredPort > 0 && actual != preferredPort) {
          AppLog.warn(TAG, "port $preferredPort is busy; clients configured with ${urlFor(preferredPort)} reconnect once it is free")
          reclaimPreferredPort()
        }
        return@synchronized
      }
      AppLog.error(TAG, "MCP server failed to start", lastError)
      _status.update { it.copy(running = false, url = null, error = lastError?.let(::describe) ?: "unknown error") }
    }

  private fun describe(e: Throwable): String = if (e is BindException) "port is busy" else e.message ?: e::class.simpleName.orEmpty()

  private fun createServer(port: Int, token: String, writable: Boolean): EmbeddedServer<*, *> {
    val tools = BerillTools(core)
    val expected = "Bearer $token".toByteArray()
    val protocol = createBerillServer(tools, readOnly = !writable)
    return embeddedServer(CIO, host = HOST, port = port) {
      monitor.subscribe(ApplicationStopped) { runBlocking { protocol.close() } }
      intercept(ApplicationCallPipeline.Plugins) {
        val given = call.request.headers["Authorization"]?.toByteArray() ?: ByteArray(0)
        if (!MessageDigest.isEqual(given, expected)) {
          call.respondText("Unauthorized", status = HttpStatusCode.Unauthorized)
          finish()
        }
      }
      mcpStreamableHttp(path = PATH, enableDnsRebindingProtection = true) { protocol }
    }
  }

  /** Moves the server back to [preferredPort] as soon as whatever held it lets it go. */
  private fun reclaimPreferredPort() {
    reclaim = core.scope.launch(Dispatchers.IO) {
      while (true) {
        delay(reclaimInterval)
        if (!portIsFree(preferredPort)) continue
        synchronized(lock) {
          if (reclaim === coroutineContext[Job] && server != null) {
            reclaim = null
            restart()
          }
        }
        break
      }
    }
  }

  private fun portIsFree(port: Int): Boolean =
    runCatching { ServerSocket(port, 1, InetAddress.getByName(HOST)).close() }.isSuccess

  private fun shutdown() {
    reclaim?.cancel()
    reclaim = null
    val current = server ?: return
    server = null
    runCatching { current.stop(gracePeriodMillis = 200, timeoutMillis = 2_000) }
      .onFailure { AppLog.warn(TAG, "MCP server stop failed", it) }
    endpointFile(dataDir).delete()
  }

  private fun writeEndpoint(url: String) {
    val file = endpointFile(dataDir)
    runCatching {
      file.parentFile?.mkdirs()
      file.writeText(pretty.encodeToString(McpEndpoint.serializer(), McpEndpoint(url, ProcessHandle.current().pid())))
    }.onFailure { AppLog.warn(TAG, "cannot write ${file.absolutePath}", it) }
  }

  companion object {
    const val DEFAULT_PORT = 39871
    const val HOST = "127.0.0.1"
    const val PATH = "/mcp"
    const val BRIDGE_FLAG = "--mcp"
    const val SERVER_NAME = "berill"
    const val TOKEN_SECRET = "mcp.token"
    internal const val ENABLED = "mcp.enabled"
    internal const val WRITABLE = "mcp.writable"
    internal const val PORT = "mcp.port"
    private const val TAG = "mcp"

    private val pretty = Json { prettyPrint = true }

    fun endpointFile(dataDir: File) = File(dataDir, "mcp-endpoint.json")

    fun readEndpoint(dataDir: File): McpEndpoint? =
      runCatching { Json.decodeFromString(McpEndpoint.serializer(), endpointFile(dataDir).readText()) }.getOrNull()

    fun urlFor(port: Int) = "http://$HOST:$port$PATH"

    private fun newToken(): String {
      val bytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
      return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
  }
}
