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
import kotlin.math.abs
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
      val target = Offset(reading.center.x, reading.center.y + 70f)
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

  @Test fun booksCanBeShownAsCardsAndRows() = runBlocking(Dispatchers.Main) {
    val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
    BerillDatabase.Schema.create(driver)
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    val core = BerillCore(driver, listOf(BooksPlugin), scope)
    core.finishOnboarding()
    core.appearance.setLanguage("ru")
    val ctx = core.plugins.context(BooksPlugin)
    val books = ctx.data.collection(BooksPlugin.BOOKS, Book.serializer())
    books.save("b1", Book("Мастер и Маргарита", "Булгаков", status = BookStatus.READING, rating = 9))
    books.save("b2", Book("Пикник на обочине", "Стругацкие"))
    books.save("b3", Book("Солярис", "Лем", status = BookStatus.DONE, rating = 8))
    core.nav.openPlugin(BooksPlugin.id)
    val scene = ImageComposeScene(1280, 900) {
      MaterialTheme(colorScheme = darkColorScheme()) { Surface(Modifier.fillMaxSize()) { BerillShell(core) } }
    }
    try {
      for (layout in listOf("CARDS", "ROWS")) {
        ctx.settings.set("listLayout", kotlinx.serialization.json.JsonPrimitive(layout))
        settle(scene)
        save(scene, "books-${layout.lowercase()}.png")
        assertTrue(texts(scene).any { it == "Солярис" } && texts(scene).any { it == "Читаю" }, texts(scene).toString())
      }
      ctx.settings.set("listLayout", kotlinx.serialization.json.JsonNull)
      core.displayPreferences.set(core.displayPreferences.state.value.copy(lists = dev.akkraizen.berill.api.ui.layout.ListLayout.ROWS))
      settle(scene)
      save(scene, "books-global-rows.png")
    } finally {
      scene.close()
      scope.cancel()
    }
  }

  @Test fun notesAreShownAsGrid() = runBlocking(Dispatchers.Main) {
    val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
    BerillDatabase.Schema.create(driver)
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    val core = BerillCore(driver, emptyList(), scope)
    core.finishOnboarding()
    core.appearance.setLanguage("ru")
    val bodies = listOf("Короткая", "Список покупок: молоко, хлеб, сыр, яблоки, кофе, чай и что-нибудь к чаю на выходные", "Идеи", "План недели: тренировки, учёба, проект, встреча с друзьями, уборка", "Цитата", "Конспект лекции по сетям связи: модель OSI, стек TCP/IP, маршрутизация и коммутация")
    bodies.forEachIndexed { i, body -> core.notes.save("n$i", "Заметка ${i + 1}", body) }
    core.nav.selectTab(dev.akkraizen.berill.core.nav.Tab.Notes)
    val scene = ImageComposeScene(1280, 900) {
      MaterialTheme(colorScheme = darkColorScheme()) { Surface(Modifier.fillMaxSize()) { BerillShell(core) } }
    }
    try {
      settle(scene)
      val cards = all(scene).filter { node -> node.config.getOrNull(SemanticsProperties.Text).orEmpty().any { it.text.startsWith("Заметка ") } }.map { it.boundsInRoot }
      assertEquals(6, cards.size, texts(scene).toString())
      assertTrue(cards.map { it.left }.distinct().size >= 2, cards.toString())
      val surfaces = all(scene).filter { node -> node.config.getOrNull(SemanticsActions.OnClick) != null && node.boundsInRoot.width < 300f && node.boundsInRoot.height > 100f }.map { it.boundsInRoot.height }
      assertEquals(6, surfaces.size, surfaces.toString())
      assertEquals(1, surfaces.distinct().size, surfaces.toString())
      save(scene, "notes-grid.png")
    } finally {
      scene.close()
      scope.cancel()
      driver.close()
    }
  }

  @Test fun lineChartConnectsAcrossMissingDays() = runBlocking(Dispatchers.Main) {
    val scene = ImageComposeScene(900, 520) {
      MaterialTheme(colorScheme = darkColorScheme()) {
        Surface(Modifier.fillMaxSize()) {
          dev.akkraizen.berill.api.ui.charts.StatisticsChart(
            (1..10).map { "$it" },
            listOf(
              dev.akkraizen.berill.api.ui.charts.ChartSeries("A", listOf(3.0, null, null, 4.0, 5.0, null, 2.0, null, null, 4.0)),
              dev.akkraizen.berill.api.ui.charts.ChartSeries("B", listOf(null, 2.0, null, null, 3.0, 4.0, null, null, 5.0, null))
            ),
            kind = dev.akkraizen.berill.api.ui.charts.ChartKind.Line,
            target = 5.0
          )
        }
      }
    }
    try {
      settle(scene)
      repeat(20) { delay(40); scene.render(System.nanoTime()).close() }
      save(scene, "chart-gaps.png")
    } finally {
      scene.close()
    }
  }

  @Test fun quickActionsKeepFollowingThePointerAfterTheyMove() = runBlocking(Dispatchers.Main) {
    val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
    BerillDatabase.Schema.create(driver)
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    val core = BerillCore(driver, listOf(dev.akkraizen.berill.plugins.health.HealthPlugin), scope)
    core.finishOnboarding()
    core.appearance.setLanguage("ru")
    core.nav.openSettings("home")
    val scene = ImageComposeScene(1280, 900) {
      MaterialTheme(colorScheme = darkColorScheme()) { Surface(Modifier.fillMaxSize()) { BerillShell(core) } }
    }
    fun chip(text: String) = all(scene).first { node -> node.config.getOrNull(SemanticsProperties.Text).orEmpty().any { it.text == text } }.boundsInRoot
    try {
      settle(scene)
      val start = chip("Записать показатель").center
      val first = chip("Новая заметка")
      scene.sendPointerEvent(PointerEventType.Move, start)
      scene.sendPointerEvent(PointerEventType.Press, start)
      val target = Offset(first.left + 10f, first.center.y)
      for (step in 1..24) {
        scene.sendPointerEvent(PointerEventType.Move, Offset(start.x + (target.x - start.x) * step / 24, start.y))
        scene.render(System.nanoTime()).close()
      }
      val dragged = chip("Записать показатель").center
      assertTrue(kotlin.math.abs(dragged.x - target.x) < 120f, "chip stopped at $dragged instead of following the pointer to $target")
      scene.sendPointerEvent(PointerEventType.Release, target)
      settle(scene)
      val order = core.homePreferences.state.value.actionOrder
      assertEquals("health/vitals", order.first(), order.toString())
      val settled = chip("Записать показатель")
      assertTrue(settled.left < chip("Новая заметка").left, "the dropped chip did not settle in its new place")
      save(scene, "home-actions-reordered.png")
    } finally {
      scene.close()
      scope.cancel()
    }
  }

  @Test fun homeCardsLandInTheGridCellTheyAreDroppedOn() = runBlocking(Dispatchers.Main) {
    val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
    BerillDatabase.Schema.create(driver)
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    val plugins = listOf(BooksPlugin, dev.akkraizen.berill.plugins.health.HealthPlugin, dev.akkraizen.berill.plugins.projects.ProjectsPlugin, dev.akkraizen.berill.plugins.workouts.WorkoutsPlugin)
    val core = BerillCore(driver, plugins, scope)
    core.finishOnboarding()
    core.appearance.setLanguage("ru")
    core.nav.openSettings("home")
    val titles = mapOf("core/notes" to "Последние заметки", "books/reading" to "Сейчас читаю", "health/today" to "Здоровье сегодня", "projects/active" to "Проекты в работе", "workouts/week" to "Тренировки")
    val scene = ImageComposeScene(1400, 1400) {
      MaterialTheme(colorScheme = darkColorScheme()) { Surface(Modifier.fillMaxSize()) { BerillShell(core) } }
    }
    fun bounds(text: String) = all(scene).first { node -> node.boundsInRoot.left > 590f && node.config.getOrNull(SemanticsProperties.Text).orEmpty().any { it.text == text } }.boundsInRoot
    fun layout(): List<List<String>> {
      val boxes = titles.mapValues { bounds(it.value) }
      val anchors = boxes.values.map { it.left }.sorted().fold(mutableListOf<Float>()) { acc, x -> if (acc.isEmpty() || x - acc.last() > 100f) acc += x; acc }
      return anchors.map { a -> boxes.filter { (_, b) -> abs(b.left - a) < 100f }.entries.sortedBy { it.value.top }.map { it.key } }
    }
    try {
      settle(scene)
      val before = layout()
      assertEquals(2, before.size, before.toString())
      val target = before.indices.first { before[it].size >= 2 }
      val other = 1 - target
      val moving = before[other].last()
      val (above, below) = before[target][0] to before[target][1]
      val from = bounds(titles.getValue(moving)).center
      scene.sendPointerEvent(PointerEventType.Move, from)
      scene.sendPointerEvent(PointerEventType.Press, from)
      val to = bounds(titles.getValue(below)).let { Offset(it.center.x, it.top + 12f) }
      for (step in 1..40) {
        scene.sendPointerEvent(PointerEventType.Move, Offset(from.x + (to.x - from.x) * step / 40, from.y + (to.y - from.y) * step / 40))
        repeat(2) { scene.render(System.nanoTime()).close() }
      }
      settle(scene)
      save(scene, "home-grid-dragging.png")
      scene.sendPointerEvent(PointerEventType.Release, to)
      settle(scene)
      save(scene, "home-grid-drop.png")
      val after = layout()
      val expectedTarget = before[target].toMutableList().apply { add(indexOf(below), moving) }
      assertEquals(expectedTarget, after[target], "$before -> $after")
      assertEquals(before[other] - moving, after[other], "$before -> $after")
      assertTrue(above in after[target])
    } finally {
      scene.close()
      scope.cancel()
    }
  }

}
