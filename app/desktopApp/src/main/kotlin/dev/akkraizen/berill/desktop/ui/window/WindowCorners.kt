package dev.akkraizen.berill.desktop.ui.window

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.ptr.IntByReference
import java.awt.Window

internal interface DwmApi : Library {
  fun DwmSetWindowAttribute(hwnd: Pointer, attribute: Int, value: Pointer, size: Int): Int

  companion object {
    val INSTANCE: DwmApi by lazy { Native.load("dwmapi", DwmApi::class.java) }
  }
}

private const val DWMWA_WINDOW_CORNER_PREFERENCE = 33

private const val DWMWCP_ROUND = 2

internal fun Window.roundCorners() {
  if (!System.getProperty("os.name").orEmpty().startsWith("Windows")) return
  runCatching {
    val hwnd = Native.getComponentPointer(this) ?: return
    val preference = IntByReference(DWMWCP_ROUND)
    DwmApi.INSTANCE.DwmSetWindowAttribute(hwnd, DWMWA_WINDOW_CORNER_PREFERENCE, preference.pointer, 4)
  }
}
