package dev.akkraizen.berill.desktop.windows

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.WString
import com.sun.jna.ptr.IntByReference

internal interface Advapi32 : Library {
  fun RegGetValueW(
    key: Pointer,
    subKey: WString,
    value: WString,
    flags: Int,
    type: IntByReference?,
    data: Pointer?,
    size: IntByReference
  ): Int

  fun RegSetKeyValueW(key: Pointer, subKey: WString, valueName: WString, type: Int, data: Pointer, size: Int): Int

  fun RegDeleteKeyValueW(key: Pointer, subKey: WString, valueName: WString): Int

  companion object {
    val INSTANCE: Advapi32 by lazy { Native.load("advapi32", Advapi32::class.java) }
  }
}

internal interface User32 : Library {
  fun RegisterHotKey(hwnd: Pointer?, id: Int, modifiers: Int, vk: Int): Boolean

  fun UnregisterHotKey(hwnd: Pointer?, id: Int): Boolean

  fun GetMessageW(msg: Pointer, hwnd: Pointer?, min: Int, max: Int): Int

  fun PostThreadMessageW(threadId: Int, msg: Int, wParam: Pointer?, lParam: Pointer?): Boolean

  companion object {
    val INSTANCE: User32 by lazy { Native.load("user32", User32::class.java) }
  }
}

internal interface Kernel32 : Library {
  fun GetCurrentThreadId(): Int

  companion object {
    val INSTANCE: Kernel32 by lazy { Native.load("kernel32", Kernel32::class.java) }
  }
}
