package dev.akkraizen.berill.plugins.projects
import dev.akkraizen.berill.plugins.projects.data.*
import dev.akkraizen.berill.plugins.projects.i18n.pluginI18n
import kotlinx.serialization.json.*
import kotlin.test.*
class ProjectStatusCompatibilityTest {
 @Test fun preservesLegacyWireValues() { for ((status, legacy) in mapOf(ProjectStatus.IDEA to "идея", ProjectStatus.IN_PROGRESS to "в работе", ProjectStatus.PAUSED to "на паузе", ProjectStatus.READY to "готово")) {
  val old = JsonPrimitive(legacy)
  assertEquals(status, Json.decodeFromJsonElement(ProjectStatusSerializer, old))
  assertEquals(old, Json.encodeToJsonElement(ProjectStatusSerializer, status))
 } }
}
