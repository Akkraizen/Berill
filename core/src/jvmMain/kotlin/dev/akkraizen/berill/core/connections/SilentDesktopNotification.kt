package dev.akkraizen.berill.core.connections

import com.sun.jna.*
import com.sun.jna.win32.StdCallLibrary
import java.awt.Frame
import javax.swing.Timer
import dev.akkraizen.berill.core.log.AppLog

/** Native silent balloons avoid AWT's uncontrollable system notification sound. Called on the EDT. */
internal object SilentDesktopNotification {
  fun show(title: String, body: String) {
    try {
      val os = System.getProperty("os.name").orEmpty()
      when {
        os.startsWith("Windows") -> windows(title, body)
        os.startsWith("Mac") -> ProcessBuilder("/usr/bin/osascript", "-e", "display notification ${appleString(body)} with title ${appleString(title)}").start()
        else -> ProcessBuilder("notify-send", "--hint=boolean:suppress-sound:true", "--", title, body).start()
      }
    } catch (e: Exception) { AppLog.error("notifications", "silent delivery", e) }
  }
  private fun appleString(value: String): String = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace('\n', ' ').replace('\r', ' ') + "\""
  private fun windows(title: String, body: String) {
    val frame = Frame()
    val data = BalloonData()
    var added = false
    try {
      frame.isUndecorated = true
      frame.addNotify() // Creates a native owner without showing a window.
      data.cbSize = data.size()
      data.hWnd = Native.getComponentPointer(frame)
      data.uID = 1
      data.uFlags = 0x2 or 0x4 // NIF_ICON | NIF_TIP
      data.hIcon = BalloonUser.INSTANCE.LoadIconW(null, Pointer(32516))
      "Berill".toCharArray().copyInto(data.szTip)
      check(BalloonShell.INSTANCE.Shell_NotifyIconW(0, data)) { "Cannot create notification icon" }
      added = true
      data.uFlags = 0x10 or 0x40 // NIF_INFO | NIF_REALTIME
      title.take(63).toCharArray().copyInto(data.szInfoTitle)
      body.take(255).toCharArray().copyInto(data.szInfo)
      data.dwInfoFlags = 0x1 or 0x10 // NIIF_INFO | NIIF_NOSOUND
      data.uTimeoutOrVersion = 10000
      check(BalloonShell.INSTANCE.Shell_NotifyIconW(1, data)) { "Cannot show silent notification" }
      Timer(12000) { event ->
        try { BalloonShell.INSTANCE.Shell_NotifyIconW(2, data) }
        finally { frame.dispose(); (event.source as Timer).stop() }
      }.apply { isRepeats = false; start() }
    } catch (e: Exception) {
      try { if (added) BalloonShell.INSTANCE.Shell_NotifyIconW(2, data) }
      finally { frame.dispose() }
      throw e
    }
  }
}

internal interface BalloonShell : StdCallLibrary {
  fun Shell_NotifyIconW(message: Int, data: BalloonData): Boolean
  companion object { val INSTANCE: BalloonShell by lazy { Native.load("shell32", BalloonShell::class.java) } }
}
internal interface BalloonUser : StdCallLibrary {
  fun LoadIconW(instance: Pointer?, name: Pointer): Pointer?
  companion object { val INSTANCE: BalloonUser by lazy { Native.load("user32", BalloonUser::class.java) } }
}
@Structure.FieldOrder("cbSize", "hWnd", "uID", "uFlags", "uCallbackMessage", "hIcon", "szTip", "dwState", "dwStateMask", "szInfo", "uTimeoutOrVersion", "szInfoTitle", "dwInfoFlags", "guidItem", "hBalloonIcon")
internal class BalloonData : Structure() {
  @JvmField var cbSize = 0
  @JvmField var hWnd: Pointer? = null
  @JvmField var uID = 0
  @JvmField var uFlags = 0
  @JvmField var uCallbackMessage = 0
  @JvmField var hIcon: Pointer? = null
  @JvmField var szTip = CharArray(128)
  @JvmField var dwState = 0
  @JvmField var dwStateMask = 0
  @JvmField var szInfo = CharArray(256)
  @JvmField var uTimeoutOrVersion = 0
  @JvmField var szInfoTitle = CharArray(64)
  @JvmField var dwInfoFlags = 0
  @JvmField var guidItem = ByteArray(16)
  @JvmField var hBalloonIcon: Pointer? = null
}
