package dev.akkraizen.berill

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import dev.akkraizen.berill.core.BerillCore
import dev.akkraizen.berill.core.db.BerillDatabase
import dev.akkraizen.berill.core.ui.shell.BerillShell
import kotlinx.coroutines.*
import java.io.File
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class)
class FocusModeRenderTest {
  @Test fun focusedEditorAndNotificationsSettingsRenderWithoutLosingExternalEdits() = runBlocking(Dispatchers.Main) {
    val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
    BerillDatabase.Schema.create(driver)
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    val core = BerillCore(driver, BerillPlugins, scope)
    core.finishOnboarding(); core.notes.save("n", "Focus", "# A quiet place\nLong-form writing without the sidebar or note list.")
    core.notes.save("other", "Another note", "Keep this note intact.")
    core.nav.openNote("n"); core.focusedNote.value = "n"
    val scene = ImageComposeScene(1280, 800) { BerillShell(core) }
    suspend fun screenshot(name: String) {
      delay(50); scene.render(System.nanoTime()).close(); delay(400)
      val image = scene.render(System.nanoTime())
      try { val data = assertNotNull(image.encodeToData()); try { File(System.getProperty("berill.uiEvidenceDir"), name).apply { parentFile.mkdirs(); writeBytes(data.bytes) } } finally { data.close() } } finally { image.close() }
    }
    try {
      screenshot("focus-editor.png")
      core.focusedNote.value = null; screenshot("focus-normal-editor.png")
      core.notes.save("n", "Focus", "External edit while the note remains open.")
      delay(100); core.focusedNote.value = "n"; screenshot("focus-editor-updated.png")
      assertTrue(core.onEscapeKey()); assertNull(core.focusedNote.value)
      core.notificationPreferences.set(core.notificationPreferences.state.value.copy(quietHours = true))
      core.nav.openSettings("notifications"); screenshot("notification-settings.png")
      assertEquals("External edit while the note remains open.", core.notes.get("n")!!.body)
      assertEquals("Keep this note intact.", core.notes.get("other")!!.body)
    } finally { scene.close(); scope.cancel(); driver.close() }
  }
}
