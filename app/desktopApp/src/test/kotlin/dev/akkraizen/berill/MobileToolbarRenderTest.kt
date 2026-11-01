package dev.akkraizen.berill

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.semantics.*
import dev.akkraizen.berill.api.i18n.I18n
import dev.akkraizen.berill.core.BerillCore
import dev.akkraizen.berill.core.db.BerillDatabase
import dev.akkraizen.berill.core.nav.Destination
import dev.akkraizen.berill.core.notifications.*
import dev.akkraizen.berill.core.ui.shell.BerillShell
import kotlinx.coroutines.*
import java.io.File
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class)
class MobileToolbarRenderTest {
  @Test fun phoneTitlesFitAndOverflowOpensWithTouch() = runBlocking(Dispatchers.Main) {
    for (width in listOf(320, 360, 412)) {
      val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
      BerillDatabase.Schema.create(driver)
      val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
      val core = BerillCore(driver, emptyList(), scope)
      core.appearance.setLanguage("ru")
      core.finishOnboarding(); core.nav.openSettings("sync")
      core.notifications.publish(Notice("update", NoticeKind.UPDATE, at = System.currentTimeMillis()))
      val scene = ImageComposeScene(width, 800) { BerillShell(core) }
      suspend fun render(file: String) {
        repeat(10) { delay(40); scene.render(System.nanoTime()).close() }
        val image = scene.render(System.nanoTime())
        try { val data = assertNotNull(image.encodeToData()); try { File(System.getProperty("berill.uiEvidenceDir"), file).apply { parentFile.mkdirs(); writeBytes(data.bytes) } } finally { data.close() } } finally { image.close() }
      }
      fun descendants(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::descendants)
      fun nodes() = scene.semanticsOwners.flatMap { descendants(it.rootSemanticsNode) }
      fun text(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }
      try {
        render("mobile-toolbar-$width.png")
        val menu = nodes().first { n -> n.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().any { it.startsWith(I18n.text("core.menu")) } && n.config.getOrNull(SemanticsActions.OnClick) != null }
        val search = nodes().first { n -> n.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().contains(I18n.text("connections.searchTitle")) && n.config.getOrNull(SemanticsActions.OnClick) != null }
        val title = nodes().first { I18n.text("core.sync") in text(it) && it.boundsInRoot.top < 80 }
        assertTrue(title.boundsInRoot.width > 120, "Long phone title must have enough room")
        assertTrue(title.boundsInRoot.right <= search.boundsInRoot.left)
        assertTrue(search.boundsInRoot.right <= menu.boundsInRoot.left)
        val center = menu.boundsInRoot.center
        scene.sendPointerEvent(PointerEventType.Press, center, type = PointerType.Touch)
        delay(30)
        scene.sendPointerEvent(PointerEventType.Release, center, type = PointerType.Touch)
        render("mobile-menu-$width.png")
        val items = nodes()
        for (key in listOf("center.title", "connections.day", "connections.trash", "core.sync")) assertTrue(items.any { I18n.text(key) in text(it) }, "Missing overflow action: $key")
        val syncItem = items.last { I18n.text("core.sync") in text(it) && it.config.getOrNull(SemanticsActions.OnClick) != null }
        assertTrue(assertNotNull(syncItem.config.getOrNull(SemanticsActions.OnClick)?.action).invoke())
        render("mobile-menu-dismissed-$width.png")
        assertEquals(Destination.SettingsSection("sync"), core.nav.current)
        assertFalse(nodes().any { I18n.text("connections.trash") in text(it) })
      } finally { scene.close(); scope.cancel(); driver.close() }
    }
  }
}
