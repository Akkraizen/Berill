package dev.akkraizen.berill.mcp

import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.mcpStreamableHttp
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.io.StringWriter
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.runBlocking

class BridgeSessionExpiryTest {
  private fun serverInstance(): Server =
    Server(Implementation("t", "1"), ServerOptions(ServerCapabilities(tools = ServerCapabilities.Tools(listChanged = true)))).apply {
      addTool("echo", "echo", ToolSchema()) { CallToolResult(listOf(TextContent("pong"))) }
    }

  @Test fun bridgeRecoversAfterServerExpiresIdleSession() {
    val protocol = serverInstance()
    val server = embeddedServer(CIO, host = "127.0.0.1", port = 0) {
      mcpStreamableHttp(path = "/mcp", sessionIdleTimeout = 1.seconds) { protocol }
    }.start(wait = false)
    val port = runBlocking { server.engine.resolvedConnectors().first().port }
    val pipe = PipedOutputStream()
    val input = BufferedReader(InputStreamReader(PipedInputStream(pipe, 1 shl 16)))
    val output = StringWriter()
    val logs = mutableListOf<String>()
    val bridge = Thread {
      McpBridge({ BridgeTarget("http://127.0.0.1:$port/mcp", "t") }, input, output, log = { synchronized(logs) { logs += it } }).run()
    }.apply { start() }
    fun send(line: String) { pipe.write((line + "\n").toByteArray()); pipe.flush() }
    fun waitFor(text: String) {
      val deadline = System.currentTimeMillis() + 15_000
      while (text !in synchronized(output) { output.toString() } && System.currentTimeMillis() < deadline) Thread.sleep(50)
    }
    try {
      send("""{"jsonrpc":"2.0","id":0,"method":"initialize","params":{"protocolVersion":"2025-11-25","capabilities":{},"clientInfo":{"name":"c","version":"1"}}}""")
      waitFor("\"id\":0")
      send("""{"jsonrpc":"2.0","method":"notifications/initialized"}""")
      send("""{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"echo","arguments":{}}}""")
      waitFor("\"id\":1")
      Thread.sleep(8_000)
      send("""{"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"echo","arguments":{}}}""")
      waitFor("\"id\":2")
      val text = output.toString()
      val second = text.lines().firstOrNull { "\"id\":2" in it }.orEmpty()
      assertTrue("pong" in second, "output: $text\nlogs: $logs")
    } finally {
      pipe.close()
      bridge.join(10_000)
      server.stop(100, 1_000)
    }
  }
}
