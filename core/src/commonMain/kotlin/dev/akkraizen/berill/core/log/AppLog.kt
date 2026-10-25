package dev.akkraizen.berill.core.log

import kotlin.concurrent.Volatile
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

interface LogSink {
  fun write(line: String)

  fun tail(maxChars: Int): String

  val location: String?
}

private object ConsoleSink : LogSink {
  private val lines = ArrayDeque<String>()

  override fun write(line: String) {
    println(line)
    lines.addLast(line)
    while (lines.size > 500) lines.removeFirst()
  }

  override fun tail(maxChars: Int): String = lines.joinToString("\n").takeLast(maxChars)

  override val location: String? = null
}

object AppLog {
  @Volatile
  var sink: LogSink = ConsoleSink

  @Volatile
  var openLocation: (() -> Unit)? = null

  fun info(tag: String, message: String) = log("INFO ", tag, message, null)

  fun warn(tag: String, message: String, error: Throwable? = null) = log("WARN ", tag, message, error)

  fun error(tag: String, message: String, error: Throwable? = null) = log("ERROR", tag, message, error)

  fun tail(maxChars: Int = 64_000): String = runCatching { sink.tail(maxChars) }.getOrDefault("")

  private fun log(level: String, tag: String, message: String, error: Throwable?) {
    val time = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).toString().take(23).padEnd(23)
    val text = buildString {
      append(time).append(' ').append(level).append(' ').append(tag).append(": ").append(message)
      if (error != null) append('\n').append(error.stackTraceToString().lines().take(40).joinToString("\n") { "    $it" })
    }
    runCatching { sink.write(text) }
  }
}
