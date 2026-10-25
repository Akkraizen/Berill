package dev.akkraizen.berill.desktop.windows

import com.sun.jna.Memory
import com.sun.jna.Native
import dev.akkraizen.berill.core.log.AppLog
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

internal data class HotkeyCombo(val label: String, val modifiers: Int, val key: Int) {
  companion object {
    const val MOD_ALT = 0x1
    const val MOD_CONTROL = 0x2
    const val MOD_SHIFT = 0x4
    private const val VK_SPACE = 0x20
    private const val VK_B = 0x42

    val OPTIONS = listOf(
      HotkeyCombo("Ctrl+Alt+B", MOD_CONTROL or MOD_ALT, VK_B),
      HotkeyCombo("Ctrl+Alt+Shift+B", MOD_CONTROL or MOD_ALT or MOD_SHIFT, VK_B),
      HotkeyCombo("Ctrl+Alt+Space", MOD_CONTROL or MOD_ALT, VK_SPACE),
      HotkeyCombo("Ctrl+Alt+Shift+Space", MOD_CONTROL or MOD_ALT or MOD_SHIFT, VK_SPACE)
    )

    fun byLabel(label: String?): HotkeyCombo = OPTIONS.firstOrNull { it.label == label } ?: OPTIONS.first()
  }
}

internal class GlobalHotkey(private val onPressed: () -> Unit) {
  @Volatile
  private var threadId = 0
  private var worker: Thread? = null

  @Volatile
  var active: HotkeyCombo? = null
    private set

  @Synchronized
  fun start(combo: HotkeyCombo): Boolean {
    if (active == combo) return true
    stop()
    var registered = false
    var error = 0
    val ready = CountDownLatch(1)
    val t = thread(isDaemon = true, name = "berill-hotkey") {
      val user32 = try {
        threadId = Kernel32.INSTANCE.GetCurrentThreadId()
        User32.INSTANCE.also {
          registered = it.RegisterHotKey(null, ID, combo.modifiers or MOD_NOREPEAT, combo.key)
          if (!registered) error = Native.getLastError()
        }
      } catch (e: Throwable) {
        AppLog.error(TAG, "global hotkey: native call failed", e)
        null
      } finally {
        ready.countDown()
      }
      if (user32 == null || !registered) return@thread
      val msg = Memory(64)
      try {
        while (user32.GetMessageW(msg, null, WM_HOTKEY, WM_HOTKEY) > 0) {
          runCatching(onPressed)
        }
      } finally {
        user32.UnregisterHotKey(null, ID)
      }
    }
    if (!ready.await(2, TimeUnit.SECONDS)) AppLog.warn(TAG, "global hotkey: registration timed out")
    if (registered) {
      worker = t
      active = combo
      AppLog.info(TAG, "global hotkey ${combo.label} registered")
    } else {
      AppLog.warn(TAG, "global hotkey ${combo.label} was not registered (error $error)")
    }
    return registered
  }

  @Synchronized
  fun stop() {
    val w = worker ?: return
    runCatching { User32.INSTANCE.PostThreadMessageW(threadId, WM_QUIT, null, null) }
    w.join(1_000)
    if (w.isAlive) AppLog.warn(TAG, "global hotkey thread did not stop")
    worker = null
    active = null
  }

  private companion object {
    const val TAG = "desktop"
    const val ID = 1
    const val MOD_NOREPEAT = 0x4000
    const val WM_HOTKEY = 0x0312
    const val WM_QUIT = 0x0012
  }
}
