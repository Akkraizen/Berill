package dev.akkraizen.berill.desktop

import dev.akkraizen.berill.core.platform.DesktopIntegration
import dev.akkraizen.berill.desktop.macos.MacIntegration
import dev.akkraizen.berill.desktop.windows.WindowsIntegration
import java.io.File

internal object DesktopOs {
  private val name = System.getProperty("os.name").orEmpty()
  val isWindows: Boolean = name.startsWith("Windows")
  val isMac: Boolean = name.startsWith("Mac")
}

internal fun createDesktopIntegration(dir: File, onHotkey: () -> Unit): DesktopIntegration =
  if (DesktopOs.isMac) MacIntegration(dir, onHotkey) else WindowsIntegration(dir, onHotkey)
