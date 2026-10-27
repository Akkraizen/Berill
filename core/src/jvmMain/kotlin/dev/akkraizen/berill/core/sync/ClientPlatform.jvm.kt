package dev.akkraizen.berill.core.sync

import java.net.InetAddress
import java.util.concurrent.TimeUnit

private val osName = System.getProperty("os.name").orEmpty()

internal actual fun clientDeviceName(): String =
  System.getenv("COMPUTERNAME")?.takeIf { it.isNotBlank() }
    ?: macComputerName()
    ?: runCatching { InetAddress.getLocalHost().hostName }.getOrNull()
    ?: "PC"

internal actual val clientPlatform: String = when {
  osName.startsWith("Windows") -> "windows"
  osName.startsWith("Mac") -> "macos"
  else -> "desktop"
}

private fun macComputerName(): String? {
  if (!osName.startsWith("Mac")) return null
  return runCatching {
    val process = ProcessBuilder("scutil", "--get", "ComputerName").redirectErrorStream(true).start()
    if (!process.waitFor(2, TimeUnit.SECONDS)) {
      process.destroy()
      return null
    }
    process.inputStream.bufferedReader().readText().trim().takeIf { process.exitValue() == 0 && it.isNotBlank() }
  }.getOrNull()
}
