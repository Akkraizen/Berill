package dev.akkraizen.berill.mcp

import dev.akkraizen.berill.core.secure.MemoryVault
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.StdioClientTransport
import io.modelcontextprotocol.kotlin.sdk.server.StdioServerTransport
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered

class McpProtocolTest {
  private fun connect(readOnly: Boolean, block: suspend (Client) -> Unit) = runBlocking {
    val core = openCore(Files.createTempDirectory("berill-mcp").toFile(), MemoryVault())
    BerillTools(core).createNote("Plan", "Ship the MCP server")
    val toServer = PipedOutputStream()
    val serverIn = PipedInputStream(toServer, 1 shl 16)
    val toClient = PipedOutputStream()
    val clientIn = PipedInputStream(toClient, 1 shl 16)
    val server = createBerillServer(BerillTools(core), readOnly)
    launch {
      server.createSession(StdioServerTransport(serverIn.asSource().buffered(), toClient.asSink().buffered()))
    }
    val client = Client(Implementation("test", "1.0"))
    withTimeout(30_000) {
      client.connect(StdioClientTransport(clientIn.asSource().buffered(), toServer.asSink().buffered()))
      block(client)
    }
    client.close()
    server.close()
  }

  @Test
  fun readOnlyServerHidesWriteTools() = connect(readOnly = true) { client ->
    val names = client.listTools().tools.map { it.name }.toSet()

    assertTrue("search" in names && "read_note" in names && "sync_status" in names, names.toString())
    assertTrue(names.none { it in setOf("create_note", "append_to_note", "update_note", "delete_note") }, names.toString())
  }

  @Test
  fun toolsAnswerOverTheProtocol() = connect(readOnly = false) { client ->
    assertTrue("create_note" in client.listTools().tools.map { it.name })

    val result = client.callTool("search", mapOf("query" to "mcp server"))
    val text = (result.content.single() as TextContent).text
    assertTrue("\"collection\":\"notes\"" in text, text)

    val error = client.callTool("read_records", mapOf("collection" to "settings"))
    assertEquals(true, error.isError)
  }
}
