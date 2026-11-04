package dev.akkraizen.berill

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import dev.akkraizen.berill.api.ui.today
import dev.akkraizen.berill.core.BerillCore
import dev.akkraizen.berill.core.db.BerillDatabase
import dev.akkraizen.berill.core.ui.shell.BerillShell
import dev.akkraizen.berill.plugins.books.BooksPlugin
import dev.akkraizen.berill.plugins.books.data.Book
import dev.akkraizen.berill.plugins.books.data.BookStatus
import dev.akkraizen.berill.plugins.health.HealthPlugin
import dev.akkraizen.berill.plugins.health.data.ActivityDay
import dev.akkraizen.berill.plugins.health.data.Medication
import dev.akkraizen.berill.plugins.health.data.WellbeingDay
import dev.akkraizen.berill.plugins.projects.ProjectsPlugin
import dev.akkraizen.berill.plugins.projects.data.Project
import dev.akkraizen.berill.plugins.projects.data.ProjectTask
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
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.minus

@OptIn(ExperimentalComposeUiApi::class)
class DailyFeaturesRenderTest {
  private suspend fun settle(scene: ImageComposeScene) = repeat(14) { delay(40); scene.render(System.nanoTime()).close() }

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

  private fun clickAt(scene: ImageComposeScene, predicate: (SemanticsNode) -> Boolean) {
    val at = all(scene).last(predicate).boundsInRoot.center
    scene.sendPointerEvent(PointerEventType.Move, at)
    val target = all(scene).filter { it.config.getOrNull(SemanticsActions.OnClick) != null }.filter { it.boundsInRoot.contains(at) }.minBy { it.boundsInRoot.width * it.boundsInRoot.height }
    target.config[SemanticsActions.OnClick].action!!.invoke()
  }

  private fun tap(scene: ImageComposeScene, text: String) {
    val at = all(scene).last { node -> node.config.getOrNull(SemanticsProperties.Text).orEmpty().any { it.text == text } }.boundsInRoot.center
    scene.sendPointerEvent(PointerEventType.Move, at)
    scene.sendPointerEvent(PointerEventType.Press, at)
    scene.sendPointerEvent(PointerEventType.Release, at)
  }

  private fun click(scene: ImageComposeScene, text: String) =
    clickAt(scene) { node -> node.config.getOrNull(SemanticsProperties.Text).orEmpty().any { it.text == text } }

  private fun clickDescribed(scene: ImageComposeScene, description: String) =
    clickAt(scene) { node -> node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().any { it == description } }

  private fun withCore(plugins: List<dev.akkraizen.berill.api.BerillPlugin>, block: suspend (BerillCore) -> Unit) = runBlocking(Dispatchers.Main) {
    val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
    BerillDatabase.Schema.create(driver)
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    val core = BerillCore(driver, plugins, scope)
    core.finishOnboarding()
    core.appearance.setLanguage("ru")
    try { block(core) } finally { scope.cancel(); driver.close() }
  }

  private fun scene(core: BerillCore) = ImageComposeScene(1280, 900) {
    MaterialTheme(colorScheme = darkColorScheme()) { Surface(Modifier.fillMaxSize()) { BerillShell(core) } }
  }

  @Test fun notesHavePinsColorsTagsAndTemplates(): Unit = withCore(emptyList()) { core ->
    core.notes.save("a", "Список покупок", "молоко, хлеб #дом")
    core.notes.save("b", "Конспект по сетям", "модель OSI #учёба #сети")
    core.notes.save("c", "Идеи для Berill", "лента сегодня #проекты")
    core.notes.save("d", "Цитата", "Мысль дня")
    core.notes.setPinned("b", true)
    core.notes.setColor("b", "blue")
    core.notes.setColor("a", "yellow")
    core.nav.selectTab(dev.akkraizen.berill.core.nav.Tab.Notes)
    val scene = scene(core)
    try {
      settle(scene)
      val shown = texts(scene)
      assertTrue(shown.any { it == "Закреплённые" } && shown.any { it == "#учёба" }, shown.toString())
      save(scene, "notes-organized.png")
      click(scene, "#дом")
      settle(scene)
      assertTrue(texts(scene).none { it == "Цитата" }, texts(scene).toString())
      click(scene, "Все")
      clickDescribed(scene, "Шаблоны")
      settle(scene)
      assertTrue(texts(scene).any { it == "Конспект лекции" }, texts(scene).toString())
      save(scene, "notes-templates.png")
      click(scene, "Конспект лекции")
      settle(scene)
      val created = core.notes.all().single { it.value.title.startsWith("Конспект лекции") }
      assertTrue("#конспект" in created.value.body, created.value.body)
    } finally { scene.close() }
  }

