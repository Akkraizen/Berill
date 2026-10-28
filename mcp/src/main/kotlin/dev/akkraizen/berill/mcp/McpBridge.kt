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
  private var session: String? = null
  private var initialize: String? = null
  private var initialized: String? = null
  private var wokeUp = false

  fun run() {
    while (true) {
      val line = input.readLine() ?: break
      if (line.isBlank()) continue
      handle(line)
    }
    close()
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
    val result = runCatching { send(endpoint, line, forward = true) }
    val status = result.getOrNull()
    val retry = result.isFailure || status == 404 || status == 401
    if (retry && method != "initialize" && initialize != null) {
      log("Berill session was lost, reconnecting")
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
    if (result.isFailure && id != null) reply(error(id, result.exceptionOrNull()?.message ?: OFF_MESSAGE))
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
              if (data.isNotEmpty() && forward) reply(data.toString())
              data.clear()
            }
          }
        }
      }
      if (data.isNotEmpty() && forward) reply(data.toString())
    } else {
      val text = lines.use { it.toList().joinToString("\n") }.trim()
      if (text.isNotEmpty() && forward) reply(text)
    }
    return response.statusCode()
  }

  private fun close() {
    val endpoint = current ?: return
    val id = session ?: return
    runCatching {
      http.send(
        HttpRequest.newBuilder(URI.create(endpoint.url))
          .timeout(Duration.ofSeconds(3))
          .header("Authorization", "Bearer ${endpoint.token}")
          .header(SESSION_HEADER, id)
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
