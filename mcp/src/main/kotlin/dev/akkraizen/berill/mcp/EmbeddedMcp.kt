package dev.akkraizen.berill.mcp

import dev.akkraizen.berill.core.BerillCore
import dev.akkraizen.berill.core.log.AppLog
import dev.akkraizen.berill.core.platform.McpAccess
import dev.akkraizen.berill.core.platform.McpStatus
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.respondText
import io.modelcontextprotocol.kotlin.sdk.server.mcpStreamableHttp
import java.io.File
import java.net.BindException
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

@Serializable
data class McpEndpoint(val url: String, val pid: Long)

class EmbeddedMcp(
  private val core: BerillCore,
  private val dataDir: File,
  override val bridgeCommand: String? = null,
  private val preferredPort: Int = DEFAULT_PORT
) : McpAccess {
  private val store = core.store
  private val vault = core.vault
  private val lock = Any()
  private var server: EmbeddedServer<*, *>? = null

  private val _status = MutableStateFlow(McpStatus(enabled = flag(ENABLED), writable = flag(WRITABLE)))
  override val status: StateFlow<McpStatus> = _status

  private fun flag(key: String) = store.meta(key) == "1"

  override fun start() {
    if (flag(ENABLED)) launch()
  }

  override fun stop() =
    synchronized(lock) {
      shutdown()
      _status.update { it.copy(running = false, url = null) }
    }

  override fun setEnabled(enabled: Boolean) {
    store.setMeta(ENABLED, if (enabled) "1" else "0")
    _status.update { it.copy(enabled = enabled, error = null) }
    if (enabled) launch() else stop()
  }

  override fun setWritable(writable: Boolean) {
    store.setMeta(WRITABLE, if (writable) "1" else "0")
    _status.update { it.copy(writable = writable) }
    if (_status.value.enabled) launch()
  }

  override fun token(): String =
    synchronized(lock) {
      vault.read(TOKEN_SECRET)?.takeIf { it.isNotBlank() } ?: newToken().also { vault.write(TOKEN_SECRET, it) }
    }

  override fun regenerateToken() {
    synchronized(lock) { vault.write(TOKEN_SECRET, newToken()) }
    if (_status.value.enabled) launch()
  }

  override fun desktopClientConfig(): String {
    val command = bridgeCommand
    val server = if (command != null)
      JsonObject(mapOf("command" to JsonPrimitive(command), "args" to JsonArray(listOf(JsonPrimitive(BRIDGE_FLAG)))))
    else
      JsonObject(
        mapOf(
          "type" to JsonPrimitive("http"),
          "url" to JsonPrimitive(url()),
          "headers" to JsonObject(mapOf("Authorization" to JsonPrimitive("Bearer ${token()}")))
        )
      )
    return pretty.encodeToString(JsonObject.serializer(), JsonObject(mapOf("mcpServers" to JsonObject(mapOf(SERVER_NAME to server)))))
  }

  override fun claudeCodeCommand(): String {
    val command = bridgeCommand
    return if (command != null)
      "claude mcp add $SERVER_NAME -- ${quote(command)} $BRIDGE_FLAG"
    else
      "claude mcp add --transport http $SERVER_NAME ${url()} --header \"Authorization: Bearer ${token()}\""
  }

  private fun url(): String = _status.value.url ?: urlFor(store.meta(PORT)?.toIntOrNull() ?: preferredPort)

  private fun launch() =
    synchronized(lock) {
      shutdown()
      val token = token()
      val writable = flag(WRITABLE)
      val ports = listOfNotNull(store.meta(PORT)?.toIntOrNull(), preferredPort, 0).distinct()
      var lastError: Throwable? = null
      for (port in ports) {
        val started = runCatching { createServer(port, token, writable).also { it.start(wait = false) } }
          .onFailure { lastError = it }
          .getOrNull() ?: continue
        val actual = runBlocking { started.engine.resolvedConnectors().first().port }
        server = started
        store.setMeta(PORT, actual.toString())
        val url = urlFor(actual)
        writeEndpoint(url)
        _status.update { it.copy(running = true, url = url, error = null) }
        AppLog.info(TAG, "MCP server listening on $url, writable=$writable")
        return@synchronized
      }
      AppLog.error(TAG, "MCP server failed to start", lastError)
      _status.update { it.copy(running = false, url = null, error = lastError?.let(::describe) ?: "unknown error") }
    }

  private fun describe(e: Throwable): String = if (e is BindException) "port is busy" else e.message ?: e::class.simpleName.orEmpty()

  private fun createServer(port: Int, token: String, writable: Boolean): EmbeddedServer<*, *> {
    val tools = BerillTools(core)
    val expected = "Bearer $token".toByteArray()
    return embeddedServer(CIO, host = HOST, port = port) {
      intercept(ApplicationCallPipeline.Plugins) {
        val given = call.request.headers["Authorization"]?.toByteArray() ?: ByteArray(0)
        if (!MessageDigest.isEqual(given, expected)) {
          call.respondText("Unauthorized", status = HttpStatusCode.Unauthorized)
          finish()
        }
      }
      mcpStreamableHttp(path = PATH, enableDnsRebindingProtection = true) { createBerillServer(tools, readOnly = !writable) }
    }
  }

  private fun shutdown() {
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

    private fun quote(path: String) = if (path.any { it.isWhitespace() }) "\"$path\"" else path
  }
}
