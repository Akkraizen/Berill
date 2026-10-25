package dev.akkraizen.berill.desktop.windows

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopIntegrationTest {
  @Test
  fun installedAppStartsItsOwnExecutableInBackground() {
    val home = Files.createTempDirectory("jdk").toFile()
    val launch = launchCommand("C:\\Program Files\\Berill\\Berill.exe", home, "a.jar", home.resolve("autostart.args"))!!

    assertEquals("\"C:\\Program Files\\Berill\\Berill.exe\" --background", launch.command)
    assertNull(launch.argsFile)
  }

  @Test
  fun developmentRunStartsJavawWithArgumentFile() {
    val home = Files.createTempDirectory("jdk").toFile()
    home.resolve("bin").mkdirs()
    home.resolve("bin/javaw.exe").writeText("")
    val args = home.resolve("autostart.args")

    val launch = launchCommand(null, home, "C:\\work\\a.jar;C:\\work\\b.jar", args)

    assertNotNull(launch)
    assertTrue(launch.command.endsWith("\"@${args.absolutePath}\" dev.akkraizen.berill.MainKt --background"))
    assertEquals(args to "-cp\n\"C:/work/a.jar;C:/work/b.jar\"\n", launch.argsFile)
  }

  @Test
  fun withoutJavawOrClasspathAutostartIsUnavailable() {
    val home = Files.createTempDirectory("jdk").toFile()
    assertNull(launchCommand(null, home, "a.jar", home.resolve("x")))
    home.resolve("bin").mkdirs()
    home.resolve("bin/javaw.exe").writeText("")
    assertNull(launchCommand(null, home, "", home.resolve("x")))
  }

  @Test
  fun unknownHotkeyFallsBackToDefault() {
    assertEquals("Ctrl+Alt+B", HotkeyCombo.byLabel(null).label)
    assertEquals("Ctrl+Alt+B", HotkeyCombo.byLabel("F13").label)
    assertEquals("Ctrl+Alt+Space", HotkeyCombo.byLabel("Ctrl+Alt+Space").label)
    assertEquals(HotkeyCombo.OPTIONS.size, HotkeyCombo.OPTIONS.map { it.modifiers to it.key }.toSet().size)
  }
}
