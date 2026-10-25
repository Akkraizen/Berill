package dev.akkraizen.berill.core.external

import dev.akkraizen.berill.sdk.protocol.FromRunner
import dev.akkraizen.berill.sdk.protocol.PluginManifest
import dev.akkraizen.berill.sdk.protocol.ToRunner

interface ExternalPluginPlatform {

  fun installed(): List<PluginManifest>

  fun inspect(bytes: ByteArray): PackageInspection

  fun install(bytes: ByteArray): PackageInspection

  fun uninstall(pluginId: String)

  fun pendingChanges(): Set<String>

  val launcher: RunnerLauncher
}

data class PackageInspection(val manifest: PluginManifest?, val problems: List<String>) {
  val ok: Boolean
    get() = manifest != null && problems.isEmpty()
}

interface RunnerLauncher {

  fun start(manifest: PluginManifest, language: String, listener: RunnerListener): RunnerConnection
}

interface RunnerListener {
  fun onMessage(message: FromRunner)

  fun onExit(reason: String)
}

interface RunnerConnection {
  fun send(message: ToRunner)

  fun close()
}
