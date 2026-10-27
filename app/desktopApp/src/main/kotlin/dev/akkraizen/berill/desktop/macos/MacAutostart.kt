package dev.akkraizen.berill.desktop.macos

import dev.akkraizen.berill.core.log.AppLog
import dev.akkraizen.berill.desktop.windows.BACKGROUND_FLAG
import java.io.File

internal const val LAUNCH_AGENT_LABEL = "dev.akkraizen.berill"

internal fun launchAgentPlist(executable: String): String {
  fun xml(text: String) = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
  return """
    |<?xml version="1.0" encoding="UTF-8"?>
    |<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
    |<plist version="1.0">
    |<dict>
    |  <key>Label</key>
    |  <string>$LAUNCH_AGENT_LABEL</string>
    |  <key>ProgramArguments</key>
    |  <array>
    |    <string>${xml(executable)}</string>
    |    <string>$BACKGROUND_FLAG</string>
    |  </array>
    |  <key>RunAtLoad</key>
    |  <true/>
    |  <key>ProcessType</key>
    |  <string>Interactive</string>
    |</dict>
    |</plist>
    |""".trimMargin()
}

internal class MacAutostart(home: File, private val executable: String?) {
  private val file = File(home, "Library/LaunchAgents/$LAUNCH_AGENT_LABEL.plist")

  val available: Boolean = executable != null

  fun isEnabled(): Boolean = file.isFile

  fun set(enabled: Boolean): Boolean = runCatching {
    if (enabled) {
      val target = executable ?: return false
      file.parentFile.mkdirs()
      file.writeText(launchAgentPlist(target))
    } else {
      file.delete() || !file.exists()
    }
    true
  }.onFailure { AppLog.error(TAG, "autostart change failed", it) }.getOrDefault(false)

  fun refresh() {
    val target = executable ?: return
    if (file.isFile && file.readText() != launchAgentPlist(target)) set(true)
  }

  private companion object {
    const val TAG = "desktop"
  }
}
