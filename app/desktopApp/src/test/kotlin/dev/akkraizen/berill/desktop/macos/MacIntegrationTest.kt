package dev.akkraizen.berill.desktop.macos

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MacIntegrationTest {
  @Test
  fun launchAgentStartsInstalledAppInBackground() {
    val plist = launchAgentPlist("/Applications/Berill & Co.app/Contents/MacOS/Berill")

    assertTrue("<string>dev.akkraizen.berill</string>" in plist)
    assertTrue("<string>/Applications/Berill &amp; Co.app/Contents/MacOS/Berill</string>" in plist)
    assertTrue("<string>--background</string>" in plist)
    assertTrue("<key>RunAtLoad</key>\n  <true/>" in plist)
  }

  @Test
  fun autostartWritesAndRemovesLaunchAgent() {
    val home = Files.createTempDirectory("home").toFile()
    val autostart = MacAutostart(home, "/Applications/Berill.app/Contents/MacOS/Berill")
    val agent = home.resolve("Library/LaunchAgents/dev.akkraizen.berill.plist")

    assertTrue(autostart.set(true))
    assertTrue(autostart.isEnabled())
    assertEquals(launchAgentPlist("/Applications/Berill.app/Contents/MacOS/Berill"), agent.readText())
    assertTrue(autostart.set(false))
    assertFalse(agent.exists())
  }

  @Test
  fun autostartIsUnavailableOutsideInstalledApp() {
    val home = Files.createTempDirectory("home").toFile()
    val autostart = MacAutostart(home, null)

    assertFalse(autostart.available)
    assertFalse(autostart.set(true))
  }

  @Test
  fun unknownMacHotkeyFallsBackToDefault() {
    assertEquals("Ctrl+Option+B", MacHotkeyCombo.byLabel(null).label)
    assertEquals("Ctrl+Option+B", MacHotkeyCombo.byLabel("Ctrl+Alt+B").label)
    assertEquals("Cmd+Shift+B", MacHotkeyCombo.byLabel("Cmd+Shift+B").label)
    assertEquals(MacHotkeyCombo.OPTIONS.size, MacHotkeyCombo.OPTIONS.map { it.modifiers to it.keyCode }.toSet().size)
  }
}
