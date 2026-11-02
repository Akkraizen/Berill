package dev.akkraizen.berill.mcp

import dev.akkraizen.berill.core.platform.McpClientConfig
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

sealed interface McpLaunch {
  data class Stdio(val command: String, val args: List<String> = emptyList()) : McpLaunch

  data class Http(val url: String, val token: String) : McpLaunch
}

enum class McpClient(val id: String, val title: String, val location: String) {
  CLAUDE_DESKTOP("claude-desktop", "Claude Desktop", "claude_desktop_config.json"),
  CLAUDE_CODE("claude-code", "Claude Code", "terminal"),
  CODEX("codex", "OpenAI Codex", "~/.codex/config.toml"),
  CURSOR("cursor", "Cursor", "~/.cursor/mcp.json"),
  VS_CODE("vscode", "VS Code (GitHub Copilot)", ".vscode/mcp.json"),
  WINDSURF("windsurf", "Windsurf", "~/.codeium/windsurf/mcp_config.json"),
  GEMINI("gemini", "Gemini CLI", "~/.gemini/settings.json"),
  ZED("zed", "Zed", "~/.config/zed/settings.json"),
  OPENCODE("opencode", "opencode", "~/.config/opencode/opencode.json"),
  CLINE("cline", "Cline / Roo Code", "cline_mcp_settings.json"),
  JETBRAINS("jetbrains", "JetBrains AI Assistant / Junie", "Settings | Tools | AI Assistant | Model Context Protocol"),
  GENERIC("generic", "Other MCP clients", "mcp.json");

  companion object {
    fun byId(id: String): McpClient? = entries.firstOrNull { it.id.equals(id, ignoreCase = true) }
  }
}

object McpClients {
  const val AUTH_ENV = "BERILL_MCP_AUTH"

  private val pretty = Json { prettyPrint = true }

  fun all(name: String, launch: McpLaunch): List<McpClientConfig> =
    McpClient.entries.map { config(it, name, launch) }

  fun config(client: McpClient, name: String, launch: McpLaunch): McpClientConfig =
    McpClientConfig(client.id, client.title, client.location, render(client, name, launch))

  fun render(client: McpClient, name: String, launch: McpLaunch): String =
    when (client) {
      McpClient.CLAUDE_CODE -> claudeCode(name, launch)
      McpClient.CODEX -> codex(name, launch)
      McpClient.CLAUDE_DESKTOP -> json("mcpServers", name, if (launch is McpLaunch.Stdio) stdio(launch) else remoteProxy(launch as McpLaunch.Http))
      McpClient.CURSOR, McpClient.JETBRAINS -> json("mcpServers", name, when (launch) {
        is McpLaunch.Stdio -> stdio(launch)
        is McpLaunch.Http -> obj("url" to text(launch.url), "headers" to headers(launch))
      })
      McpClient.VS_CODE -> json("servers", name, when (launch) {
        is McpLaunch.Stdio -> obj("type" to text("stdio")) + stdio(launch)
        is McpLaunch.Http -> obj("type" to text("http"), "url" to text(launch.url), "headers" to headers(launch))
      })
      McpClient.WINDSURF -> json("mcpServers", name, when (launch) {
        is McpLaunch.Stdio -> stdio(launch)
        is McpLaunch.Http -> obj("serverUrl" to text(launch.url), "headers" to headers(launch))
      })
      McpClient.GEMINI -> json("mcpServers", name, when (launch) {
        is McpLaunch.Stdio -> stdio(launch)
        is McpLaunch.Http -> obj("httpUrl" to text(launch.url), "headers" to headers(launch))
      })
      McpClient.ZED -> json("context_servers", name, when (launch) {
        is McpLaunch.Stdio -> obj("source" to text("custom")) + stdio(launch) + obj("env" to obj())
        is McpLaunch.Http -> obj("url" to text(launch.url), "headers" to headers(launch))
      })
      McpClient.OPENCODE -> pretty.encodeToString(JsonObject.serializer(), obj(
        "\$schema" to text("https://opencode.ai/config.json"),
        "mcp" to obj(name to when (launch) {
          is McpLaunch.Stdio -> obj("type" to text("local"), "command" to list(listOf(launch.command) + launch.args), "enabled" to JsonPrimitive(true))
          is McpLaunch.Http -> obj("type" to text("remote"), "url" to text(launch.url), "headers" to headers(launch), "enabled" to JsonPrimitive(true))
        })
      ))
      McpClient.CLINE -> json("mcpServers", name, when (launch) {
        is McpLaunch.Stdio -> stdio(launch) + obj("disabled" to JsonPrimitive(false))
        is McpLaunch.Http -> obj("type" to text("streamableHttp"), "url" to text(launch.url), "headers" to headers(launch), "disabled" to JsonPrimitive(false))
      })
      McpClient.GENERIC -> json("mcpServers", name, when (launch) {
        is McpLaunch.Stdio -> stdio(launch)
        is McpLaunch.Http -> obj("type" to text("http"), "url" to text(launch.url), "headers" to headers(launch))
      })
    }

