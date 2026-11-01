package dev.akkraizen.berill.plugins.books
import dev.akkraizen.berill.plugins.books.data.*
import dev.akkraizen.berill.plugins.books.i18n.pluginI18n
import dev.akkraizen.berill.api.i18n.I18n
import dev.akkraizen.berill.api.i18n.AppLanguage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.*
class BookStatusCompatibilityTest {
 @Test fun legacyValuesRoundTripInBothLanguages() {
  val previous = I18n.language
  try { for (language in listOf("en", "ru")) {
   I18n.language = AppLanguage.fromCode(language)!!
   for ((status, legacy) in mapOf(BookStatus.NOT_STARTED to "не начато", BookStatus.READING to "в процессе чтения", BookStatus.DONE to "прочитано")) {
    val old = JsonPrimitive(legacy)
    assertEquals(status, Json.decodeFromJsonElement(BookStatusSerializer, old))
    assertEquals(old, Json.encodeToJsonElement(BookStatusSerializer, status))
   }
  } } finally { I18n.language = previous }
 }
}
