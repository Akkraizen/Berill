package dev.akkraizen.berill.plugins.study
import dev.akkraizen.berill.plugins.study.data.*
import dev.akkraizen.berill.plugins.study.i18n.pluginI18n
import kotlinx.serialization.json.*
import kotlin.test.*
class StudyKeysCompatibilityTest {
 @Test fun preservesLegacyWireValues() { for ((v, legacy) in mapOf(TaskStatus.TODO to "не начато", TaskStatus.DOING to "в работе", TaskStatus.SUBMITTED to "сдано")) {
  val old = JsonPrimitive(legacy)
  assertEquals(v, Json.decodeFromJsonElement(TaskStatusSerializer, old))
  assertEquals(old, Json.encodeToJsonElement(TaskStatusSerializer, v))
 } }
 @Test fun retainsImportedControlRecognition() {
  assertTrue(Subject(control = "Экзамен").isExam)
  assertTrue(Subject(control = "Дифференцированный зачет").isCredit)
 }
}
