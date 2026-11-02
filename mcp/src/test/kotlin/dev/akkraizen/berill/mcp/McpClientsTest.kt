package dev.akkraizen.berill.mcp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class McpClientsTest {
  private val stdio = McpLaunch.Stdio("C:\\Program Files\\Berill\\Berill.exe", listOf("--mcp"))
  private val http = McpLaunch.Http("http://127.0.0.1:39871/mcp", "secret")

  private fun json(client: McpClient, launch: McpLaunch): JsonObject =
    Json.parseToJsonElement(McpClients.render(client, "berill", launch)).jsonObject

  private fun JsonObject.server(root: String) = getValue(root).jsonObject.getValue("berill").jsonObject

  @Test
  fun everyJsonClientGetsValidConfigForBothTransports() {
    McpClient.entries.filter { it != McpClient.CLAUDE_CODE && it != McpClient.CODEX }.forEach { client ->
      listOf(stdio, http).forEach { launch ->
        val text = McpClients.render(client, "berill", launch)
        assertTrue(Json.parseToJsonElement(text) is JsonObject, "${client.id}: $text")
        if (launch is McpLaunch.Http) assertTrue("Bearer secret" in text, "${client.id}: $text")
        else assertTrue("Berill.exe" in text && "--mcp" in text, "${client.id}: $text")
      }
    }
  }

  @Test
  fun clientSpecificKeysFollowEachClientsSchema() {
    assertEquals("stdio", json(McpClient.VS_CODE, stdio).server("servers")["type"]!!.jsonPrimitive.content)
    assertEquals("http", json(McpClient.VS_CODE, http).server("servers")["type"]!!.jsonPrimitive.content)
    assertEquals(http.url, json(McpClient.CURSOR, http).server("mcpServers")["url"]!!.jsonPrimitive.content)
    assertEquals(http.url, json(McpClient.WINDSURF, http).server("mcpServers")["serverUrl"]!!.jsonPrimitive.content)
    assertEquals(http.url, json(McpClient.GEMINI, http).server("mcpServers")["httpUrl"]!!.jsonPrimitive.content)
    assertEquals("streamableHttp", json(McpClient.CLINE, http).server("mcpServers")["type"]!!.jsonPrimitive.content)
    assertEquals("custom", json(McpClient.ZED, stdio).server("context_servers")["source"]!!.jsonPrimitive.content)
    val opencode = json(McpClient.OPENCODE, stdio).server("mcp")
    assertEquals("local", opencode["type"]!!.jsonPrimitive.content)
    assertEquals(listOf(stdio.command, "--mcp"), opencode["command"]!!.jsonArray.map { it.jsonPrimitive.content })
    val desktop = json(McpClient.CLAUDE_DESKTOP, http).server("mcpServers")
    assertEquals("npx", desktop["command"]!!.jsonPrimitive.content)
    assertTrue((desktop["args"] as JsonArray).any { it.jsonPrimitive.content == http.url })
    assertEquals("Bearer secret", desktop["env"]!!.jsonObject[McpClients.AUTH_ENV]!!.jsonPrimitive.content)
  }

  @Test
  fun commandLineAndTomlClientsAreQuoted() {
    assertEquals("claude mcp add --scope user berill -- \"C:\\Program Files\\Berill\\Berill.exe\" --mcp", McpClients.render(McpClient.CLAUDE_CODE, "berill", stdio))
    assertEquals(
      "[mcp_servers.berill]\ncommand = \"C:\\\\Program Files\\\\Berill\\\\Berill.exe\"\nargs = [\"--mcp\"]",
      McpClients.render(McpClient.CODEX, "berill", stdio)
    )
    assertEquals(
      "[mcp_servers.berill]\nurl = \"http://127.0.0.1:39871/mcp\"\nhttp_headers = { \"Authorization\" = \"Bearer secret\" }",
      McpClients.render(McpClient.CODEX, "berill", http)
    )
  }
}
