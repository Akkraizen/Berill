package dev.akkraizen.berill.mcp

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import dev.akkraizen.berill.core.BerillCore
import dev.akkraizen.berill.core.db.BerillDatabase
import dev.akkraizen.berill.core.secure.MemoryVault
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.StdioClientTransport
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class EmbeddedMcpTest {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  private val dir: File = Files.createTempDirectory("berill-embedded-mcp").toFile()
  private val vault = MemoryVault()
  private val core: BerillCore = run {
    val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
    BerillDatabase.Schema.create(driver)
    BerillCore(driver, emptyList(), scope, vault = vault)
  }
  private val mcp = EmbeddedMcp(core, dir, preferredPort = 0)

  @AfterTest
  fun tearDown() {
    mcp.stop()
    scope.cancel()
    dir.deleteRecursively()
  }

  private fun post(url: String, token: String?, origin: String? = null): Int {
    val builder = HttpRequest.newBuilder(URI.create(url))
      .header("Content-Type", "application/json")
      .header("Accept", "application/json, text/event-stream")
      .POST(HttpRequest.BodyPublishers.ofString("""{"jsonrpc":"2.0","id":1,"method":"ping"}"""))
    token?.let { builder.header("Authorization", "Bearer $it") }
    origin?.let { builder.header("Origin", it) }
    return HttpClient.newHttpClient().send(builder.build(), HttpResponse.BodyHandlers.discarding()).statusCode()
  }

  private fun withBridge(block: suspend (Client) -> Unit) = runBlocking {
    val toBridge = PipedOutputStream()
    val bridgeIn = PipedInputStream(toBridge, 1 shl 16)
    val toClient = PipedOutputStream()
    val clientIn = PipedInputStream(toClient, 1 shl 16)
    val bridge = Thread {
      McpBridge(
        target = { bridgeTarget(dir, vault) },
        input = BufferedReader(InputStreamReader(bridgeIn, Charsets.UTF_8)),
        output = OutputStreamWriter(toClient, Charsets.UTF_8)
      ).run()
    }.apply {
      isDaemon = true
      start()
    }
    val client = Client(Implementation("test", "1.0"))
    withTimeout(30_000) {
      client.connect(StdioClientTransport(clientIn.asSource().buffered(), toBridge.asSink().buffered()))
      block(client)
    }
    client.close()
    toBridge.close()
    bridge.join(5_000)
  }

  @Test
  fun serverIsOffUntilEnabled() {
    mcp.start()
    assertFalse(mcp.status.value.running)
    assertNull(EmbeddedMcp.readEndpoint(dir))
  }

  @Test
  fun enabledServerRequiresTheToken() {
    mcp.setEnabled(true)
    val status = mcp.status.value
    assertTrue(status.running, status.toString())
    val url = assertNotNull(status.url)
    assertTrue(url.startsWith("http://127.0.0.1:"))
    assertEquals(url, EmbeddedMcp.readEndpoint(dir)?.url)
    assertEquals(401, post(url, null))
    assertEquals(401, post(url, "wrong"))
    assertTrue(post(url, mcp.token()) != 401)
    assertEquals(403, post(url, mcp.token(), origin = "https://evil.example"))
    mcp.setEnabled(false)
    assertFalse(mcp.status.value.running)
    assertNull(EmbeddedMcp.readEndpoint(dir))
  }

  @Test
  fun bridgeReachesTheAppAndRespectsReadOnlyMode() {
    core.notes.save("n1", "Plan", "Ship the embedded MCP server")
    mcp.setEnabled(true)
    withBridge { client ->
      val names = client.listTools().tools.map { it.name }.toSet()
      assertTrue("search" in names, names.toString())
      assertTrue("create_note" !in names, names.toString())
      val text = (client.callTool("search", mapOf("query" to "embedded")).content.single() as TextContent).text
      assertTrue("n1" in text, text)
    }
    mcp.setWritable(true)
    withBridge { client ->
      assertTrue("create_note" in client.listTools().tools.map { it.name })
      val created = (client.callTool("create_note", mapOf("title" to "From Claude", "body" to "Hello")).content.single() as TextContent).text
      val id = Json.parseToJsonElement(created).jsonObject["id"]!!.jsonPrimitive.content
      assertEquals("Hello", core.notes.get(id)?.body)
    }
  }

  @Test
  fun bridgeReconnectsAfterTheServerRestarts() {
    mcp.setEnabled(true)
    withBridge { client ->
      assertTrue(client.listTools().tools.isNotEmpty())
      mcp.regenerateToken()
      val result = client.callTool("list_notes", emptyMap())
      assertTrue(result.isError != true, (result.content.firstOrNull() as? TextContent)?.text)
    }
  }

  @Test
  fun bridgeExplainsWhenBerillIsOff() {
    val toBridge = PipedOutputStream()
    val bridgeIn = PipedInputStream(toBridge, 1 shl 16)
    val out = java.io.StringWriter()
    val bridge = Thread {
      McpBridge(target = { bridgeTarget(dir, vault) }, input = BufferedReader(InputStreamReader(bridgeIn)), output = out).run()
    }.apply { start() }
    toBridge.write("""{"jsonrpc":"2.0","id":7,"method":"initialize","params":{}}""".toByteArray())
    toBridge.write("\n".toByteArray())
    toBridge.close()
    bridge.join(10_000)
    val reply = Json.parseToJsonElement(out.toString().trim()) as JsonObject
    assertEquals("7", reply["id"]!!.jsonPrimitive.content)
    assertTrue(McpBridge.OFF_MESSAGE in reply["error"].toString())
  }

  @Test
  fun clientConfigsPointAtTheBridgeOrTheUrl() {
    mcp.setEnabled(true)
    assertTrue(mcp.desktopClientConfig().contains("Bearer ${mcp.token()}"))
    assertTrue(mcp.claudeCodeCommand().startsWith("claude mcp add --transport http berill http://127.0.0.1:"))
    val bridged = EmbeddedMcp(core, dir, bridgeCommand = "C:\\Program Files\\Berill\\Berill.exe", preferredPort = 0)
    assertTrue(bridged.desktopClientConfig().contains("\"--mcp\""))
    assertFalse(bridged.desktopClientConfig().contains("Bearer"))
    assertEquals("claude mcp add berill -- \"C:\\Program Files\\Berill\\Berill.exe\" --mcp", bridged.claudeCodeCommand())
  }
}
