package dev.akkraizen.berill

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.AnnotatedString
import dev.akkraizen.berill.api.ui.UiText
import dev.akkraizen.berill.core.BerillCore
import dev.akkraizen.berill.core.db.BerillDatabase
import dev.akkraizen.berill.core.ui.shell.BerillShell
import dev.akkraizen.berill.plugins.books.BooksPlugin
import dev.akkraizen.berill.plugins.books.data.Book
import dev.akkraizen.berill.plugins.books.data.BookStatus
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

@OptIn(ExperimentalComposeUiApi::class)
class KanbanDragRenderTest {
  private suspend fun settle(scene: ImageComposeScene) = repeat(12) { delay(40); scene.render(System.nanoTime()).close() }

  private fun save(scene: ImageComposeScene, file: String) {
    val image = scene.render(System.nanoTime())
    try {
      val data = image.encodeToData()!!
      try { File(System.getProperty("berill.uiEvidenceDir"), file).apply { parentFile.mkdirs(); writeBytes(data.bytes) } } finally { data.close() }
    } finally { image.close() }
  }

  private fun nodes(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::nodes)

  private fun all(scene: ImageComposeScene) = scene.semanticsOwners.flatMap { nodes(it.rootSemanticsNode) }

  private fun texts(scene: ImageComposeScene) = all(scene).flatMap { it.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } }

  @Test fun bookCardIsDraggedToAnotherColumnOnWideBoards() = runBlocking(Dispatchers.Main) {
    val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
    BerillDatabase.Schema.create(driver)
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    val core = BerillCore(driver, listOf(BooksPlugin), scope)
    core.finishOnboarding()
    core.appearance.setLanguage("ru")
    val books = core.plugins.context(BooksPlugin).data.collection(BooksPlugin.BOOKS, Book.serializer())
    books.save("b1", Book("Drag fixture", "Author"))
    core.nav.openPlugin(BooksPlugin.id)
    val scene = ImageComposeScene(1280, 900) {
      MaterialTheme(colorScheme = darkColorScheme()) { Surface(Modifier.fillMaxSize()) { BerillShell(core) } }
    }
    try {
      settle(scene)
      assertTrue(texts(scene).any { "Drag fixture" in it }, texts(scene).toString())
      val start = all(scene).first { node -> node.config.getOrNull(SemanticsProperties.Text).orEmpty().any { it.text == "Author" } }.boundsInRoot.center
      scene.sendPointerEvent(PointerEventType.Move, start)
      scene.sendPointerEvent(PointerEventType.Press, start)
      val column = all(scene).first { node -> node.config.getOrNull(SemanticsProperties.Text).orEmpty().any { it.text == "Читаю" } }.boundsInRoot
      val end = Offset(column.center.x, start.y + 20f)
      for (step in 1..16) {
        scene.sendPointerEvent(PointerEventType.Move, Offset(start.x + (end.x - start.x) * step / 16, start.y + (end.y - start.y) * step / 16))
        scene.render(System.nanoTime()).close()
      }
      save(scene, "books-drag-in-progress.png")
      scene.sendPointerEvent(PointerEventType.Release, end)
      settle(scene)
      save(scene, "books-drag-done.png")
      assertEquals(BookStatus.READING, books.get("b1")!!.normalizedStatus)
    } finally {
      scene.close()
      scope.cancel()
    }
  }

  @Test fun homeEditorReordersAndHidesCardsInPlace() = runBlocking(Dispatchers.Main) {
    val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
    BerillDatabase.Schema.create(driver)
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    val core = BerillCore(driver, listOf(BooksPlugin), scope)
    core.finishOnboarding()
    core.appearance.setLanguage("ru")
    core.nav.openSettings("home")
    val scene = ImageComposeScene(1280, 900) {
      MaterialTheme(colorScheme = darkColorScheme()) { Surface(Modifier.fillMaxSize()) { BerillShell(core) } }
    }
    try {
      settle(scene)
      save(scene, "home-editor.png")
      val notes = all(scene).first { node -> node.config.getOrNull(SemanticsProperties.Text).orEmpty().any { it.text == "Последние заметки" } }.boundsInRoot.center
      val reading = all(scene).first { node -> node.config.getOrNull(SemanticsProperties.Text).orEmpty().any { it.text == "Сейчас читаю" } }.boundsInRoot
      scene.sendPointerEvent(PointerEventType.Move, notes)
      scene.sendPointerEvent(PointerEventType.Press, notes)
      val target = Offset(reading.center.x, reading.center.y + 20f)
      for (step in 1..16) {
        scene.sendPointerEvent(PointerEventType.Move, Offset(notes.x + (target.x - notes.x) * step / 16, notes.y + (target.y - notes.y) * step / 16))
        scene.render(System.nanoTime()).close()
      }
      scene.sendPointerEvent(PointerEventType.Release, target)
      settle(scene)
      save(scene, "home-editor-after-drag.png")
      val order = core.homePreferences.state.value.cardOrder
      assertTrue(order.indexOf("core/notes") > order.indexOf("books/reading"), order.toString())
      val hide = all(scene).first { node -> node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().contains(UiText.hide()) }.boundsInRoot.center
      scene.sendPointerEvent(PointerEventType.Move, hide)
      scene.sendPointerEvent(PointerEventType.Press, hide)
      scene.sendPointerEvent(PointerEventType.Release, hide)
      settle(scene)
      save(scene, "home-editor-hidden.png")
      assertTrue(!core.homePreferences.state.value.greeting || core.homePreferences.state.value.hiddenCards.isNotEmpty())
    } finally {
      scene.close()
      scope.cancel()
    }
  }

  @Test fun programDaysCollapse() = runBlocking(Dispatchers.Main) {
    val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
    BerillDatabase.Schema.create(driver)
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    val core = BerillCore(driver, listOf(dev.akkraizen.berill.plugins.workouts.WorkoutsPlugin), scope)
    core.finishOnboarding()
    core.appearance.setLanguage("ru")
    val plugin = dev.akkraizen.berill.plugins.workouts.WorkoutsPlugin
    val ctx = core.plugins.context(plugin)
    ctx.data.collection(plugin.PROGRAMS, dev.akkraizen.berill.plugins.workouts.data.Program.serializer()).save("p",
      dev.akkraizen.berill.plugins.workouts.data.Program("Fixture program", listOf(
        dev.akkraizen.berill.plugins.workouts.data.WorkoutDay("Day A", List(5) { dev.akkraizen.berill.plugins.workouts.data.Exercise("Squat $it") }),
        dev.akkraizen.berill.plugins.workouts.data.WorkoutDay("Day B", List(4) { dev.akkraizen.berill.plugins.workouts.data.Exercise("Press $it") }))))
    core.nav.openPlugin(plugin.id, page = plugin.PAGE_PROGRAM)
    val scene = ImageComposeScene(900, 900) {
      MaterialTheme(colorScheme = darkColorScheme()) { Surface(Modifier.fillMaxSize()) { BerillShell(core) } }
    }
    try {
      settle(scene)
      val day = all(scene).first { node -> node.config.getOrNull(SemanticsProperties.Text).orEmpty().any { it.text == "Day A" } }.boundsInRoot.center
      scene.sendPointerEvent(PointerEventType.Move, day)
      scene.sendPointerEvent(PointerEventType.Press, day)
      scene.sendPointerEvent(PointerEventType.Release, day)
      settle(scene)
      save(scene, "program-day-collapsed.png")
      assertTrue(texts(scene).none { it == "Squat 0" }, texts(scene).toString())
      assertTrue(texts(scene).any { it == "Press 0 3 × 8" || it.startsWith("Press 0") }, texts(scene).toString())
    } finally {
      scene.close()
      scope.cancel()
    }
  }

  private fun click(scene: ImageComposeScene, text: String) {
    val at = all(scene).last { node -> node.config.getOrNull(SemanticsProperties.Text).orEmpty().any { it.text == text } }.boundsInRoot.center
    scene.sendPointerEvent(PointerEventType.Move, at)
    val target = all(scene).filter { it.config.getOrNull(SemanticsActions.OnClick) != null }.filter { it.boundsInRoot.contains(at) }.minBy { it.boundsInRoot.width * it.boundsInRoot.height }
    target.config[SemanticsActions.OnClick].action!!.invoke()
  }

  @Test fun healthQuickActionsOpenPopupsOnHome() = runBlocking(Dispatchers.Main) {
    val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
    BerillDatabase.Schema.create(driver)
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    val plugin = dev.akkraizen.berill.plugins.health.HealthPlugin
    val core = BerillCore(driver, listOf(plugin), scope)
    core.finishOnboarding()
    core.appearance.setLanguage("ru")
    val before = core.nav.current
    val scene = ImageComposeScene(1280, 900) {
      MaterialTheme(colorScheme = darkColorScheme()) { Surface(Modifier.fillMaxSize()) { BerillShell(core) } }
    }
    try {
      settle(scene)
      click(scene, "Записать показатель")
      settle(scene)
      save(scene, "health-quick-vital.png")
      val label = all(scene).last { node -> node.config.getOrNull(SemanticsProperties.Text).orEmpty().any { it.text == "уд/мин" } }.boundsInRoot.center
      val field = all(scene).filter { it.config.getOrNull(SemanticsActions.SetText) != null }.first { it.boundsInRoot.contains(label) }
      field.config[SemanticsActions.SetText].action!!.invoke(AnnotatedString("72"))
      settle(scene)
      save(scene, "health-quick-vital-filled.png")
      click(scene, "Сохранить")
      settle(scene)
      save(scene, "health-quick-vital-saved.png")
      assertEquals(1, core.data.raw(plugin.VITALS).all().size)
      assertEquals(before, core.nav.current)
      click(scene, "Самочувствие")
      settle(scene)
      save(scene, "health-quick-wellbeing.png")
      click(scene, "4")
      settle(scene)
      save(scene, "health-quick-wellbeing-filled.png")
      click(scene, "Сохранить")
      settle(scene)
      assertEquals(1, core.data.raw(plugin.WELLBEING).all().size)
      assertEquals(before, core.nav.current)
    } finally {
      scene.close()
      scope.cancel()
    }
  }
}
