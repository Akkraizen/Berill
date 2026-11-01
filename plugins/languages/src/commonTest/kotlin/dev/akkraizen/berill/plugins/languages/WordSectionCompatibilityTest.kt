package dev.akkraizen.berill.plugins.languages
import dev.akkraizen.berill.plugins.languages.data.*
import dev.akkraizen.berill.plugins.languages.i18n.pluginI18n
import kotlinx.serialization.json.*
import kotlin.test.*
class WordSectionCompatibilityTest {
 @Test fun preservesLegacyWireValues() { for ((v, legacy) in mapOf(WordSection.WORDS to "Слова", WordSection.PHRASES to "Фразы")) {
  val old = JsonPrimitive(legacy)
  assertEquals(v, Json.decodeFromJsonElement(WordSectionSerializer, old))
  assertEquals(old, Json.encodeToJsonElement(WordSectionSerializer, v))
 } }
}
