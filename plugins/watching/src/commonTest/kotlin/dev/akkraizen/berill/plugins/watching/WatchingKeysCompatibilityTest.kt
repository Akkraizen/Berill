package dev.akkraizen.berill.plugins.watching
import dev.akkraizen.berill.plugins.watching.data.*
import dev.akkraizen.berill.plugins.watching.i18n.pluginI18n
import kotlinx.serialization.json.*
import kotlin.test.*
class WatchingKeysCompatibilityTest {
 @Test fun preservesLegacyWireValues() {
  for ((v, legacy) in mapOf(WatchStatus.PLANNED to "в планах", WatchStatus.WATCHING to "смотрю", WatchStatus.WATCHED to "просмотрено")) {
   val old = JsonPrimitive(legacy)
   assertEquals(v, Json.decodeFromJsonElement(WatchStatusSerializer, old))
   assertEquals(old, Json.encodeToJsonElement(WatchStatusSerializer, v))
  }
  for ((v, legacy) in mapOf(MediaKind.ANIME to "аниме", MediaKind.MOVIE to "фильм", MediaKind.SERIES to "сериал")) {
   val old = JsonPrimitive(legacy)
   assertEquals(v, Json.decodeFromJsonElement(MediaKindSerializer, old))
   assertEquals(old, Json.encodeToJsonElement(MediaKindSerializer, v))
  }
 }
}
