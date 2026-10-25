package dev.akkraizen.berill.desktop

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.io.RandomAccessFile
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.security.SecureRandom
import kotlin.concurrent.thread

internal class SingleInstance private constructor(
  @Suppress("unused") private val channel: FileChannel,
  @Suppress("unused") private val lock: FileLock,
) {
  private val _activations = MutableStateFlow(0)

  val activations: StateFlow<Int> = _activations

  fun activate() {
    _activations.value++
  }

  private fun listen(dir: File) {
    val server = ServerSocket(0, 8, InetAddress.getLoopbackAddress())
    val secret =
      ByteArray(24).also(SecureRandom()::nextBytes).joinToString("") { "%02x".format(it) }
    File(dir, PORT_FILE).writeText("${server.localPort} $secret")
    thread(isDaemon = true, name = "berill-single-instance") {
      while (true) {
        val socket = runCatching { server.accept() }.getOrNull() ?: break
        runCatching {
          socket.use {
            it.soTimeout = 2_000
            val line = it.getInputStream().bufferedReader().readLine()
            if (line == "$COMMAND_SHOW $secret") _activations.value++
          }
        }
      }
    }
  }

  companion object {
    private const val LOCK_FILE = "instance.lock"
    private const val PORT_FILE = "instance.port"
    private const val COMMAND_SHOW = "show"

    fun acquire(dir: File, notify: Boolean = true): SingleInstance? {
      dir.mkdirs()
      val channel = RandomAccessFile(File(dir, LOCK_FILE), "rw").channel
      val lock = runCatching { channel.tryLock() }.getOrNull()
      if (lock == null) {
        channel.close()
        if (notify) signalRunning(dir)
        return null
      }
      return SingleInstance(channel, lock).also { instance ->
        runCatching { instance.listen(dir) }
      }
    }

    private fun signalRunning(dir: File) {
      runCatching {
        val (port, secret) = File(dir, PORT_FILE).readText().trim().split(' ', limit = 2)
        Socket().use { socket ->
          socket.connect(InetSocketAddress(InetAddress.getLoopbackAddress(), port.toInt()), 2_000)
          socket.getOutputStream().apply {
            write("$COMMAND_SHOW $secret\n".toByteArray())
            flush()
          }
        }
      }
    }
  }
}