  @Test fun homeShowsTheTodayFeed(): Unit = withCore(listOf(HealthPlugin, ProjectsPlugin)) { core ->
    core.data.collection(HealthPlugin.MEDICATIONS, Medication.serializer()).save("vit", Medication("Витамин D", times = listOf("09:00")))
    core.data.collection(ProjectsPlugin.PROJECTS, Project.serializer()).save("p", Project("Berill"))
    core.data.collection(ProjectsPlugin.TASKS, ProjectTask.serializer()).save("t", ProjectTask("p", "Выпустить 1.4.0", due = today().minus(1, DateTimeUnit.DAY).toString()))
    val scene = scene(core)
    try {
      settle(scene)
      val shown = texts(scene)
      assertTrue(shown.any { it == "Сегодня" } && shown.any { it == "Витамин D" } && shown.any { it == "Выпустить 1.4.0" }, shown.toString())
      assertTrue(shown.any { it == "просрочено" }, shown.toString())
      save(scene, "home-today.png")
    } finally { scene.close() }
  }

  @Test fun wellbeingStatisticsShowPatterns(): Unit = withCore(listOf(HealthPlugin)) { core ->
    val moods = core.data.collection(HealthPlugin.WELLBEING, WellbeingDay.serializer())
    val activity = core.data.collection(HealthPlugin.ACTIVITY, ActivityDay.serializer())
    (1..20).forEach { offset ->
      val day = today().minus(offset, DateTimeUnit.DAY).toString()
      val rested = offset % 2 == 0
      moods.save(day, WellbeingDay(day, wellbeing = if (rested) 5 else 3, mood = 4, energy = if (rested) 5 else 2))
      activity.save(day, ActivityDay(day, steps = if (offset % 3 == 0) 9000L else 4000L, sleepMinutes = if (rested) 500L else 380L))
    }
    core.nav.openPlugin(HealthPlugin.id, page = HealthPlugin.PAGE_WELLBEING)
    val scene = ImageComposeScene(1280, 1500) {
      MaterialTheme(colorScheme = darkColorScheme()) { Surface(Modifier.fillMaxSize()) { BerillShell(core) } }
    }
    try {
      settle(scene)
      click(scene, "Статистика")
      settle(scene)
      val shown = texts(scene)
      assertTrue(shown.any { it == "Связи" } && shown.any { it.startsWith("В дни со сном от 8 ч энергия") }, shown.toString())
      save(scene, "health-insights.png")
    } finally { scene.close() }
  }

  @Test fun booksCanBeSelectedAndMovedTogether(): Unit = withCore(listOf(BooksPlugin)) { core ->
    val books = core.plugins.context(BooksPlugin).data.collection(BooksPlugin.BOOKS, Book.serializer())
    books.save("b1", Book("Мастер и Маргарита", "Булгаков"))
    books.save("b2", Book("Пикник на обочине", "Стругацкие"))
    books.save("b3", Book("Солярис", "Лем"))
    core.plugins.context(BooksPlugin).settings.set("listLayout", kotlinx.serialization.json.JsonPrimitive("ROWS"))
    core.nav.openPlugin(BooksPlugin.id)
    val scene = scene(core)
    try {
      settle(scene)
      clickDescribed(scene, "Вид списка")
      settle(scene)
      click(scene, "Выбрать несколько")
      settle(scene)
      tap(scene, "Мастер и Маргарита")
      settle(scene)
      tap(scene, "Лем")
      settle(scene)
      assertTrue(texts(scene).any { it == "Выбрано: 2" }, texts(scene).toString())
      save(scene, "books-selection.png")
      clickDescribed(scene, "Сменить статус")
      settle(scene)
      click(scene, "Прочитано")
      settle(scene)
      assertEquals(listOf(BookStatus.DONE, BookStatus.NOT_STARTED, BookStatus.DONE), listOf("b1", "b2", "b3").map { books.get(it)!!.normalizedStatus })
    } finally { scene.close() }
  }
}
