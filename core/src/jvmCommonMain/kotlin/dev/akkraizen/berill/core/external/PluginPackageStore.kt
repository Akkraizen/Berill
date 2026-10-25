package dev.akkraizen.berill.core.external

import dev.akkraizen.berill.sdk.protocol.MANIFEST_FILE
import dev.akkraizen.berill.sdk.protocol.PLUGIN_DEX
import dev.akkraizen.berill.sdk.protocol.PLUGIN_JAR
import dev.akkraizen.berill.sdk.protocol.PluginManifest
import dev.akkraizen.berill.sdk.protocol.ProtocolJson
import dev.akkraizen.berill.sdk.protocol.problems
import java.io.ByteArrayInputStream
import java.io.File
import java.util.zip.ZipInputStream

private const val MAX_PACKAGE_BYTES = 20 * 1024 * 1024
private const val PENDING_SUFFIX = ".pending"
private const val REMOVE_MARKER = ".remove"

internal class PackageFiles(val manifest: ByteArray?, val jar: ByteArray?, val dex: ByteArray?)

internal fun readPackage(bytes: ByteArray): PackageFiles {
  var manifest: ByteArray? = null
  var jar: ByteArray? = null
  var dex: ByteArray? = null
  ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
    while (true) {
      val entry = zip.nextEntry ?: break
      when (entry.name) {
        MANIFEST_FILE -> manifest = zip.readBytes()
        PLUGIN_JAR -> jar = zip.readBytes()
        PLUGIN_DEX -> dex = zip.readBytes()
      }
    }
  }
  return PackageFiles(manifest, jar, dex)
}

internal fun parseManifest(bytes: ByteArray): PluginManifest =
  ProtocolJson.decodeFromString(PluginManifest.serializer(), bytes.decodeToString())

abstract class PluginPackageStore(protected val dir: File, private val codeFile: String) : ExternalPluginPlatform {
  init {
    dir.mkdirs()
    applyPendingChanges()
  }

  protected abstract fun codeProblems(code: ByteArray?): List<String>

  private fun PackageFiles.code(): ByteArray? = if (codeFile == PLUGIN_JAR) jar else dex

  private fun applyPendingChanges() {
    dir.listFiles()
      .orEmpty()
      .filter { it.isDirectory }
      .forEach { d ->
        when {
          File(d, REMOVE_MARKER).exists() -> d.deleteRecursively()
          d.name.endsWith(PENDING_SUFFIX) -> {
            val target = File(dir, d.name.removeSuffix(PENDING_SUFFIX))
            target.deleteRecursively()
            d.renameTo(target)
          }
        }
      }
  }

  override fun installed(): List<PluginManifest> =
    dir.listFiles()
      .orEmpty()
      .filter { it.isDirectory && !it.name.endsWith(PENDING_SUFFIX) && !File(it, REMOVE_MARKER).exists() }
      .mapNotNull { d ->
        runCatching { parseManifest(File(d, MANIFEST_FILE).readBytes()).takeIf { it.id == d.name } }.getOrNull()
      }

  override fun inspect(bytes: ByteArray): PackageInspection {
    if (bytes.size > MAX_PACKAGE_BYTES)
      return PackageInspection(null, listOf("пакет больше ${MAX_PACKAGE_BYTES / 1024 / 1024} МБ"))
    val files = runCatching { readPackage(bytes) }
      .getOrElse { return PackageInspection(null, listOf("не zip-архив")) }
    val manifestBytes = files.manifest ?: return PackageInspection(null, listOf("нет $MANIFEST_FILE"))
    val manifest = runCatching { parseManifest(manifestBytes) }
      .getOrElse { return PackageInspection(null, listOf("$MANIFEST_FILE: ${it.message?.take(200)}")) }
    return PackageInspection(manifest, manifest.problems() + codeProblems(files.code()))
  }

  override fun install(bytes: ByteArray): PackageInspection {
    val result = inspect(bytes)
    if (!result.ok) return result
    val manifest = result.manifest!!
    val files = readPackage(bytes)
    val target = File(dir, manifest.id + PENDING_SUFFIX)
    target.deleteRecursively()
    target.mkdirs()
    File(target, MANIFEST_FILE).writeBytes(files.manifest!!)
    File(target, codeFile).writeBytes(files.code()!!)
    val current = File(dir, manifest.id)
    if (!current.exists()) target.renameTo(current)
    File(current, REMOVE_MARKER).delete()
    return result
  }

  override fun uninstall(pluginId: String) {
    val d = File(dir, pluginId)
    if (d.isDirectory) File(d, REMOVE_MARKER).writeText("")
    File(dir, pluginId + PENDING_SUFFIX).deleteRecursively()
  }

  override fun pendingChanges(): Set<String> =
    dir.listFiles()
      .orEmpty()
      .filter { it.isDirectory }
      .mapNotNull { d ->
        when {
          d.name.endsWith(PENDING_SUFFIX) -> d.name.removeSuffix(PENDING_SUFFIX)
          File(d, REMOVE_MARKER).exists() -> d.name
          else -> null
        }
      }
      .toSet()
}
