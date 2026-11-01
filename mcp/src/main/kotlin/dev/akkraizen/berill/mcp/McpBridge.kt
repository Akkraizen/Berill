package dev.akkraizen.berill.mcp

import java.io.BufferedReader
import java.io.Writer
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

data class BridgeTarget(val url: String, val token: String)

class McpBridge(
  private val target: () -> BridgeTarget?,
  private val input: BufferedReader,
  private val output: Writer,
  private val log: (String) -> Unit = {},
  private val wakeUp: (() -> Unit)? = null,
  private val wakeUpTimeout: Duration = Duration.ofSeconds(30)
) {
  private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()
  private var current: BridgeTarget? = null
  @Volatile private var session: String? = null
  @Volatile private var protocolVersion: String? = null
  @Volatile private var eventSession: String? = null
  @Volatile private var eventInput: java.io.InputStream? = null
  private var initialize: String? = null
  private var initialized: String? = null
  private var wokeUp = false

  fun run() {
    try {
      while (true) {
        val line = input.readLine() ?: break
        if (line.isBlank()) continue
        handle(line)
      }
    } finally { close() }
  }

  private fun handle(line: String) {
    val message = runCatching { Json.parseToJsonElement(line) }.getOrNull() as? JsonObject
    val id = message?.get("id")
    val method = (message?.get("method") as? JsonPrimitive)?.takeIf { it.isString }?.content
    if (method == "initialize") {
      initialize = line
      session = null
    }
    if (method == "notifications/initialized") initialized = line
    val endpoint = resolve()
    if (endpoint == null) {
      if (id != null) reply(error(id, OFF_MESSAGE))
      return
    }
    if (method != "initialize" && session == null && initialize != null) {
      val ready = runCatching {
        check(send(endpoint, initialize!!, forward = false) < 400)
        initialized?.let { check(send(endpoint, it, forward = false) < 400) }
      }.isSuccess
      if (!ready) { if (id != null) reply(error(id, OFF_MESSAGE)); return }
    }
    val result = runCatching { send(endpoint, line, forward = true) }
    val status = result.getOrNull()
    val retry = status == 404 || status == 401
    if (retry && method != "initialize" && initialize != null) {
      log("Berill session was lost, reconnecting")
      stopEvents()
      current = null
      session = null
      val fresh = resolve()
      val resent = fresh != null && runCatching {
        send(fresh, initialize!!, forward = false)
        initialized?.let { send(fresh, it, forward = false) }
        send(fresh, line, forward = true)
      }.getOrNull()?.let { it < 400 } == true
      if (!resent && id != null) reply(error(id, OFF_MESSAGE))
      return
    }
    if (result.isFailure) {
      stopEvents(); current = null; session = null
      if (id != null) reply(error(id, "Connection lost. The outcome may be unknown; read the record before retrying a change."))
    }
    else if (status != null && status >= 400 && id != null) reply(error(id, "Berill answered with HTTP $status"))
  }

  private fun resolve(): BridgeTarget? {
    current?.let { return it }
    val found = target() ?: wake()
    current = found
    return found
  }

  private fun wake(): BridgeTarget? {
    val start = wakeUp ?: return null
    if (wokeUp) return null
    wokeUp = true
    log("Berill is not running, starting it in the background")
    runCatching(start).onFailure { log("Cannot start Berill: ${it.message}") }
    val deadline = System.nanoTime() + wakeUpTimeout.toNanos()
    while (System.nanoTime() < deadline) {
      target()?.let { return it }
      Thread.sleep(300)
    }
    return null
  }

  private fun send(endpoint: BridgeTarget, body: String, forward: Boolean): Int {
    val builder = HttpRequest.newBuilder(URI.create(endpoint.url))
      .timeout(Duration.ofMinutes(5))
      .header("Content-Type", "application/json")
      .header("Accept", "application/json, text/event-stream")
      .header("Authorization", "Bearer ${endpoint.token}")
      .POST(HttpRequest.BodyPublishers.ofString(body))
    session?.let { builder.header(SESSION_HEADER, it) }
    protocolVersion?.let { builder.header("MCP-Protocol-Version", it) }
    val response = http.send(builder.build(), HttpResponse.BodyHandlers.ofLines())
    response.headers().firstValue(SESSION_HEADER).ifPresent { session = it }
    val type = response.headers().firstValue("Content-Type").orElse("")
    val lines = response.body()
    if (response.statusCode() >= 400) {
      lines.close()
      return response.statusCode()
    }
    if (type.startsWith("text/event-stream")) {
      val data = StringBuilder()
      lines.use { stream ->
        stream.forEach { raw ->
          when {
            raw.startsWith("data:") -> {
              if (data.isNotEmpty()) data.append('\n')
              data.append(raw.removePrefix("data:").removePrefix(" "))
            }
            raw.isEmpty() -> {
              if (data.isNotEmpty()) accept(data.toString(), forward)
              data.clear()
            }
          }
        }
      }
      if (data.isNotEmpty()) accept(data.toString(), forward)
    } else {
      val text = lines.use { it.toList().joinToString("\n") }.trim()
      if (text.isNotEmpty()) accept(text, forward)
    }
    if (body == initialized && response.statusCode() < 400) startEvents(endpoint)
    return response.statusCode()
  }

  private fun accept(text: String, forward: Boolean) {
    val response = runCatching { Json.parseToJsonElement(text) as? JsonObject }.getOrNull()
    val result = response?.get("result") as? JsonObject
    (result?.get("protocolVersion") as? JsonPrimitive)?.content?.let { protocolVersion = it }
    if (forward) reply(text)
  }

  private fun stopEvents() {
    eventSession = null
    runCatching { eventInput?.close() }
    eventInput = null
  }

  private fun startEvents(endpoint: BridgeTarget) {
    val id = session ?: return
    if (eventSession == id) return
    stopEvents()
    eventSession = id
    Thread {
      try {
        val builder = HttpRequest.newBuilder(URI.create(endpoint.url)).header("Accept", "text/event-stream")
          .header("Authorization", "Bearer ${endpoint.token}").header(SESSION_HEADER, id).GET()
        protocolVersion?.let { builder.header("MCP-Protocol-Version", it) }
        val response = http.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream())
        if (response.statusCode() != 200 || eventSession != id) { response.body().close(); return@Thread }
        eventInput = response.body()
        response.body().bufferedReader(Charsets.UTF_8).use { reader ->
          val data = StringBuilder()
          reader.forEachLine { line ->
            if (line.startsWith("data:")) { if (data.isNotEmpty()) data.append('\n'); data.append(line.removePrefix("data:").removePrefix(" ")) }
            else if (line.isEmpty() && data.isNotEmpty()) { accept(data.toString(), true); data.clear() }
          }
        }
      } catch (e: Exception) { if (eventSession == id) log("Notification stream ended") }
      finally { if (eventSession == id) { eventSession = null; eventInput = null } }
    }.apply { isDaemon = true; name = "berill-mcp-events"; start() }
  }

  private fun close() {
    stopEvents()
    val endpoint = current ?: return
    val id = session ?: return
    runCatching {
      http.send(
        HttpRequest.newBuilder(URI.create(endpoint.url))
          .timeout(Duration.ofSeconds(3))
          .header("Authorization", "Bearer ${endpoint.token}")
          .header(SESSION_HEADER, id)
          .apply { protocolVersion?.let { header("MCP-Protocol-Version", it) } }
          .DELETE()
          .build(),
        HttpResponse.BodyHandlers.discarding()
      )
    }
  }

  private fun reply(text: String) {
    synchronized(output) {
      output.write(text.replace("\n", " ").trim())
      output.write("\n")
      output.flush()
    }
  }

  private fun error(id: JsonElement, message: String): String =
    buildJsonObject {
      put("jsonrpc", "2.0")
      put("id", if (id is JsonNull) JsonNull else id.jsonPrimitive)
      put(
        "error",
        buildJsonObject {
          put("code", -32603)
          put("message", message)
        }
      )
    }.toString()

  companion object {
    const val SESSION_HEADER = "Mcp-Session-Id"
    const val OFF_MESSAGE =
      "Berill is not reachable. Start Berill and turn on Settings → AI assistants (MCP), then try again."
  }
}
