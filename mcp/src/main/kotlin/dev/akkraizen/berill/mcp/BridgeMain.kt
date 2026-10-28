package dev.akkraizen.berill.mcp

import dev.akkraizen.berill.core.secure.SecretVault
import java.io.BufferedReader
import java.io.FileDescriptor
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.File
import kotlin.text.Charsets.UTF_8

fun bridgeTarget(dataDir: File, vault: SecretVault): BridgeTarget? {
  val endpoint = EmbeddedMcp.readEndpoint(dataDir) ?: return null
  if (!ProcessHandle.of(endpoint.pid).map { it.isAlive }.orElse(false)) return null
  val token = runCatching { vault.read(EmbeddedMcp.TOKEN_SECRET) }.getOrNull()?.takeIf { it.isNotBlank() } ?: return null
  return BridgeTarget(endpoint.url, token)
}

fun runMcpBridge(dataDir: File, vault: SecretVault, startApp: (() -> Unit)?) {
  val input = BufferedReader(InputStreamReader(System.`in`, UTF_8))
  val output = OutputStreamWriter(FileOutputStream(FileDescriptor.out), UTF_8)
  System.setOut(System.err)
  McpBridge(
    target = { bridgeTarget(dataDir, vault) },
    input = input,
    output = output,
    log = { System.err.println("berill-mcp: $it") },
    wakeUp = startApp
  ).run()
}
