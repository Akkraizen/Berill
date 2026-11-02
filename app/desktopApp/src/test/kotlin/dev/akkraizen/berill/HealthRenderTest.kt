package dev.akkraizen.berill

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import dev.akkraizen.berill.api.ui.today
import dev.akkraizen.berill.core.BerillCore
import dev.akkraizen.berill.core.db.BerillDatabase
import dev.akkraizen.berill.plugins.health.HealthPlugin
import dev.akkraizen.berill.plugins.health.data.ActivityDay
import dev.akkraizen.berill.plugins.health.data.Intake
import dev.akkraizen.berill.plugins.health.data.IntakeStatus
import dev.akkraizen.berill.plugins.health.data.Medication
import dev.akkraizen.berill.plugins.health.data.MedicationKind
import dev.akkraizen.berill.plugins.health.data.SleepEntry
import dev.akkraizen.berill.plugins.health.data.intakeId
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

@OptIn(ExperimentalComposeUiApi::class)
class HealthRenderTest {
  private suspend fun render(scene: ImageComposeScene, file: String) {
    repeat(12) { delay(40); scene.render(System.nanoTime()).close() }
    val image = scene.render(System.nanoTime())
    try {
      val data = image.encodeToData()!!
      try { File(System.getProperty("berill.uiEvidenceDir"), file).apply { parentFile.mkdirs(); writeBytes(data.bytes) } } finally { data.close() }
    } finally { image.close() }
  }

  private fun nodes(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::nodes)

  private fun text(scene: ImageComposeScene) = scene.semanticsOwners.flatMap { nodes(it.rootSemanticsNode) }
    .flatMap { it.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } }

  @Test fun doseStylesSettingsAndSleepRender() = runBlocking(Dispatchers.Main) {
    for ((style, dark) in listOf("CARDS" to false, "TIMELINE" to true, "CHIPS" to false)) {
      val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
      BerillDatabase.Schema.create(driver)
      val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
      val core = BerillCore(driver, listOf(HealthPlugin), scope)
      core.appearance.setLanguage("ru")
      val ctx = core.plugins.context(HealthPlugin)
      val day = today().toString()
      val meds = ctx.data.collection(HealthPlugin.MEDICATIONS, Medication.serializer())
      meds.save("d", Medication("Витамин D", MedicationKind.SUPPLEMENT, "2000 МЕ", listOf("00:01")))
      meds.save("m", Medication("Магний", MedicationKind.SUPPLEMENT, "400 мг", listOf("00:02", "23:58")))
      meds.save("p", Medication("Парацетамол", dose = "500 мг", times = listOf("23:59")))
      meds.save("o", Medication("Омега-3", MedicationKind.SUPPLEMENT))
      ctx.data.collection(HealthPlugin.INTAKES, Intake.serializer())
        .save(intakeId("d", day, "00:01"), Intake("d", day, "00:01", IntakeStatus.TAKEN, System.currentTimeMillis()))
      ctx.data.collection(HealthPlugin.ACTIVITY, ActivityDay.serializer()).save(day, ActivityDay(day, 6420, 450, "health_connect"))
      ctx.data.collection(HealthPlugin.SLEEP, SleepEntry.serializer()).save(day, SleepEntry(day, "23:30", "07:30", 4))
      ctx.settings.set("prefs", buildJsonObject { put("doseStyle", JsonPrimitive(style)) })
      val pages = listOf(HealthPlugin.PAGE_TODAY, HealthPlugin.PAGE_SLEEP).map { id -> HealthPlugin.pages.first { it.id == id } }
      for (page in pages) {
        val scene = ImageComposeScene(420, 1100) {
          MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
            Surface(Modifier.fillMaxSize()) { page.content(ctx) }
          }
        }
        try {
          render(scene, "health-${page.id}-${style.lowercase()}.png")
          val shown = text(scene)
          if (page.id == HealthPlugin.PAGE_TODAY) assertTrue(shown.any { "Магний" in it } && shown.any { "Принято 1 из 5" in it }, shown.toString())
          else assertTrue(shown.any { "8 ч" in it }, shown.toString())
        } finally { scene.close() }
      }
      val settings = ImageComposeScene(420, 900) {
        MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
          Surface(Modifier.fillMaxSize()) { HealthPlugin.Settings(ctx) }
        }
      }
      try {
        render(settings, "health-settings-${style.lowercase()}.png")
        assertTrue(text(settings).any { "Отметка приёма" in it })
      } finally { settings.close() }
      scope.cancel()
    }
  }
}
