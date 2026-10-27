package dev.akkraizen.berill.desktop.macos

import com.sun.jna.Callback
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.ptr.PointerByReference
import dev.akkraizen.berill.core.log.AppLog

internal data class MacHotkeyCombo(val label: String, val modifiers: Int, val keyCode: Int) {
  companion object {
    const val CMD = 0x0100
    const val SHIFT = 0x0200
    const val OPTION = 0x0800
    const val CONTROL = 0x1000
    private const val KEY_B = 0x0B
    private const val KEY_SPACE = 0x31

    val OPTIONS = listOf(
      MacHotkeyCombo("Ctrl+Option+B", CONTROL or OPTION, KEY_B),
      MacHotkeyCombo("Cmd+Shift+B", CMD or SHIFT, KEY_B),
      MacHotkeyCombo("Ctrl+Option+Space", CONTROL or OPTION, KEY_SPACE),
      MacHotkeyCombo("Cmd+Option+Space", CMD or OPTION, KEY_SPACE)
    )

    fun byLabel(label: String?): MacHotkeyCombo = OPTIONS.firstOrNull { it.label == label } ?: OPTIONS.first()
  }
}

internal class MacHotkey(private val onPressed: () -> Unit) {
  @Suppress("FunctionName")
  private interface Carbon : Library {
    fun GetEventDispatcherTarget(): Pointer

    fun InstallEventHandler(
      target: Pointer,
      handler: EventHandler,
      numTypes: Int,
      typeList: EventTypeSpec,
      userData: Pointer?,
      handlerRef: PointerByReference?
    ): Int

    fun RemoveEventHandler(handlerRef: Pointer): Int

    fun RegisterEventHotKey(
      keyCode: Int,
      modifiers: Int,
      id: EventHotKeyID.ByValue,
      target: Pointer,
      options: Int,
      hotKeyRef: PointerByReference
    ): Int

    fun UnregisterEventHotKey(hotKeyRef: Pointer): Int
  }

  @Structure.FieldOrder("eventClass", "eventKind")
  class EventTypeSpec : Structure() {
    @JvmField var eventClass: Int = 0
    @JvmField var eventKind: Int = 0
  }

  @Structure.FieldOrder("signature", "id")
  open class EventHotKeyID : Structure() {
    @JvmField var signature: Int = 0
    @JvmField var id: Int = 0

    class ByValue : EventHotKeyID(), Structure.ByValue
  }

  fun interface EventHandler : Callback {
    fun invoke(nextHandler: Pointer?, event: Pointer?, userData: Pointer?): Int
  }

  private val carbon by lazy { Native.load("Carbon", Carbon::class.java) }

  private val handler = EventHandler { _, _, _ ->
    runCatching(onPressed)
    0
  }
  private var handlerRef: Pointer? = null
  private var hotKeyRef: Pointer? = null

  @Volatile
  var active: MacHotkeyCombo? = null
    private set

  @Synchronized
  fun start(combo: MacHotkeyCombo): Boolean {
    if (active == combo) return true
    stop()
    return runCatching {
      val target = carbon.GetEventDispatcherTarget()
      if (handlerRef == null) {
        val types = EventTypeSpec().apply {
          eventClass = KEYBOARD_EVENT_CLASS
          eventKind = HOTKEY_PRESSED
        }
        val ref = PointerByReference()
        check(carbon.InstallEventHandler(target, handler, 1, types, null, ref) == 0) { "InstallEventHandler failed" }
        handlerRef = ref.value
      }
      val id = EventHotKeyID.ByValue().apply {
        signature = SIGNATURE
        id = 1
      }
      val ref = PointerByReference()
      val status = carbon.RegisterEventHotKey(combo.keyCode, combo.modifiers, id, target, 0, ref)
      if (status != 0) {
        AppLog.warn(TAG, "global hotkey ${combo.label} was not registered (status $status)")
        return false
      }
      hotKeyRef = ref.value
      active = combo
      AppLog.info(TAG, "global hotkey ${combo.label} registered")
      true
    }.onFailure { AppLog.error(TAG, "global hotkey: native call failed", it) }.getOrDefault(false)
  }

  @Synchronized
  fun stop() {
    hotKeyRef?.let { runCatching { carbon.UnregisterEventHotKey(it) } }
    hotKeyRef = null
    active = null
  }

  private companion object {
    const val TAG = "desktop"
    const val KEYBOARD_EVENT_CLASS = 0x6B657962
    const val HOTKEY_PRESSED = 5
    const val SIGNATURE = 0x6265726C
  }
}
