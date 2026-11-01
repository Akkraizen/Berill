package dev.akkraizen.berill.updates

import dev.akkraizen.berill.protocol.LatestRelease
import dev.akkraizen.berill.protocol.UpdateSignature
import dev.akkraizen.berill.protocol.compareVersions
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

class Releases(val directory: File) {
  fun latest(): LatestRelease? {
    val files = directory.listFiles()?.filter { it.isFile } ?: return null
    val byVersion =
      files.mapNotNull { f -> NAME.matchEntire(f.name)?.let { it.groupValues[1] to f.name } }
        .groupBy({ it.first }, { it.second })
    val version = byVersion.keys.maxWithOrNull(::compareVersions) ?: return null
    val names = byVersion.getValue(version)
    val result = LatestRelease(
      version = version,
      android = names.firstOrNull { it.endsWith("-android.apk") }?.let { "/download/$it" },
      windows = (names.firstOrNull { it.endsWith("-windows.exe") } ?: names.firstOrNull { it.endsWith("-Setup.exe") })?.let { "/download/$it" },
      macos = names.firstOrNull { it.endsWith("-macos.dmg") }?.let { "/download/$it" },
      linuxDeb = names.firstOrNull { it.endsWith("-linux.deb") }?.let { "/download/$it" },
      linuxRpm = names.firstOrNull { it.endsWith("-linux.rpm") }?.let { "/download/$it" }
    )
    val paths = mapOf("android" to result.android, "windows" to result.windows, "macos" to result.macos,
      "linux-deb" to result.linuxDeb, "linux-rpm" to result.linuxRpm)
    val signatures = signatures(version)
    val artifacts = paths.mapNotNull { (platform, path) ->
      val source = path?.substringAfterLast('/')?.let(::file) ?: return@mapNotNull null
      platform to dev.akkraizen.berill.protocol.ReleaseArtifact(path, checksum(source), source.length(), signatures[source.name])
    }.toMap()
    val notesFile = File(directory, "Berill-$version-notes.md")
    val notes = if (notesFile.isFile && notesFile.length() <= 64 * 1024) notesFile.readText() else null
    return result.copy(artifacts = artifacts, notes = notes)
  }

  /** Signatures made by the release workflow; a missing or malformed file means unsigned packages. */
  private fun signatures(version: String): Map<String, String> {
    val file = File(directory, UpdateSignature.signaturesFile(version))
    if (!file.isFile || file.length() > 64 * 1024) return emptyMap()
    return runCatching {
      val files = Json.parseToJsonElement(file.readText()).jsonObject["files"]?.jsonObject ?: return emptyMap()
      files.mapNotNull { (name, value) ->
        val signature = (value as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return@mapNotNull null
        if (NAME.matchEntire(name) == null || !SIGNATURE.matches(signature)) null else name to signature
      }.toMap()
    }.getOrDefault(emptyMap())
  }

  private data class Fingerprint(val size: Long, val modified: Long, val hash: String)
  private val checksums = java.util.concurrent.ConcurrentHashMap<String, Fingerprint>()
  private fun checksum(file: File): String {
    val cached = checksums[file.name]
    if (cached != null && cached.size == file.length() && cached.modified == file.lastModified()) return cached.hash
    val digest = java.security.MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
      val buffer = ByteArray(65536)
      while (true) { val n = input.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) }
    }
    val hash = digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
    checksums[file.name] = Fingerprint(file.length(), file.lastModified(), hash)
    return hash
  }

  fun file(name: String): File? {
    if (NAME.matchEntire(name) == null) return null
    return File(
      directory,
      name
    ).takeIf { it.isFile && it.parentFile.canonicalPath == directory.canonicalPath }
  }

  companion object {
    private val SIGNATURE = Regex("[A-Za-z0-9+/]{40,200}={0,2}")
    private val NAME =
      Regex("""Berill-(\d+(?:\.\d+){1,3})-(android\.apk|Setup\.exe|windows\.exe|macos\.dmg|linux\.deb|linux\.rpm)""")

    fun fromEnv() = Releases(File(System.getenv("RELEASES_DIR") ?: "/app/releases"))
  }
}
