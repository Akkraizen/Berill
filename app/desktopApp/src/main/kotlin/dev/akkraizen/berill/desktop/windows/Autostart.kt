package dev.akkraizen.berill.desktop.windows

import com.sun.jna.Memory
import com.sun.jna.Pointer
import com.sun.jna.WString
import com.sun.jna.ptr.IntByReference
import dev.akkraizen.berill.core.log.AppLog
import java.io.File

internal const val BACKGROUND_FLAG = "--background"

internal const val MAIN_CLASS = "dev.akkraizen.berill.MainKt"

internal class LaunchCommand(val command: String, val argsFile: Pair<File, String>? = null)

internal fun launchCommand(installedExe: String?, javaHome: File, classpath: String?, argsFile: File): LaunchCommand? {
  if (installedExe != null) return LaunchCommand("\"$installedExe\" $BACKGROUND_FLAG")
  val javaw = File(javaHome, "bin/javaw.exe").takeIf { it.isFile } ?: return null
  if (classpath.isNullOrBlank()) return null
  val content = "-cp\n\"${classpath.replace('\\', '/')}\"\n"
  return LaunchCommand(
    "\"${javaw.absolutePath}\" \"@${argsFile.absolutePath}\" $MAIN_CLASS $BACKGROUND_FLAG",
    argsFile to content
  )
}

internal class Autostart(dataDir: File) {
  private val installedExe = System.getProperty("jpackage.app-path")?.takeIf { File(it).isFile }
  private val launch = launchCommand(
    installedExe,
    File(System.getProperty("java.home")),
    System.getProperty("java.class.path"),
    File(dataDir, "autostart.args")
  )

  val available: Boolean = launch != null

  fun current(): String? = runCatching {
    val size = IntByReference(0)
    if (Advapi32.INSTANCE.RegGetValueW(HKCU, RUN, NAME, RRF_RT_REG_SZ, null, null, size) != 0) return null
    val buffer = Memory(size.value.toLong().coerceAtLeast(2))
    if (Advapi32.INSTANCE.RegGetValueW(HKCU, RUN, NAME, RRF_RT_REG_SZ, null, buffer, size) != 0) return null
    buffer.getWideString(0)
  }.getOrNull()

  fun set(enabled: Boolean): Boolean = runCatching {
    if (enabled) {
      val target = launch ?: return false
      target.argsFile?.let { (file, content) -> file.writeText(content) }
      val bytes = (target.command + "\u0000").toByteArray(Charsets.UTF_16LE)
      val data = Memory(bytes.size.toLong()).apply { write(0, bytes, 0, bytes.size) }
      Advapi32.INSTANCE.RegSetKeyValueW(HKCU, RUN, NAME, REG_SZ, data, bytes.size) == 0
    } else {
      val code = Advapi32.INSTANCE.RegDeleteKeyValueW(HKCU, RUN, NAME)
      code == 0 || code == ERROR_FILE_NOT_FOUND
    }
  }.onFailure { AppLog.error(TAG, "autostart change failed", it) }.getOrDefault(false)

  fun refresh() {
    val stored = current() ?: return
    val target = launch ?: return
    if (installedExe != null && stored != target.command) set(true)
  }

  private companion object {
    const val TAG = "desktop"
    val HKCU: Pointer = Pointer.createConstant(0x80000001.toInt().toLong())
    val RUN = WString("Software\\Microsoft\\Windows\\CurrentVersion\\Run")
    val NAME = WString("Berill")
    const val REG_SZ = 1
    const val RRF_RT_REG_SZ = 0x2
    const val ERROR_FILE_NOT_FOUND = 2
  }
}