  private fun claudeCode(name: String, launch: McpLaunch): String =
    when (launch) {
      is McpLaunch.Stdio -> "claude mcp add --scope user $name -- " + (listOf(launch.command) + launch.args).joinToString(" ", transform = ::shellQuote)
      is McpLaunch.Http -> "claude mcp add --scope user --transport http $name ${launch.url} --header \"Authorization: Bearer ${launch.token}\""
    }

  private fun codex(name: String, launch: McpLaunch): String =
    buildString {
      appendLine("[mcp_servers.${tomlKey(name)}]")
      when (launch) {
        is McpLaunch.Stdio -> {
          appendLine("command = ${tomlString(launch.command)}")
          append("args = [${launch.args.joinToString(", ", transform = ::tomlString)}]")
        }
        is McpLaunch.Http -> {
          appendLine("url = ${tomlString(launch.url)}")
          append("http_headers = { \"Authorization\" = ${tomlString("Bearer ${launch.token}")} }")
        }
      }
    }

  private fun remoteProxy(launch: McpLaunch.Http): JsonObject =
    obj(
      "command" to text("npx"),
      "args" to list(listOf("-y", "mcp-remote", launch.url, "--allow-http", "--header", "Authorization:\${$AUTH_ENV}")),
      "env" to obj(AUTH_ENV to text("Bearer ${launch.token}"))
    )

  private fun stdio(launch: McpLaunch.Stdio): JsonObject =
    obj("command" to text(launch.command), "args" to list(launch.args))

  private fun headers(launch: McpLaunch.Http): JsonObject = obj("Authorization" to text("Bearer ${launch.token}"))

  private fun json(root: String, name: String, server: JsonObject): String =
    pretty.encodeToString(JsonObject.serializer(), obj(root to obj(name to server)))

  private fun obj(vararg entries: Pair<String, JsonElement>): JsonObject = JsonObject(linkedMapOf(*entries))

  private operator fun JsonObject.plus(other: JsonObject): JsonObject = JsonObject(LinkedHashMap<String, JsonElement>(this).apply { putAll(other) })

  private fun text(value: String) = JsonPrimitive(value)

  private fun list(values: List<String>) = JsonArray(values.map(::JsonPrimitive))

  private fun tomlString(value: String): String = JsonPrimitive(value).toString()

  private fun tomlKey(value: String): String = if (value.matches(Regex("[A-Za-z0-9_-]+"))) value else tomlString(value)

  fun shellQuote(value: String): String =
    if (value.isNotEmpty() && value.none { it.isWhitespace() || it in "\"'`$&|;<>()*?!#~" }) value else "\"" + value.replace("\"", "\\\"") + "\""
}
