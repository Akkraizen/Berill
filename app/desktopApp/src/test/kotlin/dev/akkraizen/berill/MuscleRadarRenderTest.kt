package dev.akkraizen.berill

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import dev.akkraizen.berill.api.i18n.I18n
import dev.akkraizen.berill.api.ui.today
import dev.akkraizen.berill.core.BerillCore
import dev.akkraizen.berill.core.db.BerillDatabase
import dev.akkraizen.berill.core.ui.shell.BerillShell
import dev.akkraizen.berill.core.update.*
import dev.akkraizen.berill.plugins.workouts.WorkoutsPlugin
import dev.akkraizen.berill.plugins.workouts.data.*
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class)
class MuscleRadarRenderTest {
  private suspend fun render(scene: ImageComposeScene, file: String) {
    repeat(12) { delay(40); scene.render(System.nanoTime()).close() }
    val image = scene.render(System.nanoTime())
    try { val data = image.encodeToData()!!; try { File(System.getProperty("berill.uiEvidenceDir"), file).apply { parentFile.mkdirs(); writeBytes(data.bytes) } } finally { data.close() } } finally { image.close() }
  }
  private fun nodes(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::nodes)
  private fun text(scene: ImageComposeScene) = scene.semanticsOwners.flatMap { nodes(it.rootSemanticsNode) }
    .flatMap { it.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } }

  @Test fun muscleRadarRendersOnPhoneAndDesktop() = runBlocking(Dispatchers.Main) {
    for ((width, dark) in listOf(320 to true, 400 to false, 900 to true)) {
      val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
      BerillDatabase.Schema.create(driver)
      val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
      val core = BerillCore(driver, listOf(WorkoutsPlugin), scope)
      core.appearance.setLanguage("ru")
      val ctx = core.plugins.context(WorkoutsPlugin)
      val catalog = ctx.data.collection(WorkoutsPlugin.EXERCISES, CustomExercise.serializer())
      catalog.save("press", CustomExercise("Press", MUSCLE_CHEST))
      catalog.save("rows", CustomExercise("Rows", MUSCLE_BACK))
      ctx.data.collection(WorkoutsPlugin.LOGS, WorkoutLog.serializer()).save("log", WorkoutLog(today().toString(), finished = true,
        exercises = listOf(LoggedExercise("Press", List(4) { LoggedSet(40.0, 10, true) }), LoggedExercise("Rows", List(6) { LoggedSet(30.0, 12, true) }))))
      val page = WorkoutsPlugin.pages.first { it.id == WorkoutsPlugin.PAGE_JOURNAL }
      val scene = ImageComposeScene(width, 1000) {
        MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
          Surface(Modifier.fillMaxSize()) { page.content(ctx) }
        }
      }
      try {
        render(scene, "muscle-radar-$width.png")
        val chart = scene.semanticsOwners.flatMap { nodes(it.rootSemanticsNode) }.firstOrNull {
          it.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().any { value -> value.contains("Грудь") && value.contains("Спина") }
        }
        assertNotNull(chart, "Radar must expose group totals to accessibility")
        assertTrue(chart.boundsInRoot.left >= 0 && chart.boundsInRoot.right <= width)
      } finally { scene.close(); scope.cancel(); driver.close() }
    }
  }

}
