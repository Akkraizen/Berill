package dev.akkraizen.berill

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import dev.akkraizen.berill.api.ui.today
import dev.akkraizen.berill.core.BerillCore
import dev.akkraizen.berill.core.db.BerillDatabase
import dev.akkraizen.berill.plugins.projects.ProjectsPlugin
import dev.akkraizen.berill.plugins.projects.data.Milestone
import dev.akkraizen.berill.plugins.projects.data.Project
import dev.akkraizen.berill.plugins.projects.data.ProjectStatus
import dev.akkraizen.berill.plugins.projects.data.ProjectTask
import dev.akkraizen.berill.plugins.projects.data.TaskPriority
import dev.akkraizen.berill.plugins.projects.data.TaskStatus
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.plus

@OptIn(ExperimentalComposeUiApi::class)
class ProjectsRenderTest {
  private suspend fun settle(scene: ImageComposeScene) = repeat(12) { delay(40); scene.render(System.nanoTime()).close() }

  private fun save(scene: ImageComposeScene, file: String) {
    val image = scene.render(System.nanoTime())
    try {
      val data = image.encodeToData()!!
      try { File(System.getProperty("berill.uiEvidenceDir"), file).apply { parentFile.mkdirs(); writeBytes(data.bytes) } } finally { data.close() }
    } finally { image.close() }
  }

  private fun nodes(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::nodes)

  private fun texts(scene: ImageComposeScene) = scene.semanticsOwners.flatMap { nodes(it.rootSemanticsNode) }
    .flatMap { it.config.getOrNull(SemanticsProperties.Text).orEmpty().map { t -> t.text } }

  private fun click(scene: ImageComposeScene, text: String) {
    val all = scene.semanticsOwners.flatMap { nodes(it.rootSemanticsNode) }
    val at = all.last { node -> node.config.getOrNull(SemanticsProperties.Text).orEmpty().any { it.text == text } }.boundsInRoot.center
    val target = all.filter { it.config.getOrNull(androidx.compose.ui.semantics.SemanticsActions.OnClick) != null }
      .filter { it.boundsInRoot.contains(at) }.minBy { it.boundsInRoot.width * it.boundsInRoot.height }
    target.config[androidx.compose.ui.semantics.SemanticsActions.OnClick].action!!.invoke()
  }

  @Test fun boardAndProjectShowTasksMilestonesAndTags() = runBlocking(Dispatchers.Main) {
    val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
    BerillDatabase.Schema.create(driver)
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    val core = BerillCore(driver, listOf(ProjectsPlugin), scope)
    core.finishOnboarding()
    core.appearance.setLanguage("ru")
    val ctx = core.plugins.context(ProjectsPlugin)
    val projects = ctx.data.collection(ProjectsPlugin.PROJECTS, Project.serializer())
    val tasks = ctx.data.collection(ProjectsPlugin.TASKS, ProjectTask.serializer())
    val steps = ctx.data.collection(ProjectsPlugin.MILESTONES, Milestone.serializer())
    val soon = today().plus(5, DateTimeUnit.DAY).toString()
    val late = today().plus(-2, DateTimeUnit.DAY).toString()
    projects.save("berill", Project("Berill", ProjectStatus.IN_PROGRESS, "Личное рабочее пространство", listOf("Kotlin", "Compose"), tags = listOf("личное", "kmp"), deadline = soon, tasksMigrated = true, body = "## Идея\nВсё в одном"))
    projects.save("site", Project("Сайт", ProjectStatus.IDEA, tags = listOf("веб"), tasksMigrated = true))
    projects.save("old", Project("Старый бот", ProjectStatus.READY, archived = true, tasksMigrated = true))
    core.notes.save("n1", "Архитектура Berill", "Модули и синхронизация")
    projects.save("berill", projects.get("berill")!!.copy(notes = listOf("n1")))
    steps.save("m1", Milestone("berill", "Релиз 1.4", due = soon))
    tasks.save("t1", ProjectTask("berill", "Задачи проектов", TaskStatus.DONE, completed = today().toString(), milestone = "m1"))
    tasks.save("t2", ProjectTask("berill", "Действия MCP", TaskStatus.DOING, TaskPriority.HIGH, due = late, milestone = "m1"))
    tasks.save("t3", ProjectTask("berill", "Обновить сайт", due = soon, order = 3))
    core.nav.openPlugin(ProjectsPlugin.id)
    val scene = ImageComposeScene(1280, 1000) {
      MaterialTheme(colorScheme = darkColorScheme()) { Surface(Modifier.fillMaxSize()) { dev.akkraizen.berill.core.ui.shell.BerillShell(core) } }
    }
    try {
      settle(scene)
      save(scene, "projects-board.png")
      val board = texts(scene)
      assertTrue(board.any { "#kmp" in it } && board.any { "1 из 3" in it } && board.none { it == "Старый бот" }, board.toString())
      core.nav.openPlugin(ProjectsPlugin.id, "project:berill")
      settle(scene)
      save(scene, "projects-detail.png")
      val detail = texts(scene)
      assertTrue(detail.any { it == "Действия MCP" } && detail.any { it == "Релиз 1.4" } && detail.any { it == "Архитектура Berill" }, detail.toString())
      assertTrue(detail.none { it == "Задачи проектов" }, "done tasks are hidden by the default filter")
      click(scene, "Действия MCP")
      settle(scene)
      save(scene, "projects-task-dialog.png")
      assertTrue(texts(scene).any { it == "Приоритет" }, texts(scene).toString())
      click(scene, "Отмена")
      settle(scene)
      steps.save("m0", Milestone("berill", "Прототип", due = late, done = true, completed = late))
      steps.save("m2", Milestone("berill", "Публикация", due = today().plus(20, DateTimeUnit.DAY).toString()))
      for ((style, layout) in listOf("TIMELINE" to "CARDS", "ROADMAP" to "ROWS", "CARDS" to "BOARD")) {
        ctx.settings.set("milestoneStyle", kotlinx.serialization.json.JsonPrimitive(style))
        ctx.settings.set("tasksLayout", kotlinx.serialization.json.JsonPrimitive(layout))
        core.nav.openPlugin(ProjectsPlugin.id, "project:berill")
        settle(scene)
        save(scene, "projects-milestones-${style.lowercase()}.png")
        assertTrue(texts(scene).any { it == "Публикация" }, style)
      }
      for (layout in listOf("CARDS", "ROWS")) {
        ctx.settings.set("boardLayout", kotlinx.serialization.json.JsonPrimitive(layout))
        core.nav.openPlugin(ProjectsPlugin.id)
        settle(scene)
        save(scene, "projects-board-${layout.lowercase()}.png")
        assertTrue(texts(scene).any { it == "Berill" }, layout)
      }
    } finally {
      scene.close()
      scope.cancel()
    }
  }
}
