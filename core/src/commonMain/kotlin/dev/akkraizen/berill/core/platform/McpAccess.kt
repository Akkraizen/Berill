package dev.akkraizen.berill.core.platform

import kotlinx.coroutines.flow.StateFlow

data class McpStatus(
  val enabled: Boolean = false,
  val writable: Boolean = false,
  val running: Boolean = false,
  val url: String? = null,
  val error: String? = null
)

data class McpClientConfig(val id: String, val title: String, val location: String, val text: String)

interface McpAccess {
  val status: StateFlow<McpStatus>

  fun start()

  fun stop()

  fun setEnabled(enabled: Boolean)

  fun setWritable(writable: Boolean)

  fun token(): String

  fun regenerateToken()

  val bridgeCommand: String?

  fun desktopClientConfig(): String

  fun httpClientConfig(): String = desktopClientConfig()

  fun claudeCodeCommand(): String

  fun clientConfigs(): List<McpClientConfig> = emptyList()
}
