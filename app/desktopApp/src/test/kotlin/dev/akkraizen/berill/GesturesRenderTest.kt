package dev.akkraizen.berill

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import dev.akkraizen.berill.api.ui.today
import dev.akkraizen.berill.core.BerillCore
import dev.akkraizen.berill.core.db.BerillDatabase
import dev.akkraizen.berill.core.nav.Destination
import dev.akkraizen.berill.core.ui.shell.BerillShell
import dev.akkraizen.berill.plugins.health.HealthPlugin
import dev.akkraizen.berill.plugins.health.data.Medication
import dev.akkraizen.berill.plugins.projects.ProjectsPlugin
import dev.akkraizen.berill.plugins.projects.data.Project
import dev.akkraizen.berill.plugins.projects.data.ProjectTask
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

@OptIn(ExperimentalComposeUiApi::class)
class GesturesRenderTest {
  private var clock = 1_000L

  private suspend fun settle(scene: ImageComposeScene) = repeat(16) { delay(40); scene.render(System.nanoTime()).close() }

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
  private fun center(scene: ImageComposeScene, text: String) =
    all(scene).last { node -> node.config.getOrNull(SemanticsProperties.Text).orEmpty().any { it.text == text } }.boundsInRoot.center

  private suspend fun swipe(scene: ImageComposeScene, from: Offset, dx: Float, steps: Int = 12) {
    scene.sendPointerEvent(PointerEventType.Press, from, timeMillis = clock, type = PointerType.Touch)
    for (step in 1..steps) {
      clock += 16
      scene.sendPointerEvent(PointerEventType.Move, Offset(from.x + dx * step / steps, from.y + step), timeMillis = clock, type = PointerType.Touch)
      scene.render(System.nanoTime()).close()
    }
    clock += 16
    scene.sendPointerEvent(PointerEventType.Release, Offset(from.x + dx, from.y + steps), timeMillis = clock, type = PointerType.Touch)
    clock += 1_000
    settle(scene)
  }

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

  private fun page(core: BerillCore) = (core.nav.current as? Destination.Plugin)?.page

  @Test fun swipesSwitchTabsThenPages(): Unit = withCore(listOf(HealthPlugin)) { core ->
    core.nav.openSection(HealthPlugin.id, HealthPlugin.PAGE_WELLBEING)
    val scene = scene(core)
    try {
      settle(scene)
      val content = Offset(900f, center(scene, "Симптомы").y)
      swipe(scene, content, 40f)
      assertTrue(texts(scene).none { it == "Связи" }, "short drags are not swipes")
      swipe(scene, content, -320f)
      assertTrue(texts(scene).any { it == "Связи" }, texts(scene).toString())
      assertEquals(HealthPlugin.PAGE_WELLBEING, page(core))
      save(scene, "gesture-statistics.png")
      swipe(scene, content, -320f)
      assertEquals(HealthPlugin.PAGE_SLEEP, page(core))
      swipe(scene, content, 320f)
      assertEquals(HealthPlugin.PAGE_WELLBEING, page(core))
    } finally { scene.close() }
  }

  @Test fun trackpadScrollSwitchesTabs(): Unit = withCore(listOf(HealthPlugin)) { core ->
    core.nav.openSection(HealthPlugin.id, HealthPlugin.PAGE_WELLBEING)
    val scene = scene(core)
    try {
      settle(scene)
      repeat(4) {
        clock += 20
        scene.sendPointerEvent(PointerEventType.Scroll, Offset(900f, center(scene, "Симптомы").y), scrollDelta = Offset(1.5f, 0f), timeMillis = clock)
      }
      settle(scene)
      assertTrue(texts(scene).any { it == "Связи" }, texts(scene).toString())
    } finally { scene.close() }
  }

  @Test fun rowsAreSwipedToCompleteOrDelete(): Unit = withCore(listOf(HealthPlugin, ProjectsPlugin)) { core ->
    core.data.collection(HealthPlugin.MEDICATIONS, Medication.serializer()).save("vit", Medication("Витамин D", times = listOf("09:00")))
    core.data.collection(ProjectsPlugin.PROJECTS, Project.serializer()).save("p", Project("Berill"))
    val tasks = core.data.collection(ProjectsPlugin.TASKS, ProjectTask.serializer())
    tasks.save("t1", ProjectTask("p", "Написать тесты", due = today().toString()))
    tasks.save("t2", ProjectTask("p", "Лишняя задача"))
    val scene = scene(core)
    try {
      settle(scene)
      val dose = all(scene).first { node -> node.config.getOrNull(SemanticsProperties.Text).orEmpty().any { it.text == "Витамин D" } }.boundsInRoot.center
      swipe(scene, dose, 260f)
      val intake = core.data.raw(HealthPlugin.INTAKES).all()
      assertEquals(1, intake.size, intake.toString())
      core.nav.openPlugin(ProjectsPlugin.id, "project:p")
      settle(scene)
      val row = center(scene, "Лишняя задача")
      scene.sendPointerEvent(PointerEventType.Press, row, timeMillis = clock, type = PointerType.Touch)
      for (step in 1..8) {
        clock += 16
        scene.sendPointerEvent(PointerEventType.Move, Offset(row.x - 30f * step, row.y), timeMillis = clock, type = PointerType.Touch)
        scene.render(System.nanoTime()).close()
      }
      save(scene, "gesture-row-delete.png")
      clock += 16
      scene.sendPointerEvent(PointerEventType.Release, Offset(row.x - 240f, row.y), timeMillis = clock, type = PointerType.Touch)
      settle(scene)
      assertNull(tasks.get("t2"))
      swipe(scene, center(scene, "Написать тесты"), 260f)
      assertTrue(tasks.get("t1")!!.done)
    } finally { scene.close() }
  }
}
