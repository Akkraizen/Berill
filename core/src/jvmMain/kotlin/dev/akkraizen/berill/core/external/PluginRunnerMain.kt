package dev.akkraizen.berill.core.external

import dev.akkraizen.berill.sdk.ExternalPlugin
import dev.akkraizen.berill.sdk.protocol.FromRunner
import dev.akkraizen.berill.sdk.protocol.ProtocolJson
import dev.akkraizen.berill.sdk.protocol.ToRunner
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileDescriptor
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.PrintStream
import java.net.URLClassLoader
import kotlin.system.exitProcess

const val PLUGIN_RUNNER_FLAG = "--berill-plugin-runner"

fun runPluginRunner() {
  val protocolOut = PrintStream(FileOutputStream(FileDescriptor.out), false, Charsets.UTF_8)
  val protocolIn = BufferedReader(InputStreamReader(FileInputStream(FileDescriptor.`in`), Charsets.UTF_8))
  System.setOut(PrintStream(FileOutputStream(FileDescriptor.err), true, Charsets.UTF_8))
  System.setIn(ByteArrayInputStream(ByteArray(0)))

  val lock = Any()
  val send: (FromRunner) -> Unit = { message ->
    val line = ProtocolJson.encodeToString(FromRunner.serializer(), message)
    synchronized(lock) {
      protocolOut.print(line)
      protocolOut.print('\n')
      protocolOut.flush()
    }
  }
  val runtime = RunnerRuntime(platform = ThreadRunnerPlatform(), send = send, instantiate = ::instantiateDesktopPlugin)
  while (true) {
    val line = protocolIn.readLine() ?: break
    if (line.isBlank()) continue
    val message = runCatching { ProtocolJson.decodeFromString(ToRunner.serializer(), line) }.getOrNull()
      ?: continue
    runtime.onMessage(message)
  }
  exitProcess(0)
}

private fun instantiateDesktopPlugin(load: ToRunner.Load): ExternalPlugin {
  val jar = File(requireNotNull(load.code) { "no plugin.jar" })
  val problems = BytecodeVerifier.verifyJar(jar.readBytes())
  require(problems.isEmpty()) { problems.take(5).joinToString("; ") }
  val loader =
    URLClassLoader(arrayOf(jar.toURI().toURL()), FilteringClassLoader(ExternalPlugin::class.java.classLoader))
  val cls = loader.loadClass(load.entry)
  require(ExternalPlugin::class.java.isAssignableFrom(cls)) {
    "${load.entry} is not an ExternalPlugin"
  }
  return cls.getDeclaredConstructor().newInstance() as ExternalPlugin
}

fun main() = runPluginRunner()
