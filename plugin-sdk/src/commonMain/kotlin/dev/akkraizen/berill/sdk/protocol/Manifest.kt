package dev.akkraizen.berill.sdk.protocol

import kotlinx.serialization.Serializable

const val MANIFEST_FILE = "berill-plugin.json"
const val PLUGIN_JAR = "plugin.jar"
const val PLUGIN_DEX = "classes.dex"
const val PACKAGE_EXTENSION = "tplugin"

@Serializable
data class ManifestPage(val id: String, val title: LocalizedText, val icon: String = "puzzle")

@Serializable
data class ManifestCard(val id: String, val title: LocalizedText)

@Serializable
data class ManifestPermissions(val network: List<String> = emptyList())

@Serializable
data class PluginManifest(
  val id: String,
  val version: String,
  val name: LocalizedText,
  val description: LocalizedText = LocalizedText("en" to ""),
  val author: String = "",
  val entry: String,
  val icon: String = "puzzle",
  val pages: List<ManifestPage>,
  val homeCards: List<ManifestCard> = emptyList(),
  val hasSettings: Boolean = false,
  val collections: List<String> = emptyList(),
  val permissions: ManifestPermissions = ManifestPermissions(),
  val sdkVersion: Int = PROTOCOL_VERSION
)

fun PluginManifest.problems(): List<String> =
  buildList {
    if (!Regex("[a-z][a-z0-9_]{1,31}").matches(id))
      add("id `$id`: a-z, 0-9, _ (2–32 symbols)")
    if (version.isBlank() || version.length > 32)
      add("version: 1–32 symbols")
    if (name.translations.values.any { it.isBlank() || it.length > 60 })
      add("name: 1–60 symbols")
    if (sdkVersion > PROTOCOL_VERSION)
      add("the plugin is compiled for a newer version of Berill (SDK $sdkVersion)")
    if (!Regex("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_$]*)+").matches(entry))
      add("entry `$entry`: full class name")
    if (RESERVED_PACKAGES.any { entry.startsWith(it) })
      add("entry `$entry`: package reserved")
    if (pages.isEmpty())
      add("pages: at least one page is needed")

    val pageIds = pages.map { it.id }
    if (pageIds.any { !Regex("[a-z0-9][a-z0-9_-]{0,31}").matches(it) })
      add("pages: page id — a-z, 0-9, _, -")
    if (pageIds.size != pageIds.toSet().size)
      add("pages: repeated ids")
    if (homeCards.map { it.id }.let { it.size != it.toSet().size })
      add("homeCards: repeated ids")

    collections.forEach { collection ->
      if (!collection.startsWith("${id}_") || !Regex("[a-z][a-z0-9_]{0,39}").matches(collection)) {
        add("collection `$collection`: must start with `${id}_`, a-z, 0-9, _ (up to 40 characters)")
      }
    }

    permissions.network.forEach { h ->
      if (!Regex("(\\*\\.)?[a-z0-9-]+(\\.[a-z0-9-]+)+")
          .matches(h)
      ) {
        add("permissions.network: `$h` — a host of the form api.example.com or *.example.com is required")
      }
    }
  }

val RESERVED_PACKAGES = listOf(
  "java.",
  "javax.",
  "jdk.",
  "sun.",
  "com.sun.",
  "kotlin.",
  "kotlinx.",
  "android.",
  "androidx.",
  "dalvik.",
  "dev.akkraizen.berill."
)
