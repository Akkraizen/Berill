package dev.akkraizen.berill.core.sync

import java.net.InetAddress

internal actual fun clientDeviceName(): String =
  System.getenv("COMPUTERNAME")?.takeIf { it.isNotBlank() }
    ?: runCatching { InetAddress.getLocalHost().hostName }.getOrNull()
    ?: "PC"

internal actual val clientPlatform: String =
  if (System.getProperty("os.name").orEmpty().startsWith("Windows")) "windows" else "desktop"
