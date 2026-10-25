package dev.akkraizen.berill.updates

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ReleasesTest {
  @Test
  fun picksNewestVersionAndItsFiles() {
    val dir = Files.createTempDirectory("releases").toFile()
    listOf(
      "Berill-1.2.0-android.apk",
      "Berill-1.10.0-android.apk",
      "Berill-1.10.0-Setup.exe",
      "Berill-1.9.9-Setup.exe",
      "notes.txt"
    ).forEach { File(dir, it).writeText("x") }

    val latest = Releases(dir).latest()!!

    assertEquals("1.10.0", latest.version)
    assertEquals("/download/Berill-1.10.0-android.apk", latest.android)
    assertEquals("/download/Berill-1.10.0-Setup.exe", latest.windows)
  }

  @Test
  fun refusesUnexpectedFileNames() {
    val dir = Files.createTempDirectory("releases").toFile()
    File(dir, "Berill-1.0.0-android.apk").writeText("x")
    val releases = Releases(dir)

    assertNull(releases.file("../secret"))
    assertNull(releases.file("notes.txt"))
    assertEquals("Berill-1.0.0-android.apk", releases.file("Berill-1.0.0-android.apk")?.name)
  }

  @Test
  fun emptyDirectoryHasNoRelease() {
    assertNull(Releases(Files.createTempDirectory("releases").toFile()).latest())
  }
}
