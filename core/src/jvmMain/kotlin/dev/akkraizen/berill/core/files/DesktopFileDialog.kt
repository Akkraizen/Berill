package dev.akkraizen.berill.core.files

import com.sun.jna.Function
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Platform
import com.sun.jna.Pointer
import com.sun.jna.WString
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.PointerByReference
import com.sun.jna.win32.StdCallLibrary
import java.awt.Dialog
import java.awt.FileDialog
import java.awt.Frame
import java.awt.KeyboardFocusManager
import java.io.File
import java.io.IOException
import java.util.concurrent.Executors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext

private val windowsFileDialogThread by lazy {
  Executors.newSingleThreadExecutor { task -> Thread(task, "Berill-file-dialog").apply { isDaemon = true } }
    .asCoroutineDispatcher()
}

internal suspend fun openDesktopFile(title: String, extensions: List<String>): File? {
  return openDesktopFiles(title, extensions, false).firstOrNull()
}

internal suspend fun openDesktopFiles(title: String, extensions: List<String>, multiple: Boolean = true): List<File> {
  val owner = withContext(Dispatchers.Main) { KeyboardFocusManager.getCurrentKeyboardFocusManager().activeWindow }
  if (Platform.isWindows()) {
    val handle = withContext(Dispatchers.Main) { owner?.takeIf { it.isDisplayable }?.let(Native::getWindowPointer) }
    return withContext(windowsFileDialogThread) {
      WindowsOpenFileDialog(title, extensions, multiple).use { it.showFiles(handle) }
    }
  }
  return withContext(Dispatchers.Main) {
    val dialog = if (owner is Dialog) FileDialog(owner, title, FileDialog.LOAD)
    else FileDialog(owner as? Frame, title, FileDialog.LOAD)
    try {
      dialog.isMultipleMode = multiple
      dialog.file = extensions.joinToString(";") { "*.$it" }
      dialog.setFilenameFilter { _, name -> extensions.isEmpty() || name.substringAfterLast('.').lowercase() in extensions }
      dialog.isVisible = true
      dialog.files.toList()
    } finally {
      dialog.dispose()
    }
  }
}

internal class WindowsOpenFileDialog(title: String, extensions: List<String>, private val multiple: Boolean = false) : AutoCloseable {
  private val allocations = mutableListOf<Memory>()
  private val dialog: Pointer
  private var closed = false

  init {
    checkResult(Ole.instance.CoInitializeEx(null, COINIT_APARTMENTTHREADED), "CoInitializeEx")
    var created: Pointer? = null
    try {
      val classId = allocate(16)
      val interfaceId = allocate(16)
      checkResult(Ole.instance.CLSIDFromString(WString("{DC1C5A9C-E88A-4DDE-A5A1-60F82A20AEF7}"), classId), "CLSIDFromString")
      checkResult(Ole.instance.CLSIDFromString(WString("{D57C7288-D4AD-4768-BE02-9D969532D960}"), interfaceId), "CLSIDFromString")
      val result = PointerByReference()
      checkResult(Ole.instance.CoCreateInstance(classId, null, CLSCTX_INPROC_SERVER, interfaceId, result), "CoCreateInstance")
      created = checkNotNull(result.value)
      dialog = created
      checkResult(invoke(dialog, SET_TITLE, WString(title)), "SetTitle")
      checkResult(invoke(
        dialog,
        SET_OPTIONS,
        options or FOS_FORCEFILESYSTEM or FOS_PATHMUSTEXIST or FOS_FILEMUSTEXIST or
          (if (multiple) FOS_ALLOWMULTISELECT else 0)
      ), "SetOptions")
      if (extensions.isNotEmpty()) {
        val name = wideString(extensions.joinToString(", ") { it.uppercase() })
        val pattern = wideString(extensions.joinToString(";") { "*.$it" })
        val filter = allocate(Native.POINTER_SIZE * 2L)
        filter.setPointer(0, name)
        filter.setPointer(Native.POINTER_SIZE.toLong(), pattern)
        checkResult(invoke(dialog, SET_FILE_TYPES, 1, filter), "SetFileTypes")
      }
    } catch (error: Throwable) {
      created?.let { invoke(it, RELEASE) }
      allocations.forEach { it.close() }
      Ole.instance.CoUninitialize()
      throw error
    }
  }

