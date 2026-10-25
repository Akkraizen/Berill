package dev.akkraizen.berill.core.platform

interface DesktopIntegration {
  fun start() {}

  val autostartAvailable: Boolean

  fun isAutostartEnabled(): Boolean

  fun setAutostart(enabled: Boolean): Boolean

  val hotkeyOptions: List<String>

  fun hotkey(): String

  fun isHotkeyEnabled(): Boolean

  fun isHotkeyActive(): Boolean

  fun setHotkey(enabled: Boolean, combo: String = hotkey()): Boolean
}
