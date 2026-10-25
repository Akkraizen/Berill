package dev.akkraizen.berill.updates

import dev.akkraizen.berill.protocol.LatestRelease
import dev.akkraizen.berill.protocol.compareVersions
import java.io.File

class Releases(val directory: File) {
  fun latest(): LatestRelease? {
    val files = directory.listFiles()?.filter { it.isFile } ?: return null
    val byVersion = files.mapNotNull { f -> NAME.matchEntire(f.name)?.let { it.groupValues[1] to f.name } }
      .groupBy({ it.first }, { it.second })
    val version = byVersion.keys.maxWithOrNull(::compareVersions) ?: return null
    val names = byVersion.getValue(version)
    return LatestRelease(
      version = version,
      android = names.firstOrNull { it.endsWith("-android.apk") }?.let { "/download/$it" },
      windows = names.firstOrNull { it.endsWith("-Setup.exe") }?.let { "/download/$it" }
    )
  }

  fun file(name: String): File? {
    if (NAME.matchEntire(name) == null) return null
    return File(directory, name).takeIf { it.isFile && it.parentFile.canonicalPath == directory.canonicalPath }
  }

  companion object {
    private val NAME = Regex("""Berill-(\d+(?:\.\d+){1,3})-(android\.apk|Setup\.exe)""")

    fun fromEnv() = Releases(File(System.getenv("RELEASES_DIR") ?: "/app/releases"))
  }
}
