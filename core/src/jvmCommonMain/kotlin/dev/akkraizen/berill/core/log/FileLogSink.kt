package dev.akkraizen.berill.core.log

import java.io.File
import java.io.FileOutputStream

class FileLogSink(private val dir: File, private val echo: (String) -> Unit = {}) : LogSink {
  private val lock = Any()
  private val current = File(dir, "berill.log")

  init {
    dir.mkdirs()
  }

  override val location: String = dir.absolutePath

  override fun write(line: String) {
    echo(line)
    synchronized(lock) {
      if (current.length() > MAX_BYTES) rotate()
      FileOutputStream(current, true).use { it.write((line + "\n").toByteArray(Charsets.UTF_8)) }
    }
  }

  private fun rotate() {
    val second = File(dir, "berill.2.log")
    val first = File(dir, "berill.1.log")
    second.delete()
    first.renameTo(second)
    current.renameTo(first)
  }

  override fun tail(maxChars: Int): String = synchronized(lock) {
    val text = listOf(File(dir, "berill.1.log"), current)
      .filter { it.isFile }
      .joinToString("") { it.readText(Charsets.UTF_8) }
    text.takeLast(maxChars)
  }

  private companion object {
    const val MAX_BYTES = 1_000_000L
  }
}

fun installFileLog(dir: File, echo: (String) -> Unit = {}) {
  AppLog.sink = FileLogSink(dir, echo)
  val previous = Thread.getDefaultUncaughtExceptionHandler()
  Thread.setDefaultUncaughtExceptionHandler { thread, error ->
    AppLog.error("crash", "uncaught in thread ${thread.name}", error)
    previous?.uncaughtException(thread, error)
  }
}
