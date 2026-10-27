package dev.akkraizen.berill.desktop.macos

import dev.akkraizen.berill.core.log.AppLog
import dev.akkraizen.berill.core.platform.DesktopIntegration
import java.io.File
import java.util.Properties

internal class MacIntegration(dir: File, onHotkey: () -> Unit) : DesktopIntegration {
  private val prefsFile = File(dir, "desktop.properties")
  private val prefs = Properties().apply {
    runCatching { if (prefsFile.isFile) prefsFile.inputStream().use(::load) }
  }
  private val autostart = MacAutostart(
    File(System.getProperty("user.home")),
    System.getProperty("jpackage.app-path")?.takeIf { File(it).isFile }
  )
  private val hotkeys = MacHotkey(onHotkey)

  override fun start() {
    runCatching { autostart.refresh() }
    if (isHotkeyEnabled()) hotkeys.start(MacHotkeyCombo.byLabel(hotkey()))
  }

  override val autostartAvailable: Boolean = autostart.available

  override fun isAutostartEnabled(): Boolean = autostart.isEnabled()

  override fun setAutostart(enabled: Boolean): Boolean {
    if (!autostartAvailable) return false
    return autostart.set(enabled).also { AppLog.info(TAG, "autostart ${if (enabled) "on" else "off"}: $it") }
  }

  override val hotkeyOptions: List<String> = MacHotkeyCombo.OPTIONS.map { it.label }

  override fun hotkey(): String = MacHotkeyCombo.byLabel(prefs.getProperty(KEY_COMBO)).label

  override fun isHotkeyEnabled(): Boolean = prefs.getProperty(KEY_HOTKEY, "false").toBoolean()

  override fun isHotkeyActive(): Boolean = hotkeys.active != null

  override fun setHotkey(enabled: Boolean, combo: String): Boolean {
    val selected = MacHotkeyCombo.byLabel(combo)
    prefs.setProperty(KEY_HOTKEY, enabled.toString())
    prefs.setProperty(KEY_COMBO, selected.label)
    runCatching { prefsFile.outputStream().use { prefs.store(it, null) } }
    return if (enabled) hotkeys.start(selected) else true.also { hotkeys.stop() }
  }

  private companion object {
    const val TAG = "desktop"
    const val KEY_HOTKEY = "hotkey.enabled"
    const val KEY_COMBO = "hotkey.combo"
  }
}
