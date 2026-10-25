package dev.akkraizen.berill.core.sync

import dev.akkraizen.berill.protocol.Collections
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

data class SyncOptions(
  val auto: Boolean = true,
  val intervalMinutes: Int = 1,
  val appearance: Boolean = true,
  val sections: Boolean = true,
  val pluginSettings: Boolean = true,
  val excludedCollections: Set<String> = emptySet()
) {
  fun includes(collection: String, id: String): Boolean =
    when (collection) {
      Collections.SETTINGS ->
        when {
          id == APPEARANCE_RECORD -> appearance
          id == PLUGINS_RECORD || id == INSTALLED_RECORD -> sections
          id.startsWith(PLUGIN_SETTINGS_PREFIX) -> pluginSettings
          else -> true
        }

      else -> collection !in excludedCollections
    }

  fun widens(old: SyncOptions): Boolean =
    (appearance && !old.appearance) ||
      (sections && !old.sections) ||
      (pluginSettings && !old.pluginSettings) ||
      (old.excludedCollections - excludedCollections).isNotEmpty()

  fun encode(): String =
    buildJsonObject {
      put("auto", auto)
      put("interval", intervalMinutes)
      put("appearance", appearance)
      put("sections", sections)
      put("pluginSettings", pluginSettings)
      put("excluded", JsonArray(excludedCollections.sorted().map { JsonPrimitive(it) }))
    }
      .toString()

  companion object {

    val INTERVALS = listOf(1, 5, 15, 60)

    private const val APPEARANCE_RECORD = "appearance"
    private const val PLUGINS_RECORD = "plugins"
    private const val INSTALLED_RECORD = "plugins_installed"
    private const val PLUGIN_SETTINGS_PREFIX = "plugin."

    fun decode(text: String?): SyncOptions {
      if (text.isNullOrBlank()) return SyncOptions()
      return runCatching {
        val o = Json.parseToJsonElement(text).jsonObject

        fun flag(key: String) = o[key]?.jsonPrimitive?.booleanOrNull ?: true
        SyncOptions(
          auto = flag("auto"),
          intervalMinutes = o["interval"]?.jsonPrimitive?.intOrNull?.takeIf { it in INTERVALS } ?: 1,
          appearance = flag("appearance"),
          sections = flag("sections"),
          pluginSettings = flag("pluginSettings"),
          excludedCollections = o["excluded"]
            ?.jsonArray
            ?.mapNotNull { it.jsonPrimitive.contentOrNull }
            ?.toSet()
            .orEmpty()
        )
      }
        .getOrDefault(SyncOptions())
    }
  }
}
