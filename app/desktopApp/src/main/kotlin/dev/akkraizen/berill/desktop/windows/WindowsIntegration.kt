package dev.akkraizen.berill.desktop.windows

import dev.akkraizen.berill.core.log.AppLog
import dev.akkraizen.berill.core.platform.DesktopIntegration
import java.io.File
import java.util.Properties

internal class WindowsIntegration(dir: File, onHotkey: () -> Unit) : DesktopIntegration {
  private val isWindows = System.getProperty("os.name").orEmpty().startsWith("Windows")
  private val prefsFile = File(dir, "desktop.properties")
  private val prefs = Properties().apply {
    runCatching { if (prefsFile.isFile) prefsFile.inputStream().use(::load) }
  }
  private val autostart = Autostart(dir)
  private val hotkeys = GlobalHotkey(onHotkey)

  override fun start() {
    if (!isWindows) return
    runCatching { autostart.refresh() }
    if (isHotkeyEnabled()) hotkeys.start(HotkeyCombo.byLabel(hotkey()))
  }

  override val autostartAvailable: Boolean = isWindows && autostart.available

  override fun isAutostartEnabled(): Boolean = isWindows && autostart.current() != null

  override fun setAutostart(enabled: Boolean): Boolean {
    if (!autostartAvailable) return false
    return autostart.set(enabled).also { AppLog.info(TAG, "autostart ${if (enabled) "on" else "off"}: $it") }
  }

  override val hotkeyOptions: List<String> = HotkeyCombo.OPTIONS.map { it.label }

  override fun hotkey(): String = HotkeyCombo.byLabel(prefs.getProperty(KEY_COMBO)).label

  override fun isHotkeyEnabled(): Boolean = isWindows && prefs.getProperty(KEY_HOTKEY, "true").toBoolean()

  override fun isHotkeyActive(): Boolean = hotkeys.active != null

  override fun setHotkey(enabled: Boolean, combo: String): Boolean {
    if (!isWindows) return false
    val selected = HotkeyCombo.byLabel(combo)
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
