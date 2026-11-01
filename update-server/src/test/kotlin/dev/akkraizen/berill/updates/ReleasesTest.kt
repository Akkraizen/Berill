package dev.akkraizen.berill.updates

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ReleasesTest {
  @Test
  fun includesVerifiedMetadataAndReleaseNotes() {
    val dir = Files.createTempDirectory("update-manifest").toFile()
    try {
      val bytes = "installer".encodeToByteArray()
      File(dir, "Berill-9.0.0-windows.exe").writeBytes(bytes)
      File(dir, "Berill-9.0.0-notes.md").writeText("Release notes")
      val artifact = Releases(dir).latest()!!
      assertEquals("Release notes", artifact.notes)
      assertEquals(bytes.size.toLong(), artifact.artifacts.getValue("windows").size)
      val hash = java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
      assertEquals(hash, artifact.artifacts.getValue("windows").sha256)
      assertEquals(artifact.windows, artifact.artifacts.getValue("windows").path)
    } finally { dir.deleteRecursively() }
  }

  @Test
  fun supportsNewWindowsNamesAndPrefersThemOverLegacyNames() {
    val dir = Files.createTempDirectory("windows-releases").toFile()
    try {
      val releases = Releases(dir)
      File(dir, "Berill-1.2.0-Setup.exe").writeText("legacy")
      assertEquals("/download/Berill-1.2.0-Setup.exe", releases.latest()!!.windows)
      val current = File(dir, "Berill-1.2.0-windows.exe").apply { writeText("current") }
      assertEquals("/download/Berill-1.2.0-windows.exe", releases.latest()!!.windows)
      assertEquals(current, releases.file(current.name))
      File(dir, "Berill-1.2.1-windows.exe").writeText("next")
      assertEquals("1.2.1", releases.latest()!!.version)
      assertEquals("/download/Berill-1.2.1-windows.exe", releases.latest()!!.windows)
    } finally { dir.deleteRecursively() }
  }

  @Test
  fun picksNewestVersionAndItsFiles() {
    val dir = Files.createTempDirectory("releases").toFile()
    listOf(
      "Berill-1.2.0-android.apk",
      "Berill-1.10.0-android.apk",
      "Berill-1.10.0-Setup.exe",
      "Berill-1.10.0-macos.dmg",
      "Berill-1.9.9-Setup.exe",
      "notes.txt"
    ).forEach { File(dir, it).writeText("x") }

    val latest = Releases(dir).latest()!!

    assertEquals("1.10.0", latest.version)
    assertEquals("/download/Berill-1.10.0-android.apk", latest.android)
    assertEquals("/download/Berill-1.10.0-Setup.exe", latest.windows)
    assertEquals("/download/Berill-1.10.0-macos.dmg", latest.macos)
  }

  @Test
  fun refusesUnexpectedFileNames() {
    val dir = Files.createTempDirectory("releases").toFile()
    File(dir, "Berill-1.0.0-android.apk").writeText("x")
    File(dir, "Berill-1.0.0-macos.dmg").writeText("x")
    val releases = Releases(dir)

    assertNull(releases.file("../secret"))
    assertNull(releases.file("notes.txt"))
    assertEquals("Berill-1.0.0-android.apk", releases.file("Berill-1.0.0-android.apk")?.name)
    assertEquals("Berill-1.0.0-macos.dmg", releases.file("Berill-1.0.0-macos.dmg")?.name)
  }

  @Test
  fun emptyDirectoryHasNoRelease() {
    assertNull(Releases(Files.createTempDirectory("releases").toFile()).latest())
  }
}
