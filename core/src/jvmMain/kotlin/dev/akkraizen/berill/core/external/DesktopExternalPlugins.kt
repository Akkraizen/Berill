package dev.akkraizen.berill.core.external

import dev.akkraizen.berill.sdk.protocol.FromRunner
import dev.akkraizen.berill.sdk.protocol.PLUGIN_JAR
import dev.akkraizen.berill.sdk.protocol.PluginManifest
import dev.akkraizen.berill.sdk.protocol.ProtocolJson
import dev.akkraizen.berill.sdk.protocol.ToRunner
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import kotlin.concurrent.thread

class DesktopExternalPlugins(dir: File) : PluginPackageStore(dir, PLUGIN_JAR) {
  override fun codeProblems(code: ByteArray?): List<String> =
    if (code == null) listOf("нет $PLUGIN_JAR") else BytecodeVerifier.verifyJar(code)

  override val launcher: RunnerLauncher = object : RunnerLauncher {
    override fun start(manifest: PluginManifest, language: String, listener: RunnerListener): RunnerConnection {
      val pluginDir = File(dir, manifest.id)
      val jar = File(pluginDir, PLUGIN_JAR)
      require(jar.isFile) { "нет $PLUGIN_JAR" }
      val process = ProcessBuilder(runnerCommand(pluginDir))
        .directory(pluginDir)
        .redirectErrorStream(false)
        .start()
      val writer = OutputStreamWriter(process.outputStream, Charsets.UTF_8)
      val lock = Any()
      val connection = object : RunnerConnection {
        override fun send(message: ToRunner) {
          val line = ProtocolJson.encodeToString(ToRunner.serializer(), message)
          synchronized(lock) {
            writer.write(line)
            writer.write("\n")
            writer.flush()
          }
        }

        override fun close() {
          runCatching { writer.close() }
          process.destroyForcibly()
        }
      }
      thread(isDaemon = true, name = "plugin-${manifest.id}-out") {
        val reader = BufferedReader(InputStreamReader(process.inputStream, Charsets.UTF_8))
        runCatching {
          while (true) {
            val line = reader.readLine() ?: break
            if (line.length > 8 * 1024 * 1024) continue
            val msg = runCatching { ProtocolJson.decodeFromString(FromRunner.serializer(), line) }
              .getOrNull() ?: continue
            listener.onMessage(msg)
          }
        }
      }
      thread(isDaemon = true, name = "plugin-${manifest.id}-err") {
        val reader = BufferedReader(InputStreamReader(process.errorStream, Charsets.UTF_8))
        var lines = 0
        runCatching {
          while (true) {
            val line = reader.readLine() ?: break
            if (lines++ < 2000) System.err.println("[plugin ${manifest.id}] $line")
          }
        }
      }
      thread(isDaemon = true, name = "plugin-${manifest.id}-wait") {
        val code = runCatching { process.waitFor() }.getOrDefault(-1)
        listener.onExit("код $code")
      }
      Runtime.getRuntime().addShutdownHook(Thread { process.destroyForcibly() })
      connection.send(ToRunner.Load(manifest.entry, language, code = jar.absolutePath))
      return connection
    }
  }
}

private fun runnerCommand(workDir: File): List<String> {
  val home = File(System.getProperty("java.home"))
  val java = listOf(File(home, "bin/java.exe"), File(home, "bin/java")).firstOrNull { it.isFile }
  if (java != null) {
    val args = File(workDir, "runner.args")
    val classpath = System.getProperty("java.class.path").replace('\\', '/')
    args.writeText("-cp\n\"$classpath\"\n", Charsets.UTF_8)
    return listOf(
      java.absolutePath,
      "-Xmx160m",
      "-Xss2m",
      "-XX:+UseSerialGC",
      "-XX:TieredStopAtLevel=1",
      "-Dfile.encoding=UTF-8",
      "-Djava.awt.headless=true",
      "@${args.absolutePath}",
      "dev.akkraizen.berill.core.external.PluginRunnerMainKt"
    )
  }
  val self = ProcessHandle.current().info().command().orElse(null)
    ?: error("не найден исполняемый файл приложения")
  return listOf(self, PLUGIN_RUNNER_FLAG)
}