  internal val options: Int
    get() {
      check(!closed)
      val flags = IntByReference()
      checkResult(invoke(dialog, GET_OPTIONS, flags), "GetOptions")
      return flags.value
    }

  fun showFiles(owner: Pointer?): List<File> {
    check(!closed)
    val status = invoke(dialog, SHOW, owner)
    if (status == HRESULT_CANCELLED) return emptyList()
    checkResult(status, "Show")
    val result = PointerByReference()
    if (!multiple) {
      checkResult(invoke(dialog, GET_RESULT, result), "GetResult")
      return listOf(readFile(checkNotNull(result.value)))
    }
    checkResult(invoke(dialog, GET_RESULTS, result), "GetResults")
    val items = checkNotNull(result.value)
    try {
      val count = IntByReference()
      checkResult(invoke(items, GET_COUNT, count), "GetCount")
      return List(count.value) { index ->
        val item = PointerByReference()
        checkResult(invoke(items, GET_ITEM_AT, index, item), "GetItemAt")
        readFile(checkNotNull(item.value))
      }
    } finally {
      invoke(items, RELEASE)
    }
  }

  private fun readFile(item: Pointer): File {
    try {
      val path = PointerByReference()
      checkResult(invoke(item, GET_DISPLAY_NAME, SIGDN_FILESYSPATH, path), "GetDisplayName")
      val text = checkNotNull(path.value)
      return try { File(text.getWideString(0)) } finally { Ole.instance.CoTaskMemFree(text) }
    } finally {
      invoke(item, RELEASE)
    }
  }

  override fun close() {
    if (closed) return
    closed = true
    try { invoke(dialog, RELEASE) } finally {
      allocations.forEach { it.close() }
      Ole.instance.CoUninitialize()
    }
  }

  private fun allocate(size: Long): Memory = Memory(size).also { allocations += it }

  private fun wideString(text: String): Memory = allocate((text.length + 1L) * Native.WCHAR_SIZE)
    .also { it.setWideString(0, text) }

  private interface Ole : StdCallLibrary {
    fun CoInitializeEx(reserved: Pointer?, flags: Int): Int
    fun CoUninitialize()
    fun CLSIDFromString(value: WString, result: Pointer): Int
    fun CoCreateInstance(classId: Pointer, outer: Pointer?, context: Int, interfaceId: Pointer, result: PointerByReference): Int
    fun CoTaskMemFree(pointer: Pointer)

    companion object {
      val instance: Ole by lazy { Native.load("ole32", Ole::class.java) }
    }
  }

  companion object {
    private const val RELEASE = 2
    private const val SHOW = 3
    private const val SET_FILE_TYPES = 4
    private const val SET_OPTIONS = 9
    private const val GET_OPTIONS = 10
    private const val SET_TITLE = 17
    private const val GET_RESULTS = 27
    private const val GET_COUNT = 7
    private const val GET_ITEM_AT = 8
    private const val GET_RESULT = 20
    private const val GET_DISPLAY_NAME = 5
    private const val COINIT_APARTMENTTHREADED = 2
    private const val CLSCTX_INPROC_SERVER = 1
    internal const val FOS_ALLOWMULTISELECT = 0x200
    internal const val FOS_FORCEFILESYSTEM = 0x40
    internal const val FOS_PATHMUSTEXIST = 0x800
    internal const val FOS_FILEMUSTEXIST = 0x1000
    private val SIGDN_FILESYSPATH = 0x80058000.toInt()
    private val HRESULT_CANCELLED = 0x800704C7.toInt()

    private fun invoke(target: Pointer, slot: Int, vararg arguments: Any?): Int {
      val method = target.getPointer(0).getPointer(slot.toLong() * Native.POINTER_SIZE)
      return Function.getFunction(method, Function.ALT_CONVENTION).invokeInt(arrayOf(target, *arguments))
    }

    private fun checkResult(status: Int, operation: String) {
      if (status < 0) throw IOException("$operation failed: HRESULT 0x${status.toUInt().toString(16)}")
    }
  }
}
